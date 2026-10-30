package dev.escalated.ui.controllers;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.TwoFactor;
import dev.escalated.security.EscalatedAuthorization;
import dev.escalated.security.TwoFactorSession;
import dev.escalated.services.TwoFactorService;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Two-factor enrolment and the challenge (ADR 2026-10-29-configurable-access-features), escalated-
 * laravel's {@code Admin\TwoFactorController} and {@code TwoFactorChallengeController}.
 *
 * <ul>
 *   <li>{@code GET /escalated/admin/settings/two-factor} renders {@code Admin/Settings/TwoFactor}
 *       ({@code enabled}, {@code pending}, {@code required}) for every staff user, not only admins;
 *       {@code setup}, {@code confirm} and {@code disable} posts manage the user's own enrolment.
 *   <li>{@code GET /escalated/two-factor/challenge} renders {@code Auth/TwoFactorChallenge}
 *       ({@code action}, {@code error}); posting {@code code} or {@code recovery_code} to the same
 *       path passes it, 5 attempts per user per minute.
 * </ul>
 *
 * <p>Every route answers 404 while {@code ESCALATED_TWO_FACTOR} is off ({@code EnsureAccessFeaturesFilter}).
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX)
public class TwoFactorUiController {

    static final int ATTEMPTS_PER_MINUTE = 5;

    private static final Logger log = LoggerFactory.getLogger(TwoFactorUiController.class);

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final TwoFactorService twoFactor;
    private final EscalatedAuthorization authorization;
    private final UiRoutes routes;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public TwoFactorUiController(EscalatedUiRenderer renderer, Inertia inertia, TwoFactorService twoFactor,
                                 EscalatedAuthorization authorization, UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.twoFactor = twoFactor;
        this.authorization = authorization;
        this.routes = routes;
    }

    // ── Enrolment ───────────────────────────────────────────────────────

    @GetMapping("/admin/settings/two-factor")
    public Object index(Authentication user) {
        Optional<TwoFactor> enrolment = profile(user).flatMap(profile -> twoFactor.enrolment(profile.getId()));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("enabled", enrolment.map(TwoFactor::isConfirmed).orElse(false));
        props.put("pending", enrolment.map(e -> !e.isConfirmed()).orElse(false));
        props.put("required", twoFactor.required());
        return renderer.render("Escalated/Admin/Settings/TwoFactor", props);
    }

    @PostMapping("/admin/settings/two-factor/setup")
    public ResponseEntity<Void> setup(Authentication user) {
        AgentProfile profile = profile(user).orElse(null);
        if (profile == null) {
            return ResponseEntity.status(403).build();
        }
        if (twoFactor.confirmedFor(profile.getId()).isPresent()) {
            inertia.flash("error", "Two-factor authentication is already enabled. Disable it first to set it up again.");
            return back();
        }
        TwoFactorService.Setup setup;
        try {
            setup = twoFactor.setup(profile);
        } catch (IllegalStateException unconfigured) {
            log.error("Escalated two-factor setup failed: {}", unconfigured.getMessage());
            inertia.flash("error", "Two-factor authentication is not configured on this server.");
            return back();
        }
        Map<String, Object> flash = new LinkedHashMap<>();
        flash.put("qr_uri", setup.qrUri());
        flash.put("recovery_codes", setup.recoveryCodes());
        inertia.flash("two_factor_setup", flash);
        return back();
    }

    @PostMapping("/admin/settings/two-factor/confirm")
    public ResponseEntity<Void> confirm(HttpServletRequest request, Authentication user) {
        AgentProfile profile = profile(user).orElse(null);
        if (profile == null) {
            return ResponseEntity.status(403).build();
        }
        String code = UiInput.of(request).string("code");
        if (code == null) {
            throw UiInput.invalid("code", "The code field is required.");
        }
        if (code.length() != 6) {
            throw UiInput.invalid("code", "The code field must be 6 characters.");
        }
        if (twoFactor.enrolment(profile.getId()).filter(e -> !e.isConfirmed()).isEmpty()) {
            throw UiInput.invalid("code", "No pending two-factor setup found.");
        }
        Optional<List<String>> codes = twoFactor.confirm(profile.getId(), code);
        if (codes.isEmpty()) {
            throw UiInput.invalid("code", "Invalid verification code.");
        }
        // The code just entered proves the second factor for this session.
        TwoFactorSession.markPassed(request, user.getName());
        inertia.flash("success", "Two-factor authentication enabled.");
        inertia.flash("two_factor_confirmed", Map.of("recovery_codes", codes.get()));
        return back();
    }

