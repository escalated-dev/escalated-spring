package dev.escalated.ui.controllers;

import dev.escalated.services.SsoService;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import dev.escalated.services.ValidationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Admin > Settings > SSO ({@code Admin/Settings/SsoSettings}), escalated-laravel's
 * {@code SsoSettingsController}: the {@code sso_*} settings with secrets masked as {@code ********}
 * (the mask posted back keeps the stored value), and the read-only {@code endpoints} to give the
 * identity provider. 404 while {@code ESCALATED_SSO} is off.
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin/settings/sso")
public class SsoSettingsUiController {

    public static final String MASK = "********";

    private static final List<String> URL_KEYS = List.of("sso_url", "sso_login_url", "sso_logout_url",
            "sso_metadata_url", "sso_oauth_authorize_url", "sso_oauth_token_url", "sso_oauth_userinfo_url");

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final SsoService sso;

    public SsoSettingsUiController(EscalatedUiRenderer renderer, Inertia inertia, SsoService sso) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.sso = sso;
    }

    @GetMapping
    public Object index(HttpServletRequest request) {
        Map<String, String> settings = new LinkedHashMap<>(sso.config());
        for (String key : SsoService.SECRET_KEYS) {
            if (!settings.getOrDefault(key, "").isEmpty()) {
                settings.put(key, MASK);
            }
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("settings", settings);
        props.put("endpoints", sso.endpoints(request));
        return renderer.render("Escalated/Admin/Settings/SsoSettings", props);
    }

    @PostMapping
    public ResponseEntity<Void> update(HttpServletRequest request) {
        UiInput input = UiInput.of(request);
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : SsoService.DEFAULTS.keySet()) {
            if (input.has(key)) {
                String value = input.string(key);
                values.put(key, value == null ? "" : value);
            }
        }
        String provider = values.getOrDefault("sso_provider", "");
        if (!SsoService.PROVIDERS.contains(provider)) {
            errors.put("sso_provider", List.of("The selected sso provider is invalid."));
        }
        String algorithm = values.getOrDefault("sso_jwt_algorithm", "");
        if (!algorithm.isEmpty() && !SsoService.JWT_ALGORITHMS.contains(algorithm)) {
            errors.put("sso_jwt_algorithm", List.of("The selected sso jwt algorithm is invalid."));
        }
        for (String key : URL_KEYS) {
            String value = values.getOrDefault(key, "");
            if (!value.isEmpty() && !isUrl(value)) {
                errors.put(key, List.of("The " + key.replace('_', ' ') + " field must be a valid URL."));
            }
        }
        List<String> required = switch (provider) {
            case "saml" -> List.of("sso_entity_id", "sso_login_url");
            case "jwt" -> List.of("sso_url", "sso_jwt_secret");
            case "oauth" -> List.of("sso_oauth_authorize_url", "sso_oauth_token_url", "sso_oauth_userinfo_url",
                    "sso_oauth_client_id");
            default -> List.of();
        };
        Map<String, String> stored = sso.config();
        for (String key : required) {
            String value = values.getOrDefault(key, "");
            boolean keptSecret = MASK.equals(value) && !stored.getOrDefault(key, "").isEmpty();
            if (value.isEmpty() && !keptSecret) {
                errors.putIfAbsent(key, List.of("The " + key.replace('_', ' ') + " field is required."));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        for (String key : SsoService.SECRET_KEYS) {
            if (MASK.equals(values.get(key))) {
                values.remove(key);
            }
        }
        sso.save(values);
        inertia.flash("success", "SSO settings updated.");
        return inertia.back(UiRoutes.PREFIX + "/admin/settings/sso");
    }

    private static boolean isUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getScheme() != null && uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
