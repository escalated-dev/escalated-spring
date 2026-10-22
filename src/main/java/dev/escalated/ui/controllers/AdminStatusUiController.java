package dev.escalated.ui.controllers;

import dev.escalated.ui.AdminStatusUiPages;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The custom ticket status pages, as escalated-laravel's {@code routes/admin.php}:
 * {@code Escalated/Admin/Statuses/Index} and {@code Form}. Admins only (the web security chain).
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin/statuses")
public class AdminStatusUiController {

    private static final String INDEX = "escalated.admin.statuses.index";

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final AdminStatusUiPages pages;
    private final UiRoutes routes;

    public AdminStatusUiController(EscalatedUiRenderer renderer, Inertia inertia, AdminStatusUiPages pages,
                                   UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.pages = pages;
        this.routes = routes;
    }

    @GetMapping
    public Object index() {
        return renderer.render("Escalated/Admin/Statuses/Index", pages.index());
    }

    @GetMapping("/create")
    public Object create() {
        return renderer.render("Escalated/Admin/Statuses/Form", pages.form(null));
    }

    @GetMapping("/{status:\\d+}/edit")
    public Object edit(@PathVariable("status") Long id) {
        return renderer.render("Escalated/Admin/Statuses/Form", pages.form(id));
    }

    @PostMapping
    public ResponseEntity<Void> store(HttpServletRequest request) {
        pages.save(null, UiInput.of(request));
        return toIndex("success", "Status created.");
    }

    @PutMapping("/{status:\\d+}")
    public ResponseEntity<Void> update(@PathVariable("status") Long id, HttpServletRequest request) {
        pages.save(id, UiInput.of(request));
        return toIndex("success", "Status updated.");
    }

    @PatchMapping("/{status:\\d+}")
    public ResponseEntity<Void> patch(@PathVariable("status") Long id, HttpServletRequest request) {
        return update(id, request);
    }

    @DeleteMapping("/{status:\\d+}")
    public ResponseEntity<Void> destroy(@PathVariable("status") Long id) {
        if (!pages.delete(id)) {
            return toIndex("error", "Built-in statuses cannot be deleted.");
        }
        return toIndex("success", "Status deleted.");
    }

    private ResponseEntity<Void> toIndex(String flash, String message) {
        inertia.flash(flash, message);
        return inertia.redirect(routes.url(INDEX));
    }
}