    @PostMapping("/admin/settings/two-factor/disable")
    public ResponseEntity<Void> disable(Authentication user) {
        profile(user).ifPresent(profile -> twoFactor.disable(profile.getId()));
        inertia.flash("success", "Two-factor authentication disabled.");
        return back();
    }

    // ── Challenge ───────────────────────────────────────────────────────

    @GetMapping("/two-factor/challenge")
    public Object challenge(HttpServletRequest request, Authentication user) {
        if (!twoFactor.confirmedFor(user.getName()) || TwoFactorSession.passed(request, user.getName())) {
            return inertia.redirect(intended(request, user));
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("action", ServletUriComponentsBuilder.fromContextPath(request)
                .path(UiRoutes.PREFIX + "/two-factor/challenge").build().toUriString());
        props.put("error", "");
        return renderer.render("Escalated/Auth/TwoFactorChallenge", props);
    }

    @PostMapping("/two-factor/challenge")
    public ResponseEntity<Void> verify(HttpServletRequest request, Authentication user) {
        if (!allowAttempt(user.getName())) {
            return ResponseEntity.status(429).header("Retry-After", "60").build();
        }
        Optional<TwoFactor> enrolment = twoFactor.profile(user.getName())
                .flatMap(profile -> twoFactor.confirmedFor(profile.getId()));
        if (enrolment.isEmpty()) {
            return inertia.redirect(intended(request, user));
        }
        UiInput input = UiInput.of(request);
        String code = input.string("code");
        String recovery = input.string("recovery_code");
        if (code == null && recovery == null) {
            throw UiInput.invalid("code", "The code field is required when recovery code is not present.");
        }
        boolean usingRecovery = recovery != null;
        if (usingRecovery ? recovery.length() > 64 : code.length() > 16) {
            throw UiInput.invalid(usingRecovery ? "recovery_code" : "code", "The value is too long.");
        }
        boolean passed = usingRecovery
                ? twoFactor.useRecoveryCode(enrolment.get(), recovery)
                : twoFactor.verifyAndConsume(enrolment.get(), code);
        if (!passed) {
            log.info("Escalated: two-factor challenge failed for {} from {}", user.getName(), request.getRemoteAddr());
            throw usingRecovery
                    ? UiInput.invalid("recovery_code", "That recovery code is not valid.")
                    : UiInput.invalid("code", "That code is not valid.");
        }
        TwoFactorSession.markPassed(request, user.getName());
        return inertia.redirect(intended(request, user));
    }

    private boolean allowAttempt(String user) {
        long now = System.currentTimeMillis();
        Deque<Long> recent = attempts.computeIfAbsent(user, key -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && now - recent.peekFirst() > 60_000) {
                recent.pollFirst();
            }
            if (recent.size() >= ATTEMPTS_PER_MINUTE) {
                return false;
            }
            recent.addLast(now);
            return true;
        }
    }

    private String intended(HttpServletRequest request, Authentication user) {
        String intended = TwoFactorSession.takeIntended(request);
        if (intended != null) {
            return intended;
        }
        return authorization.isAgent(user)
                ? routes.url("escalated.agent.dashboard")
                : UiRoutes.PREFIX + "/admin/reports";
    }

    private ResponseEntity<Void> back() {
        return inertia.back(UiRoutes.PREFIX + "/admin/settings/two-factor");
    }

    private Optional<AgentProfile> profile(Authentication user) {
        return user == null ? Optional.empty() : twoFactor.profile(user.getName());
    }
}
