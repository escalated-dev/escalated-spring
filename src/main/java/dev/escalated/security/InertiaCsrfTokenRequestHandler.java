package dev.escalated.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Reads the CSRF token wherever the shared pages send it: Inertia's client (axios) echoes the
 * {@code XSRF-TOKEN} cookie as {@code X-XSRF-TOKEN}, and the components that call {@code fetch}
 * (saved views, presence, mentions) send the {@code csrf-token} meta tag's value as
 * {@code X-CSRF-TOKEN}, as on Laravel. Both carry the same unmasked token.
 */
public class InertiaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    /** The header the fetch-based components send. */
    public static final String FETCH_HEADER = "X-CSRF-TOKEN";

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String value = super.resolveCsrfTokenValue(request, csrfToken);
        return value != null ? value : request.getHeader(FETCH_HEADER);
    }
}
