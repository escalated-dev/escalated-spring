package dev.escalated.security;

import dev.escalated.config.EscalatedProperties;
import java.util.List;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * The extra guards {@code escalated.routes.customer-middleware} puts on the customer portal: the
 * customer pages, the customer ticket API and the customer knowledge base (escalated-laravel's
 * {@code escalated.routes.customer_middleware}, appended after the customer routes' own
 * middleware).
 *
 * <p>Each entry is {@code escalated-agent} or {@code escalated-admin}, Escalated's own agent and admin
 * gates, or the bean name of the host's {@code AuthorizationManager<RequestAuthorizationContext>}. A
 * request must be signed in and pass every one. Empty, the default, leaves those routes exactly as
 * they were.
 */
public final class CustomerRouteGuard {

    public static final String AGENT = "escalated-agent";
    public static final String ADMIN = "escalated-admin";

    private final List<String> names;
    private final EscalatedAuthorization authorization;
    private final ListableBeanFactory beans;

    CustomerRouteGuard(EscalatedProperties properties, EscalatedAuthorization authorization,
                       ListableBeanFactory beans) {
        this.names = properties.getRoutes().getCustomerMiddleware().stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .toList();
        this.authorization = authorization;
        this.beans = beans;
        for (String name : names) {
            if (!AGENT.equals(name) && !ADMIN.equals(name) && !beans.containsBean(name)) {
                throw new IllegalStateException("escalated.routes.customer-middleware names \"" + name
                        + "\", which is neither " + AGENT + ", " + ADMIN
                        + " nor the name of an AuthorizationManager<RequestAuthorizationContext> bean.");
            }
        }
    }

    /** Whether any guard is configured; with none the customer routes are left as they were. */
    public boolean configured() {
        return !names.isEmpty();
    }

    public List<String> names() {
        return names;
    }

    /** Signed in, and granted by every configured guard. */
    public AuthorizationManager<RequestAuthorizationContext> manager() {
        return (authentication, context) -> {
            Authentication user = authentication.get();
            if (user == null || !user.isAuthenticated() || user instanceof AnonymousAuthenticationToken) {
                return new AuthorizationDecision(false);
            }
            for (String name : names) {
                AuthorizationResult result = resolve(name).authorize(authentication, context);
                if (result == null || !result.isGranted()) {
                    return new AuthorizationDecision(false);
                }
            }
            return new AuthorizationDecision(true);
        };
    }

    @SuppressWarnings("unchecked")
    private AuthorizationManager<RequestAuthorizationContext> resolve(String name) {
        if (AGENT.equals(name)) {
            return authorization.agent();
        }
        if (ADMIN.equals(name)) {
            return authorization.admin();
        }
        return beans.getBean(name, AuthorizationManager.class);
    }
}
