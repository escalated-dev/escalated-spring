package dev.escalated.ui.controllers;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.UserService;
import dev.escalated.ui.AdminPeopleUiPages;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The people pages, as escalated-laravel's {@code routes/admin.php}: {@code Escalated/Admin/Users/Index},
 * {@code Roles/Index} and {@code Form}, and {@code ApiTokens/Index}. Admins only (the web security chain).
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin")
public class AdminPeopleUiController {

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final AdminPeopleUiPages pages;
    private final AgentProfileRepository agents;
    private final UiRoutes routes;

    public AdminPeopleUiController(EscalatedUiRenderer renderer, Inertia inertia, AdminPeopleUiPages pages,
                                   AgentProfileRepository agents, UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.pages = pages;
        this.agents = agents;
        this.routes = routes;
    }

    // ── Users ───────────────────────────────────────────────────────────

    @GetMapping("/users")
    public Object users(@RequestParam(name = "search", required = false) String search,
                        @RequestParam(name = "page", defaultValue = "1") int page, Authentication authentication) {
        return renderer.render("Escalated/Admin/Users/Index", pages.users(search, page, agentId(authentication)));
    }

    @PatchMapping("/users/{user:\\d+}/role")
    public ResponseEntity<Void> updateRole(@PathVariable("user") Long id, HttpServletRequest request,
                                           Authentication authentication) {
        try {
            pages.updateUserRole(id, UiInput.of(request), agentId(authentication));
        } catch (UserService.SelfDemoteException refused) {
            inertia.flash("error", refused.getMessage());
            return inertia.back(routes.url("escalated.admin.users.index"));
        }
        inertia.flash("success", "User updated.");
        return inertia.back(routes.url("escalated.admin.users.index"));
    }

    // ── Roles ───────────────────────────────────────────────────────────

    @GetMapping("/roles")
    public Object roles() {
        return renderer.render("Escalated/Admin/Roles/Index", pages.roles());
    }

    @GetMapping("/roles/create")
    public Object createRole() {
        return renderer.render("Escalated/Admin/Roles/Form", pages.roleForm(null));
    }

    @GetMapping("/roles/{role:\\d+}/edit")
    public Object editRole(@PathVariable("role") Long id) {
        return renderer.render("Escalated/Admin/Roles/Form", pages.roleForm(id));
    }

    @PostMapping("/roles")
    public ResponseEntity<Void> storeRole(HttpServletRequest request) {
        pages.saveRole(null, UiInput.of(request));
        inertia.flash("success", "Role created.");
        return inertia.redirect(routes.url("escalated.admin.roles.index"));
    }

    @PutMapping("/roles/{role:\\d+}")
    public ResponseEntity<Void> updateRole(@PathVariable("role") Long id, HttpServletRequest request) {
        if (pages.isSystemRole(id)) {
            inertia.flash("error", "System roles cannot be modified.");
            return inertia.back(routes.url("escalated.admin.roles.index"));
        }
        pages.saveRole(id, UiInput.of(request));
        inertia.flash("success", "Role updated.");
        return inertia.redirect(routes.url("escalated.admin.roles.index"));
    }

    @DeleteMapping("/roles/{role:\\d+}")
    public ResponseEntity<Void> destroyRole(@PathVariable("role") Long id) {
        if (pages.isSystemRole(id)) {
            inertia.flash("error", "System roles cannot be deleted.");
            return inertia.back(routes.url("escalated.admin.roles.index"));
        }
        pages.deleteRole(id);
        inertia.flash("success", "Role deleted.");
        return inertia.redirect(routes.url("escalated.admin.roles.index"));
    }

    // ── API tokens ──────────────────────────────────────────────────────

    @GetMapping("/api-tokens")
    public Object apiTokens() {
        return renderer.render("Escalated/Admin/ApiTokens/Index", pages.apiTokens());
    }

    @PostMapping("/api-tokens")
    public ResponseEntity<Void> issueToken(HttpServletRequest request) {
        String plainText = pages.issueToken(UiInput.of(request));
        inertia.flash("success", "API token created.");
        inertia.flash("plain_text_token", plainText);
        return inertia.back(routes.url("escalated.admin.api-tokens.index"));
    }

    @PutMapping("/api-tokens/{token:\\d+}")
    public ResponseEntity<Void> updateToken(@PathVariable("token") Long id, HttpServletRequest request) {
        pages.updateToken(id, UiInput.of(request));
        inertia.flash("success", "Token updated.");
        return inertia.back(routes.url("escalated.admin.api-tokens.index"));
    }

    @DeleteMapping("/api-tokens/{token:\\d+}")
    public ResponseEntity<Void> revokeToken(@PathVariable("token") Long id) {
        pages.revokeToken(id);
        inertia.flash("success", "Token revoked.");
        return inertia.back(routes.url("escalated.admin.api-tokens.index"));
    }

    private Long agentId(Authentication authentication) {
        return agents.findByEmail(authentication.getName()).map(AgentProfile::getId).orElse(null);
    }
}
