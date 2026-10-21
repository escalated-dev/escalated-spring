package dev.escalated.ui.controllers;

import dev.escalated.services.AutomationAdminService;
import dev.escalated.services.WorkflowAdminService;
import dev.escalated.ui.AdminUiPages;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The admin pages this port serves, as escalated-laravel's {@code routes/admin.php}: Settings,
 * Workflows (Index, Form), Automations (Index, Form), Departments (Index, Form) and Tags. Admins
 * only (the web security chain). Workflows and automations go through the same services as their
 * JSON admin APIs, so the two surfaces validate and store alike.
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin")
public class AdminUiController {

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final AdminUiPages pages;
    private final WorkflowAdminService workflows;
    private final AutomationAdminService automations;
    private final UiRoutes routes;

    public AdminUiController(
            EscalatedUiRenderer renderer,
            Inertia inertia,
            AdminUiPages pages,
            WorkflowAdminService workflows,
            AutomationAdminService automations,
            UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.pages = pages;
        this.workflows = workflows;
        this.automations = automations;
        this.routes = routes;
    }

    // ── Settings ────────────────────────────────────────────────────────

    @GetMapping("/settings")
    public Object settings() {
        return renderer.render("Escalated/Admin/Settings", pages.settings());
    }

    @PostMapping("/settings")
    public ResponseEntity<Void> updateSettings(HttpServletRequest request) {
        pages.saveSettings(UiInput.of(request));
        inertia.flash("success", "Settings updated.");
        return inertia.back(routes.url("escalated.admin.settings"));
    }

    // ── Workflows ───────────────────────────────────────────────────────

    @GetMapping("/workflows")
    public Object workflows() {
        return renderer.render("Escalated/Admin/Workflows/Index", Map.of("workflows", workflows.list()));
    }

