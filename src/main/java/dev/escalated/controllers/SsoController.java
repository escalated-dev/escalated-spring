package dev.escalated.controllers;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.RoleRepository;
import dev.escalated.security.AccessFeatures;
import dev.escalated.security.DefaultEscalatedSsoUsers;
import dev.escalated.security.EscalatedSsoUsers;
import dev.escalated.security.TwoFactorSession;
import dev.escalated.services.SsoService;
import dev.escalated.services.SsoService.Identity;
import dev.escalated.services.SsoService.SsoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The SSO sign-in routes (ADR 2026-10-29-configurable-access-features), all under
 * {@code /escalated/sso}, open to guests, answering 404 while {@code ESCALATED_SSO} is off
 * ({@code EnsureAccessFeaturesFilter}) and for any provider other than the one chosen in Admin >
 * Settings > SSO.
 *
 * <p>A successful sign-in finds (or, with {@code escalated.sso.create-users}, creates) the host user
 * by email through {@link EscalatedSsoUsers}, signs them in on a new session ID, clears the two-factor
 * mark (SSO does not skip the challenge), optionally maps the role, and redirects to
 * {@code escalated.sso.redirect-to}. Every refusal answers a generic 403 and logs the reason.
 */
@Controller
@RequestMapping("/escalated/sso")
public class SsoController {

    static final String OAUTH_STATE = "escalated.sso.oauth_state";
    static final String OAUTH_VERIFIER = "escalated.sso.oauth_verifier";

    private final SsoService sso;
    private final EscalatedSsoUsers users;
    private final AccessFeatures features;
    private final AgentProfileRepository agents;
    private final RoleRepository roles;
    private final ApplicationEventPublisher events;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public SsoController(SsoService sso, ObjectProvider<EscalatedSsoUsers> users,
                         ObjectProvider<UserDetailsService> hostUsers, AccessFeatures features,
                         AgentProfileRepository agents, RoleRepository roles, ApplicationEventPublisher events) {
        this.sso = sso;
        this.users = users.getIfAvailable(() -> new DefaultEscalatedSsoUsers(hostUsers, agents));
        this.features = features;
        this.agents = agents;
        this.roles = roles;
        this.events = events;
    }

    @GetMapping("/login")
    public ResponseEntity<?> login(HttpServletRequest request) {
        String provider = sso.provider();
        try {
            switch (provider) {
                case "saml":
                    return redirect(URI.create(sso.samlLoginUrl(request)));
                case "oauth":
                    SsoService.OAuthStart start = sso.oauthStart(request);
                    HttpSession session = request.getSession(true);
                    session.setAttribute(OAUTH_STATE, start.state());
                    session.setAttribute(OAUTH_VERIFIER, start.verifier());
                    return redirect(start.url());
                case "jwt":
                    return redirect(URI.create(sso.jwtLoginUrl(request)));
                default:
                    return ResponseEntity.notFound().build();
            }
        } catch (SsoException refused) {
            return refuse(refused);
        }
    }

    @GetMapping("/saml/metadata")
    public ResponseEntity<String> metadata(HttpServletRequest request) {
        if (!"saml".equals(sso.provider())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(sso.samlMetadata(request));
    }

    @PostMapping("/saml/acs")
    public ResponseEntity<?> acs(@RequestParam(name = "SAMLResponse", required = false) String samlResponse,
                                 HttpServletRequest request, HttpServletResponse response) {
        if (!"saml".equals(sso.provider())) {
            return ResponseEntity.notFound().build();
        }
        try {
            return signIn(sso.validateSamlResponse(request, samlResponse), request, response);
        } catch (SsoException refused) {
            return refuse(refused);
        }
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<?> oauthCallback(@RequestParam(name = "state", required = false) String state,
                                           @RequestParam(name = "code", required = false) String code,
                                           @RequestParam(name = "error", required = false) String error,
                                           HttpServletRequest request, HttpServletResponse response) {
        if (!"oauth".equals(sso.provider())) {
            return ResponseEntity.notFound().build();
        }
        HttpSession session = request.getSession(false);
        Object expected = session == null ? null : session.getAttribute(OAUTH_STATE);
        Object verifier = session == null ? null : session.getAttribute(OAUTH_VERIFIER);
        if (session != null) {
            // Single use, whatever happens next.
            session.removeAttribute(OAUTH_STATE);
            session.removeAttribute(OAUTH_VERIFIER);
        }
        try {
            if (error != null && !error.isBlank()) {
                throw new SsoException("OAuth refused: the provider answered " + error + ".");
            }
            if (state == null || !(expected instanceof String kept) || !java.security.MessageDigest.isEqual(
                    kept.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    state.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                throw new SsoException("OAuth refused: missing or mismatched state.");
            }
            if (code == null || code.isBlank() || !(verifier instanceof String pkce)) {
                throw new SsoException("OAuth refused: no authorization code.");
            }
            return signIn(sso.oauthIdentity(request, code, pkce), request, response);
        } catch (SsoException refused) {
            return refuse(refused);
        }
    }

    @RequestMapping(value = "/jwt/callback", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> jwtCallback(@RequestParam(name = "jwt", required = false) String jwt,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (!"jwt".equals(sso.provider())) {
            return ResponseEntity.notFound().build();
        }
        try {
            return signIn(sso.validateJwt(request, jwt), request, response);
        } catch (SsoException refused) {
            return refuse(refused);
        }
    }

    private ResponseEntity<?> signIn(Identity identity, HttpServletRequest request, HttpServletResponse response) {
        Authentication user = users.find(identity.email());
        if (user == null) {
            if (!sso.createUsers()) {
                throw new SsoException("SSO refused: no user with email " + identity.email() + " (create-users is off).");
            }
            user = users.create(identity.email(), identity.name());
            if (user == null) {
                throw new SsoException("SSO refused: this host cannot create users (no UserDetailsManager).");
            }
        }
        syncRole(user.getName(), identity.role());

        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(user);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        // A new login: the two-factor challenge comes again.
        TwoFactorSession.forget(request);
        events.publishEvent(new InteractiveAuthenticationSuccessEvent(user, getClass()));
        return redirect(URI.create(sso.redirectTo()));
    }

    /** With roles and permissions on and sync-roles on, a role naming an Escalated role replaces the user's. */
    private void syncRole(String principal, String role) {
        if (!features.rolesPermissions() || !sso.syncRoles() || role == null || role.isBlank()) {
            return;
        }
        roles.findByName(role.trim()).ifPresent(found -> {
            Optional<AgentProfile> profile = agents.findByEmail(principal);
            profile.ifPresent(agent -> {
                agent.setRole(found);
                agents.save(agent);
            });
        });
    }

    private static ResponseEntity<?> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private static ResponseEntity<String> refuse(SsoException reason) {
        SsoService.refused(reason);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN)
                .body("Single sign-on failed.");
    }
}
