package dev.escalated.ui.controllers;

import dev.escalated.services.ValidationFailedException;
import dev.escalated.ui.AdminSettingsSubPages;
import dev.escalated.ui.DatabaseConnections;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The admin Settings sub-pages, on escalated-laravel's routes ({@code routes/admin.php}):
 * CsatSettings, DataRetention, DatabaseConnection (read-only), EmailSettings and PublicTickets.
 * Admins only (the web security chain). A save goes back to its page with a {@code success} flash,
 * or with {@code errors}.
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin/settings")
public class AdminSettingsUiController {

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final AdminSettingsSubPages pages;
    private final DatabaseConnections connections;
    private final UiRoutes routes;

    public AdminSettingsUiController(
            EscalatedUiRenderer renderer,
            Inertia inertia,
            AdminSettingsSubPages pages,
            DatabaseConnections connections,
            UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.pages = pages;
        this.connections = connections;
        this.routes = routes;
    }

    /** Validates and saves, then back to the page with the flash, or with the errors. */
    private ResponseEntity<Void> save(HttpServletRequest request, String route, String message,
                                      Consumer<UiInput> saver) {
        try {
            saver.accept(UiInput.of(request));
        } catch (ValidationFailedException invalid) {
            inertia.errors(invalid.getErrors());
            return inertia.back(routes.url(route));
        }
        inertia.flash("success", message);
        return inertia.back(routes.url(route));
    }

    @GetMapping("/csat")
    public Object csat() {
        return renderer.render("Escalated/Admin/Settings/CsatSettings", pages.csat());
    }

    @PostMapping("/csat")
    public ResponseEntity<Void> updateCsat(HttpServletRequest request) {
        return save(request, "escalated.admin.settings.csat", "CSAT settings updated.", pages::saveCsat);
    }

    @GetMapping("/data-retention")
    public Object dataRetention() {
        return renderer.render("Escalated/Admin/Settings/DataRetention", pages.dataRetention());
    }

    @PostMapping("/data-retention")
    public ResponseEntity<Void> updateDataRetention(HttpServletRequest request) {
        return save(request, "escalated.admin.settings.data-retention", "Data retention settings updated.",
                pages::saveDataRetention);
    }

    @GetMapping("/email")
    public Object email() {
        return renderer.render("Escalated/Admin/Settings/EmailSettings", pages.email());
    }

    @PostMapping("/email")
    public ResponseEntity<Void> updateEmail(HttpServletRequest request) {
        return save(request, "escalated.admin.settings.email", "Email settings updated.", pages::saveEmail);
    }

    @GetMapping("/public-tickets")
    public Object publicTickets() {
        return renderer.render("Escalated/Admin/Settings/PublicTickets", pages.publicTickets());
    }

    /** The page posts; Laravel's route is a PUT, so both are accepted. */
    @PostMapping("/public-tickets")
    public ResponseEntity<Void> updatePublicTickets(HttpServletRequest request) {
        return save(request, "escalated.admin.settings.public-tickets", "Guest policy updated.",
                pages::savePublicTickets);
    }

    @PutMapping("/public-tickets")
    public ResponseEntity<Void> putPublicTickets(HttpServletRequest request) {
        return updatePublicTickets(request);
    }

    // ── Database connection (read-only) ─────────────────────────────────

    @GetMapping("/database")
    public Object database() {
        return renderer.render("Escalated/Admin/Settings/DatabaseConnection", connections.props());
    }

    /** The connection is configuration here, so a switch is always refused. */
    @PostMapping("/database")
    public ResponseEntity<Void> updateDatabase() {
        inertia.flash("error", "The database connection is set in configuration (spring.datasource or"
                + " escalated.datasource) and cannot be changed from here.");
        return inertia.back(routes.url("escalated.admin.settings.database"));
    }

    /**
     * Probes one configured connection. An API client gets its description as JSON (422 for a
     * missing or unknown name); a page visit goes back with a flash, or with the error.
     */
    @PostMapping("/database/test")
    public ResponseEntity<?> testDatabase(HttpServletRequest request) {
        String name = UiInput.of(request).string("connection");
        String error = null;
        if (name == null) {
            error = "The connection field is required.";
        } else if (name.length() > 64) {
            error = "The connection field must not be greater than 64 characters.";
        } else if (!connections.isConfigured(name)) {
            error = "[" + name + "] is not a configured connection.";
        }
        boolean page = Inertia.isInertia(request);
        String back = routes.url("escalated.admin.settings.database");
        if (error != null) {
            if (page) {
                inertia.errors(Map.of("connection", List.of(error)));
                return inertia.back(back);
            }
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", error, "errors", Map.of("connection", List.of(error))));
        }
        Map<String, Object> description = connections.describe(name);
        if (!page) {
            return ResponseEntity.ok(description);
        }
        if (Boolean.TRUE.equals(description.get("reachable"))) {
            inertia.flash("success", "Connected to [" + name + "].");
        } else {
            inertia.flash("error", "Could not connect to [" + name + "]: " + description.get("error"));
        }
        return inertia.back(back);
    }
}
