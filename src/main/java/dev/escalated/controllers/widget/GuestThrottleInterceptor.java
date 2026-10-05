package dev.escalated.controllers.widget;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.services.ratelimit.GuestRateLimitStore;
import dev.escalated.services.ratelimit.InMemoryGuestRateLimitStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Per-client-IP rate limit for the unauthenticated guest endpoints marked
 * {@link GuestThrottle}. Limits come from {@code escalated.guest-rate-limit.*}
 * (defaults: 5 ticket submissions and 10 replies per IP per minute), each
 * scope with its own counter, over a 60-second window.
 *
 * <p>An interceptor runs before argument resolution, so requests with a wrong
 * guest token or an unreadable body are counted too, and a token cannot be
 * guessed at speed.
 *
 * <p>The client IP is {@link HttpServletRequest#getRemoteAddr()}. Behind a proxy
 * the host must set {@code server.forward-headers-strategy} and trust its
 * proxies, or every guest shares the proxy's address.
 *
 * <p>Counters live in the host's {@link GuestRateLimitStore} bean when it
 * defines one, otherwise in memory, per process.
 */
@Component
public class GuestThrottleInterceptor implements HandlerInterceptor {

    static final Duration WINDOW = Duration.ofMinutes(1);

    private final ObjectProvider<EscalatedProperties> properties;
    private final GuestRateLimitStore store;

    public GuestThrottleInterceptor(ObjectProvider<EscalatedProperties> properties,
                                    ObjectProvider<GuestRateLimitStore> store) {
        this.properties = properties;
        this.store = store.getIfAvailable(InMemoryGuestRateLimitStore::new);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        GuestThrottle throttle = method.getMethodAnnotation(GuestThrottle.class);
        if (throttle == null) {
            return true;
        }

        EscalatedProperties.GuestRateLimitProperties config = properties
                .getIfAvailable(EscalatedProperties::new)
                .getGuestRateLimit();
        if (config == null || !config.isEnabled()) {
            return true;
        }

        GuestThrottle.Scope scope = throttle.value();
        int limit = Math.max(1, scope == GuestThrottle.Scope.TICKET
                ? config.getTicketsPerMinute()
                : config.getRepliesPerMinute());
        String ip = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String key = "escalated:guest:" + scope.name().toLowerCase(Locale.ROOT) + ":" + ip;

        GuestRateLimitStore.Hit hit = store.increment(key, WINDOW);
        if (hit.count() <= limit) {
            return true;
        }

        long retryAfter = Math.max(1, (hit.resetsIn().toMillis() + 999) / 1000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
        return false;
    }
}
