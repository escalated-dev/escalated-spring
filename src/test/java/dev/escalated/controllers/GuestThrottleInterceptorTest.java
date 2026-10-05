package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.controllers.widget.GuestThrottleInterceptor;
import dev.escalated.controllers.widget.WidgetController;
import dev.escalated.services.ratelimit.GuestRateLimitStore;
import dev.escalated.services.ratelimit.InMemoryGuestRateLimitStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class GuestThrottleInterceptorTest {

    @Test
    void countsInAHostDefinedStore() throws Exception {
        List<String> keys = new ArrayList<>();
        GuestRateLimitStore shared = (key, window) -> {
            keys.add(key + "@" + window.toSeconds());
            return new GuestRateLimitStore.Hit(11, Duration.ofSeconds(30));
        };
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("properties", new EscalatedProperties());
        beans.addBean("store", shared);
        GuestThrottleInterceptor interceptor = new GuestThrottleInterceptor(
                beans.getBeanProvider(EscalatedProperties.class),
                beans.getBeanProvider(GuestRateLimitStore.class));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new Object(), WidgetController.class.getMethod(
                "addReply", String.class, Map.class));

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isFalse();
        assertThat(keys).containsExactly("escalated:guest:reply:203.0.113.9@60");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
    }

    @Test
    void inMemoryWindowResetsAfterItCloses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryGuestRateLimitStore store = new InMemoryGuestRateLimitStore(clock);
        Duration minute = Duration.ofMinutes(1);

        store.increment("k", minute);
        clock.advance(Duration.ofSeconds(20));
        GuestRateLimitStore.Hit second = store.increment("k", minute);

        assertThat(second.count()).isEqualTo(2);
        assertThat(second.resetsIn()).isEqualTo(Duration.ofSeconds(40));

        clock.advance(Duration.ofSeconds(40));
        assertThat(store.increment("k", minute).count()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
