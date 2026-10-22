package dev.escalated.ui.controllers;

import dev.escalated.ui.AdminRoutingUiPages;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
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
 * The routing and audit admin pages, as escalated-laravel's {@code routes/admin.php}:
 * {@code Escalated/Admin/Skills/Index} and {@code Form}, {@code Capacity/Index},
 * {@code BusinessHours/Index} and {@code Form}, and {@code AuditLog/Index}. Admins only (the web
 * security chain).
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin")
public class AdminRoutingUiController {

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final AdminRoutingUiPages pages;
    private final UiRoutes routes;

    public AdminRoutingUiController(EscalatedUiRenderer renderer, Inertia inertia, AdminRoutingUiPages pages,
                                    UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.pages = pages;
        this.routes = routes;
    }

    // ── Skills ──────────────────────────────────────────────────────────

    @GetMapping("/skills")
    public Object skills() {
        return renderer.render("Escalated/Admin/Skills/Index", pages.skills());
    }

    @GetMapping("/skills/create")
    public Object createSkill() {
        return renderer.render("Escalated/Admin/Skills/Form", pages.skillForm(null));
    }

    @GetMapping("/skills/{skill:\\d+}/edit")
    public Object editSkill(@PathVariable("skill") Long id) {
        return renderer.render("Escalated/Admin/Skills/Form", pages.skillForm(id));
    }

    @PostMapping("/skills")
    public ResponseEntity<Void> storeSkill(HttpServletRequest request) {
        pages.saveSkill(null, UiInput.of(request));
        return to("escalated.admin.skills.index", "Skill created.");
    }

    @PutMapping("/skills/{skill:\\d+}")
    public ResponseEntity<Void> updateSkill(@PathVariable("skill") Long id, HttpServletRequest request) {
        pages.saveSkill(id, UiInput.of(request));
        return to("escalated.admin.skills.index", "Skill updated.");
    }

    @PatchMapping("/skills/{skill:\\d+}")
    public ResponseEntity<Void> patchSkill(@PathVariable("skill") Long id, HttpServletRequest request) {
        return updateSkill(id, request);
    }

    @DeleteMapping("/skills/{skill:\\d+}")
    public ResponseEntity<Void> destroySkill(@PathVariable("skill") Long id) {
        pages.deleteSkill(id);
        return to("escalated.admin.skills.index", "Skill deleted.");
    }

    // ── Capacity ────────────────────────────────────────────────────────

    @GetMapping("/capacity")
    public Object capacity() {
        return renderer.render("Escalated/Admin/Capacity/Index", pages.capacity());
    }

    @PutMapping("/capacity/{capacity:\\d+}")
    public ResponseEntity<Void> updateCapacity(@PathVariable("capacity") Long id, HttpServletRequest request) {
        pages.updateCapacity(id, UiInput.of(request));
        inertia.flash("success", "Capacity updated.");
        return inertia.back(routes.url("escalated.admin.capacity.index"));
    }

    // ── Business hours ──────────────────────────────────────────────────

    @GetMapping("/business-hours")
    public Object businessHours() {
        return renderer.render("Escalated/Admin/BusinessHours/Index", pages.businessHours());
    }

    @GetMapping("/business-hours/create")
    public Object createBusinessHours() {
        return renderer.render("Escalated/Admin/BusinessHours/Form", pages.businessHoursForm(null));
    }

    @GetMapping("/business-hours/{schedule:\\d+}/edit")
    public Object editBusinessHours(@PathVariable("schedule") Long id) {
        return renderer.render("Escalated/Admin/BusinessHours/Form", pages.businessHoursForm(id));
    }

    @PostMapping("/business-hours")
    public ResponseEntity<Void> storeBusinessHours(HttpServletRequest request) {
        pages.saveBusinessHours(null, UiInput.of(request));
        return to("escalated.admin.business-hours.index", "Business schedule created.");
    }

    @PutMapping("/business-hours/{schedule:\\d+}")
    public ResponseEntity<Void> updateBusinessHours(@PathVariable("schedule") Long id, HttpServletRequest request) {
        pages.saveBusinessHours(id, UiInput.of(request));
        return to("escalated.admin.business-hours.index", "Business schedule updated.");
    }

    @PatchMapping("/business-hours/{schedule:\\d+}")
    public ResponseEntity<Void> patchBusinessHours(@PathVariable("schedule") Long id, HttpServletRequest request) {
        return updateBusinessHours(id, request);
    }

    @DeleteMapping("/business-hours/{schedule:\\d+}")
    public ResponseEntity<Void> destroyBusinessHours(@PathVariable("schedule") Long id) {
        pages.deleteBusinessHours(id);
        return to("escalated.admin.business-hours.index", "Business schedule deleted.");
    }

    // ── Audit log ───────────────────────────────────────────────────────

    @GetMapping("/audit-log")
    public Object auditLog(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/AuditLog/Index", pages.auditLog(query));
    }

    private ResponseEntity<Void> to(String route, String message) {
        inertia.flash("success", message);
        return inertia.redirect(routes.url(route));
    }
}
