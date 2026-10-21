package dev.escalated.ui.controllers;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.WebhookService;
import dev.escalated.ui.AdminConfigUiPages;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The admin configuration pages, as escalated-laravel's {@code routes/admin.php}:
 * {@code Escalated/Admin/Macros/Index}, {@code CannedResponses/Index}, and
 * {@code Webhooks/Index}, {@code Form} and {@code DeliveryLog}. Admins only (the web security chain).
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin")
public class AdminConfigUiController {

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final AdminConfigUiPages pages;
    private final WebhookService webhooks;
    private final AgentProfileRepository agents;
    private final UiRoutes routes;

    public AdminConfigUiController(EscalatedUiRenderer renderer, Inertia inertia, AdminConfigUiPages pages,
                                   WebhookService webhooks, AgentProfileRepository agents, UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.pages = pages;
        this.webhooks = webhooks;
        this.agents = agents;
        this.routes = routes;
    }

    // ── Macros ──────────────────────────────────────────────────────────

    @GetMapping("/macros")
    public Object macros() {
        return renderer.render("Escalated/Admin/Macros/Index", pages.macros());
    }

    @PostMapping("/macros")
    public ResponseEntity<Void> storeMacro(HttpServletRequest request, Authentication authentication) {
        pages.saveMacro(null, UiInput.of(request), agentId(authentication));
        return back("macros", "Macro created.");
    }

    @PutMapping("/macros/{macro:\\d+}")
    public ResponseEntity<Void> updateMacro(@PathVariable("macro") Long id, HttpServletRequest request,
                                            Authentication authentication) {
        pages.saveMacro(id, UiInput.of(request), agentId(authentication));
        return back("macros", "Macro updated.");
    }

    @DeleteMapping("/macros/{macro:\\d+}")
    public ResponseEntity<Void> destroyMacro(@PathVariable("macro") Long id) {
        pages.deleteMacro(id);
        return back("macros", "Macro deleted.");
    }

    // ── Canned responses ────────────────────────────────────────────────

    @GetMapping("/canned-responses")
    public Object cannedResponses() {
        return renderer.render("Escalated/Admin/CannedResponses/Index", pages.cannedResponses());
    }

    @PostMapping("/canned-responses")
    public ResponseEntity<Void> storeCannedResponse(HttpServletRequest request, Authentication authentication) {
        pages.saveCannedResponse(null, UiInput.of(request), agentId(authentication));
        return back("canned-responses", "Canned response created.");
    }

    @PutMapping("/canned-responses/{response:\\d+}")
    public ResponseEntity<Void> updateCannedResponse(@PathVariable("response") Long id, HttpServletRequest request,
                                                     Authentication authentication) {
        pages.saveCannedResponse(id, UiInput.of(request), agentId(authentication));
        return back("canned-responses", "Canned response updated.");
    }

    @DeleteMapping("/canned-responses/{response:\\d+}")
    public ResponseEntity<Void> destroyCannedResponse(@PathVariable("response") Long id) {
        pages.deleteCannedResponse(id);
        return back("canned-responses", "Canned response deleted.");
    }

    // ── Webhooks ────────────────────────────────────────────────────────

    @GetMapping("/webhooks")
    public Object webhooks() {
        return renderer.render("Escalated/Admin/Webhooks/Index", pages.webhooks());
    }

    @GetMapping("/webhooks/create")
    public Object createWebhook() {
        return renderer.render("Escalated/Admin/Webhooks/Form", pages.webhookForm(null));
    }

    @GetMapping("/webhooks/{webhook:\\d+}/edit")
    public Object editWebhook(@PathVariable("webhook") Long id) {
        return renderer.render("Escalated/Admin/Webhooks/Form", pages.webhookForm(id));
    }

    @PostMapping("/webhooks")
    public ResponseEntity<Void> storeWebhook(HttpServletRequest request) {
        pages.saveWebhook(null, UiInput.of(request));
        return toIndex("webhooks", "Webhook created.");
    }

    @PutMapping("/webhooks/{webhook:\\d+}")
    public ResponseEntity<Void> updateWebhook(@PathVariable("webhook") Long id, HttpServletRequest request) {
        pages.saveWebhook(id, UiInput.of(request));
        return toIndex("webhooks", "Webhook updated.");
    }

    @DeleteMapping("/webhooks/{webhook:\\d+}")
    public ResponseEntity<Void> destroyWebhook(@PathVariable("webhook") Long id) {
        pages.deleteWebhook(id);
        return toIndex("webhooks", "Webhook deleted.");
    }

    @GetMapping("/webhooks/{webhook:\\d+}/deliveries")
    public Object deliveries(@PathVariable("webhook") Long id,
                             @RequestParam(name = "page", defaultValue = "1") int page) {
        return renderer.render("Escalated/Admin/Webhooks/DeliveryLog", pages.deliveries(id, page));
    }

    @PostMapping("/webhooks/deliveries/{delivery:\\d+}/retry")
    public ResponseEntity<Void> retryDelivery(@PathVariable("delivery") Long id) {
        pages.assertDelivery(id);
        webhooks.retryDelivery(id);
        inertia.flash("success", "Webhook delivery retried.");
        return inertia.back(routes.url("escalated.admin.webhooks.index"));
    }

    private ResponseEntity<Void> back(String plural, String message) {
        inertia.flash("success", message);
        return inertia.back(routes.url("escalated.admin." + plural + ".index"));
    }

    private ResponseEntity<Void> toIndex(String plural, String message) {
        inertia.flash("success", message);
        return inertia.redirect(routes.url("escalated.admin." + plural + ".index"));
    }

    private Long agentId(Authentication authentication) {
        return agents.findByEmail(authentication.getName()).map(AgentProfile::getId).orElse(null);
    }
}
