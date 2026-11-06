package dev.escalated.ui;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The outside of the embedded admin (ADR 2026-11-05-embeddable-admin), ahead of every other
 * filter, Spring Security's included. Registered only while embedding is on.
 *
 * <ul>
 *   <li>The standalone admin pages ({@code /escalated/admin/...}, the two-factor enrolment pages
 *       aside) answer 404: one route name, one URL.
 *   <li>A request under the embed prefix is marked embedded, and its response is wrapped:
 *       redirects to standalone admin pages ({@code Location}, {@code X-Inertia-Location}) point
 *       at the embedded ones, and in iframe mode the response carries
 *       {@code Content-Security-Policy: frame-ancestors 'self' <origins>} and never an
 *       {@code X-Frame-Options}. Because this wrapper is the outermost one, nothing the host's
 *       security chain or filters write afterwards can undo it; a CSP the host sends is kept, the
 *       frame policy going out as a second header.
 *   <li>The two-factor enrolment pages are not part of the embedded admin: 404 there too.
 * </ul>
 */
public class EmbedResponseFilter extends OncePerRequestFilter {

    static final String CSP = "Content-Security-Policy";
    static final String X_FRAME_OPTIONS = "X-Frame-Options";

    private final EscalatedEmbed embed;

    public EmbedResponseFilter(EscalatedEmbed embed) {
        this.embed = embed;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (EscalatedEmbed.moves(path)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!embed.underPrefix(path)) {
            chain.doFilter(request, response);
            return;
        }
        if (!EscalatedEmbed.moves(embed.toStandalone(path))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        request.setAttribute(EscalatedEmbed.REQUEST_ATTRIBUTE, Boolean.TRUE);
        request.setAttribute(EscalatedEmbed.ORIGINAL_URI_ATTRIBUTE, request.getRequestURI());
        boolean iframe = embed.iframe();
        String policy = iframe ? embed.frameAncestorsPolicy() : null;
        EmbeddedResponse wrapped = new EmbeddedResponse(response, embed, request.getContextPath(), policy);
        chain.doFilter(request, wrapped);
        if (iframe && !response.isCommitted() && response.containsHeader(X_FRAME_OPTIONS)) {
            // Written past the wrapper (by the container itself); taken off while it still can be.
            response.setHeader(X_FRAME_OPTIONS, null);
        }
    }

    /** Moves redirect targets under the prefix and, in iframe mode, owns the framing headers. */
    static final class EmbeddedResponse extends HttpServletResponseWrapper {

        private final EscalatedEmbed embed;
        private final String contextPath;
        private final String policy;

        EmbeddedResponse(HttpServletResponse response, EscalatedEmbed embed, String contextPath, String policy) {
            super(response);
            this.embed = embed;
            this.contextPath = contextPath;
            this.policy = policy;
            if (policy != null) {
                super.addHeader(CSP, policy);
            }
        }

        @Override
        public void setHeader(String name, String value) {
            if (framing(name)) {
                return;
            }
            super.setHeader(name, location(name, value));
            if (policy != null && CSP.equalsIgnoreCase(name)) {
                super.addHeader(CSP, policy);
            }
        }

        /**
         * The frame policy is not the host's CSP: a writer that sets a CSP only when none is there
         * yet (Spring Security's) still writes its own. An {@code X-Frame-Options} never is.
         */
        @Override
        public boolean containsHeader(String name) {
            if (policy != null && CSP.equalsIgnoreCase(name)) {
                return getHeaders(CSP).stream().anyMatch(value -> !policy.equals(value));
            }
            if (framing(name)) {
                return false;
            }
            return super.containsHeader(name);
        }

        @Override
        public void addHeader(String name, String value) {
            if (framing(name)) {
                return;
            }
            super.addHeader(name, location(name, value));
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            super.sendRedirect(embed.rewriteLocation(location, contextPath));
        }

        @Override
        public void sendRedirect(String location, int status) throws IOException {
            super.sendRedirect(embed.rewriteLocation(location, contextPath), status);
        }

        @Override
        public void sendRedirect(String location, boolean clearBuffer) throws IOException {
            super.sendRedirect(embed.rewriteLocation(location, contextPath), clearBuffer);
        }

        @Override
        public void sendRedirect(String location, int status, boolean clearBuffer) throws IOException {
            super.sendRedirect(embed.rewriteLocation(location, contextPath), status, clearBuffer);
        }

        @Override
        public void reset() {
            super.reset();
            if (policy != null) {
                super.addHeader(CSP, policy);
            }
        }

        private boolean framing(String name) {
            return policy != null && X_FRAME_OPTIONS.equalsIgnoreCase(name);
        }

        private String location(String name, String value) {
            if ("Location".equalsIgnoreCase(name) || Inertia.LOCATION_HEADER.equalsIgnoreCase(name)) {
                return embed.rewriteLocation(value, contextPath);
            }
            return value;
        }
    }
}
