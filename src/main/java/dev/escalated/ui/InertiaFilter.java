package dev.escalated.ui;

import dev.escalated.config.EscalatedProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The parts of the Inertia protocol that sit around a page, as Inertia's Laravel middleware does.
 *
 * <ul>
 *   <li>Asset version: an Inertia GET whose {@code X-Inertia-Version} differs from
 *       {@code escalated.ui.version} is answered 409 with {@code X-Inertia-Location}, and the
 *       client reloads the page in full. Flash messages survive it.
 *   <li>A redirect after an Inertia PUT, PATCH or DELETE is turned into a 303, so the browser
 *       follows it with a GET.
 * </ul>
 *
 * <p>Requests without {@code X-Inertia} pass untouched.
 */
public class InertiaFilter extends OncePerRequestFilter {

    private static final Set<String> REWRITTEN = Set.of("PUT", "PATCH", "DELETE");

    private final EscalatedProperties properties;

    public InertiaFilter(EscalatedProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!Inertia.isInertia(request)) {
            chain.doFilter(request, response);
            return;
        }

        response.setHeader("Vary", Inertia.HEADER);

        String version = properties.getUi().getVersion();
        if ("GET".equalsIgnoreCase(request.getMethod())
                && version != null
                && !version.isEmpty()
                && !version.equals(request.getHeader(Inertia.VERSION_HEADER))) {
            response.setStatus(409);
            response.setHeader(Inertia.LOCATION_HEADER, request.getRequestURL()
                    + (request.getQueryString() == null ? "" : "?" + request.getQueryString()));
            return;
        }

        if (REWRITTEN.contains(request.getMethod().toUpperCase(java.util.Locale.ROOT))) {
            chain.doFilter(request, new SeeOther(response));
            return;
        }
        chain.doFilter(request, response);
    }

    /** Answers 303 wherever a 302 was about to go out. */
    private static final class SeeOther extends HttpServletResponseWrapper {

        SeeOther(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int status) {
            super.setStatus(status == 302 ? 303 : status);
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            setStatus(303);
            setHeader("Location", location);
            flushBuffer();
        }
    }
}
