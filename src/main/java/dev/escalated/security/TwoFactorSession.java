package dev.escalated.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Which user has passed the two-factor challenge in this session (ADR
 * 2026-10-29-configurable-access-features), kept in the servlet session as the user's principal
 * name, so a session that passed for one user does not count for another.
 *
 * <p>Every new login is challenged again: an interactive sign-in (form login, remember-me, OAuth 2
 * login, SSO) and a logout clear the mark, as Laravel clears it on its {@code Login} and
 * {@code Logout} events. A host whose login publishes no such event calls {@link #forget} itself.
 */
@Component
public class TwoFactorSession {

    static final String PASSED = "escalated.two_factor.passed";
    static final String INTENDED = "escalated.two_factor.intended";

    public static boolean passed(HttpServletRequest request, String user) {
        HttpSession session = request.getSession(false);
        return session != null && user != null && user.equals(session.getAttribute(PASSED));
    }

    /** Marks the session as passed for {@code user}, under a new session ID. */
    public static void markPassed(HttpServletRequest request, String user) {
        request.getSession(true);
        request.changeSessionId();
        request.getSession().setAttribute(PASSED, user);
    }

    /** Clears the mark, so the next admin or agent page challenges again. */
    public static void forget(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PASSED);
        }
    }

    public static void rememberIntended(HttpServletRequest request, String url) {
        request.getSession(true).setAttribute(INTENDED, url);
    }

    /** The page asked for before the challenge, once; only a local path. */
    public static String takeIntended(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object url = session.getAttribute(INTENDED);
        session.removeAttribute(INTENDED);
        return url instanceof String path && path.startsWith("/") && !path.startsWith("//") ? path : null;
    }

    @EventListener
    public void onLogin(InteractiveAuthenticationSuccessEvent event) {
        forgetCurrent();
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        forgetCurrent();
    }

    private static void forgetCurrent() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            forget(attributes.getRequest());
        }
    }
}
