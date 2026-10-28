package dev.escalated.controllers.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Every answer on the mobile guest routes, success or error (validation,
 * 404, the guest rate limit's 429), carries {@code Cache-Control: private,
 * no-store} and {@code Referrer-Policy: no-referrer}: they hold grants and
 * private correspondence. Set before the rest of the chain runs, and held
 * there, so nothing later replaces them.
 */
public class MobileGuestPrivacyFilter extends OncePerRequestFilter {

    public static final String CACHE_CONTROL = "private, no-store";
    public static final String REFERRER_POLICY = "no-referrer";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals(MobileGuestController.PREFIX) && !path.startsWith(MobileGuestController.PREFIX + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", CACHE_CONTROL);
        response.setHeader("Referrer-Policy", REFERRER_POLICY);
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public void setHeader(String name, String value) {
                if (!held(name)) {
                    super.setHeader(name, value);
                }
            }

            @Override
            public void addHeader(String name, String value) {
                if (!held(name)) {
                    super.addHeader(name, value);
                }
            }
        });
    }

    private static boolean held(String name) {
        return "Cache-Control".equalsIgnoreCase(name) || "Referrer-Policy".equalsIgnoreCase(name)
                || "Pragma".equalsIgnoreCase(name) || "Expires".equalsIgnoreCase(name);
    }
}