    @GetMapping("/workflows/create")
    public Object createWorkflow() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("workflow", null);
        props.putAll(workflows.options());
        return renderer.render("Escalated/Admin/Workflows/Form", props);
    }

    @GetMapping("/workflows/{workflow:\\d+}")
    public Object editWorkflow(@PathVariable("workflow") Long id) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("workflow", workflows.show(id));
        props.putAll(workflows.options());
        return renderer.render("Escalated/Admin/Workflows/Form", props);
    }

    @PostMapping("/workflows")
    public ResponseEntity<Void> storeWorkflow(HttpServletRequest request) {
        workflows.create(UiInput.of(request).all());
        return toIndex("workflows", "Workflow created.");
    }

    @PutMapping("/workflows/{workflow:\\d+}")
    public ResponseEntity<Void> updateWorkflow(@PathVariable("workflow") Long id, HttpServletRequest request) {
        workflows.update(id, UiInput.of(request).all());
        return toIndex("workflows", "Workflow updated.");
    }

    @DeleteMapping("/workflows/{workflow:\\d+}")
    public ResponseEntity<Void> destroyWorkflow(@PathVariable("workflow") Long id) {
        workflows.delete(id);
        return toIndex("workflows", "Workflow deleted.");
    }

    @PostMapping("/workflows/{workflow:\\d+}/toggle")
    public ResponseEntity<Void> toggleWorkflow(@PathVariable("workflow") Long id) {
        workflows.toggle(id);
        inertia.flash("success", "Workflow updated.");
        return inertia.back(routes.url("escalated.admin.workflows.index"));
    }

    @PostMapping("/workflows/reorder")
    public ResponseEntity<Void> reorderWorkflows(HttpServletRequest request) {
        workflows.reorder(UiInput.of(request).list("workflow_ids"));
        inertia.flash("success", "Workflows reordered.");
        return inertia.back(routes.url("escalated.admin.workflows.index"));
    }

    // ── Automations ─────────────────────────────────────────────────────

    @GetMapping("/automations")
    public Object automations() {
        return renderer.render("Escalated/Admin/Automations/Index", Map.of("automations", automations.list()));
    }

    @GetMapping("/automations/create")
    public Object createAutomation() {
        return renderer.render("Escalated/Admin/Automations/Form", new LinkedHashMap<>());
    }

    @GetMapping("/automations/{automation:\\d+}/edit")
    public Object editAutomation(@PathVariable("automation") Long id) {
        return renderer.render("Escalated/Admin/Automations/Form", Map.of("automation", automations.show(id)));
    }

    @PostMapping("/automations")
    public ResponseEntity<Void> storeAutomation(HttpServletRequest request) {
        automations.create(UiInput.of(request).all());
        return toIndex("automations", "Automation created.");
    }

    @PutMapping("/automations/{automation:\\d+}")
    public ResponseEntity<Void> updateAutomation(@PathVariable("automation") Long id, HttpServletRequest request) {
        automations.update(id, UiInput.of(request).all());
        return toIndex("automations", "Automation updated.");
    }

    @DeleteMapping("/automations/{automation:\\d+}")
    public ResponseEntity<Void> destroyAutomation(@PathVariable("automation") Long id) {
        automations.delete(id);
        return toIndex("automations", "Automation deleted.");
    }

    // ── Departments ─────────────────────────────────────────────────────

    @GetMapping("/departments")
    public Object departments() {
        return renderer.render("Escalated/Admin/Departments/Index", pages.departments());
    }

    @GetMapping("/departments/create")
    public Object createDepartment() {
        return renderer.render("Escalated/Admin/Departments/Form", new LinkedHashMap<>());
    }

    @GetMapping("/departments/{department:\\d+}/edit")
    public Object editDepartment(@PathVariable("department") Long id) {
        return renderer.render("Escalated/Admin/Departments/Form", pages.department(id));
    }

    @PostMapping("/departments")
    public ResponseEntity<Void> storeDepartment(HttpServletRequest request) {
        pages.saveDepartment(null, UiInput.of(request));
        return toIndex("departments", "Department created.");
    }

    @PutMapping("/departments/{department:\\d+}")
    public ResponseEntity<Void> updateDepartment(@PathVariable("department") Long id, HttpServletRequest request) {
        pages.saveDepartment(id, UiInput.of(request));
        return toIndex("departments", "Department updated.");
    }

    @DeleteMapping("/departments/{department:\\d+}")
    public ResponseEntity<Void> destroyDepartment(@PathVariable("department") Long id) {
        try {
            pages.deleteDepartment(id);
        } catch (DataAccessException inUse) {
            inertia.flash("error", "This department is still in use. Deactivate it instead.");
            return inertia.redirect(routes.url("escalated.admin.departments.index"));
        }
        return toIndex("departments", "Department deleted.");
    }

    // ── Tags ────────────────────────────────────────────────────────────

    @GetMapping("/tags")
    public Object tags() {
        return renderer.render("Escalated/Admin/Tags/Index", pages.tags());
    }

    @PostMapping("/tags")
    public ResponseEntity<Void> storeTag(HttpServletRequest request) {
        pages.saveTag(null, UiInput.of(request));
        return tagsBack("Tag created.");
    }

    @PutMapping("/tags/{tag:\\d+}")
    public ResponseEntity<Void> updateTag(@PathVariable("tag") Long id, HttpServletRequest request) {
        pages.saveTag(id, UiInput.of(request));
        return tagsBack("Tag updated.");
    }

    @DeleteMapping("/tags/{tag:\\d+}")
    public ResponseEntity<Void> destroyTag(@PathVariable("tag") Long id) {
        pages.deleteTag(id);
        return tagsBack("Tag deleted.");
    }

    private ResponseEntity<Void> tagsBack(String message) {
        inertia.flash("success", message);
        return inertia.back(routes.url("escalated.admin.tags.index"));
    }

    private ResponseEntity<Void> toIndex(String plural, String message) {
        inertia.flash("success", message);
        return inertia.redirect(routes.url("escalated.admin." + plural + ".index"));
    }
}
