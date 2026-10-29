package dev.escalated.ui;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Permission;
import dev.escalated.plugins.PluginRegistry;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.security.EscalatedAuthorization;
import dev.escalated.services.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What every page receives, as Laravel shares it.
 *
 * <ul>
 *   <li>{@code auth.user}: {@code {id, name, email}} of the signed-in user, or null. The id is
 *       the agent profile's (what assignment and the "mine" filters take); a user with no agent
 *       profile has none. Never more than those three fields.
 *   <li>{@code flash}: {@code success} and {@code error}, once.
 *   <li>{@code errors}: the last write's validation errors, field to first message, once.
 *   <li>{@code escalated}: {@code prefix}, {@code is_agent}, {@code is_admin},
 *       {@code permissions}, {@code features}, {@code broadcasting}, {@code agent_type} and the
 *       knowledge base and guest ticket switches, as {@code EscalatedServiceProvider::shareInertiaData};
 *       and {@code plugins}, the active plugins' {@code menuItems}, {@code pageComponents} and
 *       {@code dashboardWidgets} ({@code {}} when none is active).
 * </ul>
 */
public class EscalatedSharedProps implements InertiaSharedProps {

    private final EscalatedAuthorization authorization;
    private final AgentProfileRepository agents;
    private final SettingsService settings;
    private final EscalatedProperties properties;
    private final TransactionTemplate readOnly;
    private final PluginRegistry plugins;

    public EscalatedSharedProps(
            EscalatedAuthorization authorization,
            AgentProfileRepository agents,
            SettingsService settings,
            EscalatedProperties properties,
            TransactionTemplate readOnly) {
        this(authorization, agents, settings, properties, readOnly, null);
    }

    /** As above, sharing the active plugins' menus, slots and widgets as {@code escalated.plugins}. */
    public EscalatedSharedProps(
            EscalatedAuthorization authorization,
            AgentProfileRepository agents,
            SettingsService settings,
            EscalatedProperties properties,
            TransactionTemplate readOnly,
            PluginRegistry plugins) {
        this.authorization = authorization;
        this.agents = agents;
        this.settings = settings;
        this.properties = properties;
        this.readOnly = readOnly;
        this.plugins = plugins;
    }

    @Override
    public Map<String, Object> share(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        Optional<AgentProfile> profile = signedIn ? agents.findByEmail(authentication.getName()) : Optional.empty();
        boolean isAdmin = signedIn && authorization.isAdmin(authentication);
        boolean isAgent = signedIn && authorization.isAgent(authentication);
        List<String> permissions = readOnly.execute(status -> permissions(isAdmin, profile.orElse(null)));

        // Reading the token makes Spring Security write the XSRF-TOKEN cookie Inertia's client sends back.
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }

        Map<String, Object> shared = new LinkedHashMap<>();
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("user", signedIn ? user(authentication, profile.orElse(null)) : null);
        shared.put("auth", auth);

        Map<String, Object> flash = Inertia.pull(request, Inertia.FLASH_SESSION_KEY);
        Map<String, Object> flashProp = new LinkedHashMap<>();
        flashProp.put("success", flash.get("success"));
        flashProp.put("error", flash.get("error"));
        flash.forEach(flashProp::putIfAbsent);
        shared.put("flash", flashProp);
        shared.put("errors", Inertia.pull(request, Inertia.ERRORS_SESSION_KEY));

        Map<String, Object> escalated = new LinkedHashMap<>();
        escalated.put("prefix", UiRoutes.PREFIX.substring(1));
        escalated.put("broadcasting", Map.of("channel_prefix", ""));
        escalated.put("is_agent", isAgent);
        escalated.put("is_admin", isAdmin);
        escalated.put("permissions", permissions);
        escalated.put("features", Map.of("newsletters", properties.getNewsletters().isEnabled()));
        escalated.put("guest_tickets_enabled", setting("guest_tickets_enabled", true));
        escalated.put("show_powered_by", setting("show_powered_by", true));
        escalated.put("knowledge_base_enabled", properties.getKnowledgeBase().isEnabled());
        escalated.put("knowledge_base_public", setting("knowledge_base_public", true));
        escalated.put("knowledge_base_feedback_enabled", setting("knowledge_base_feedback_enabled", true));
        if (signedIn) {
            escalated.put("agent_type", "full");
        }
        escalated.put("plugins", plugins == null ? Map.of() : plugins.uiExtensions(signedIn ? authentication : null));
        shared.put("escalated", escalated);
        return shared;
    }

    private static Map<String, Object> user(Authentication authentication, AgentProfile profile) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", profile == null ? null : profile.getId());
        user.put("name", profile != null && profile.getName() != null && !profile.getName().isBlank()
                ? profile.getName()
                : authentication.getName());
        user.put("email", profile != null && profile.getEmail() != null ? profile.getEmail() : authentication.getName());
        return user;
    }

    private List<String> permissions(boolean isAdmin, AgentProfile profile) {
        if (isAdmin) {
            return List.of("*");
        }
        List<String> names = new ArrayList<>();
        if (profile != null) {
            AgentProfile fresh = agents.findById(profile.getId()).orElse(null);
            if (fresh != null && fresh.getRole() != null) {
                for (Permission permission : fresh.getRole().getPermissions()) {
                    names.add(permission.getName());
                }
            }
        }
        return names;
    }

    private boolean setting(String key, boolean fallback) {
        try {
            return settings.get(key).map(value -> "1".equals(value) || "true".equalsIgnoreCase(value)).orElse(fallback);
        } catch (RuntimeException unavailable) {
            return fallback;
        }
    }
}
