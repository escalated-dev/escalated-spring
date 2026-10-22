package dev.escalated.ui.controllers;

import dev.escalated.ui.AdminSlaUiPages;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The SLA policy and escalation rule pages, as escalated-laravel's {@code routes/admin.php}:
 * {@code Escalated/Admin/SlaPolicies/Index} and {@code Form}, and
 * {@code Escalated/Admin/EscalationRules/Index} and {@code Form}. Admins only (the web security chain).
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin")
public class AdminSlaUiController {

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final AdminSlaUiPages pages;
    private final UiRoutes routes;

    public AdminSlaUiController(EscalatedUiRenderer renderer, Inertia inertia, AdminSlaUiPages pages,
                                UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.pages = pages;
        this.routes = routes;
    }

    // ── SLA policies ────────────────────────────────────────────────────

    @GetMapping("/sla-policies")
    public Object policies() {
        return renderer.render("Escalated/Admin/SlaPolicies/Index", pages.policies());
    }

    @GetMapping("/sla-policies/create")
    public Object createPolicy() {
        return renderer.render("Escalated/Admin/SlaPolicies/Form", pages.policyForm(null));
    }

    @GetMapping("/sla-policies/{policy:\\d+}/edit")
    public Object editPolicy(@PathVariable("policy") Long id) {
        return renderer.render("Escalated/Admin/SlaPolicies/Form", pages.policyForm(id));
    }

    @PostMapping("/sla-policies")
    public ResponseEntity<Void> storePolicy(HttpServletRequest request) {
        pages.savePolicy(null, UiInput.of(request));
        return toIndex("sla-policies", "SLA policy created.");
    }

    @PutMapping("/sla-policies/{policy:\\d+}")
    public ResponseEntity<Void> updatePolicy(@PathVariable("policy") Long id, HttpServletRequest request) {
        pages.savePolicy(id, UiInput.of(request));
        return toIndex("sla-policies", "SLA policy updated.");
    }

    @DeleteMapping("/sla-policies/{policy:\\d+}")
    public ResponseEntity<Void> destroyPolicy(@PathVariable("policy") Long id) {
        pages.deletePolicy(id);
        return toIndex("sla-policies", "SLA policy deleted.");
    }

    // ── Escalation rules ────────────────────────────────────────────────

    @GetMapping("/escalation-rules")
    public Object rules() {
        return renderer.render("Escalated/Admin/EscalationRules/Index", pages.rules());
    }

    @GetMapping("/escalation-rules/create")
    public Object createRule() {
        return renderer.render("Escalated/Admin/EscalationRules/Form", pages.ruleForm(null));
    }

    @GetMapping("/escalation-rules/{rule:\\d+}/edit")
    public Object editRule(@PathVariable("rule") Long id) {
        return renderer.render("Escalated/Admin/EscalationRules/Form", pages.ruleForm(id));
    }

    @PostMapping("/escalation-rules")
    public ResponseEntity<Void> storeRule(HttpServletRequest request) {
        pages.saveRule(null, UiInput.of(request));
        return toIndex("escalation-rules", "Escalation rule created.");
    }

    @PutMapping("/escalation-rules/{rule:\\d+}")
    public ResponseEntity<Void> updateRule(@PathVariable("rule") Long id, HttpServletRequest request) {
        pages.saveRule(id, UiInput.of(request));
        return toIndex("escalation-rules", "Escalation rule updated.");
    }

    @DeleteMapping("/escalation-rules/{rule:\\d+}")
    public ResponseEntity<Void> destroyRule(@PathVariable("rule") Long id) {
        pages.deleteRule(id);
        return toIndex("escalation-rules", "Escalation rule deleted.");
    }

    private ResponseEntity<Void> toIndex(String plural, String message) {
        inertia.flash("success", message);
        return inertia.redirect(routes.url("escalated.admin." + plural + ".index"));
    }
}
