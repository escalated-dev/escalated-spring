package dev.escalated.ui.controllers;

import dev.escalated.contracts.ImportAdapter;
import dev.escalated.models.ImportJob;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ImportJobRepository;
import dev.escalated.services.ImportDispatcher;
import dev.escalated.services.ImportJobService;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * The admin Import wizard, as escalated-laravel's {@code Admin\ImportController}, on its paths
 * and with its props (admins only, the web security chain):
 * <ul>
 *   <li>{@code GET /admin/import}: Index (adapters, the 20 newest jobs)</li>
 *   <li>{@code GET /admin/import/{platform}/connect}: Connect, with
 *       {@code POST .../test-connection} answering {@code {success[, error]}}</li>
 *   <li>{@code GET /admin/import/{platform}/mapping}: Mapping (defaults and source fields)</li>
 *   <li>{@code POST /admin/import/{platform}/review}: Review (writes nothing);
 *       {@code POST .../start} creates the job and queues it</li>
 *   <li>{@code GET /admin/import/progress/{job}}: Progress, polled through
 *       {@code GET /admin/import/status/{job}}; {@code POST /admin/import/{pause,resume,cancel}/{job}}</li>
 * </ul>
 * Sources are the {@link ImportAdapter} beans; Escalated ships none. Tested credentials wait,
 * sealed, in the session until the import starts, and are never sent in a page.
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin/import")
public class AdminImportUiController {

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final ImportJobService imports;
    private final ImportJobRepository jobs;
    private final ImportDispatcher dispatcher;
    private final AgentProfileRepository agents;
    private final UiRoutes routes;

    public AdminImportUiController(EscalatedUiRenderer renderer, Inertia inertia, ImportJobService imports,
                                   ImportJobRepository jobs, ImportDispatcher dispatcher,
                                   AgentProfileRepository agents, UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.imports = imports;
        this.jobs = jobs;
        this.dispatcher = dispatcher;
        this.agents = agents;
        this.routes = routes;
    }

    @GetMapping
    public Object index() {
        List<Map<String, Object>> adapters = new ArrayList<>();
        for (ImportAdapter adapter : imports.adapters()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", adapter.name());
            row.put("display_name", adapter.displayName());
            row.put("credential_fields", adapter.credentialFields());
            adapters.add(row);
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("adapters", adapters);
        props.put("jobs", jobs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20)).stream().map(this::job).toList());
        return renderer.render("Escalated/Admin/Import/Index", props);
    }

    @GetMapping("/{platform}/connect")
    public Object connect(@PathVariable("platform") String platform) {
        ImportAdapter adapter = adapter(platform);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("platform", adapter.name());
        props.put("display_name", adapter.displayName());
        props.put("credential_fields", adapter.credentialFields());
        return renderer.render("Escalated/Admin/Import/Connect", props);
    }

    @PostMapping("/{platform}/test-connection")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable("platform") String platform,
                                                              HttpServletRequest request, HttpSession session) {
        ImportAdapter adapter = adapter(platform);
        Map<String, String> credentials = new LinkedHashMap<>();
        if (UiInput.of(request).raw("credentials") instanceof Map<?, ?> posted) {
            posted.forEach((key, value) -> credentials.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
        }
        boolean success;
        try {
            success = adapter.testConnection(credentials);
        } catch (RuntimeException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(body);
        }
        if (success) {
            session.setAttribute(sessionKey(adapter), imports.credentials().seal(credentials));
        }
        return ResponseEntity.ok(Map.of("success", success));
    }

    @GetMapping("/{platform}/mapping")
    public Object mapping(@PathVariable("platform") String platform, HttpSession session) {
        ImportAdapter adapter = adapter(platform);
        Map<String, String> credentials = stored(session, adapter);
        if (credentials == null) {
            return toConnect(adapter);
        }
        Map<String, Object> mappings = new LinkedHashMap<>();
        for (String type : adapter.entityTypes()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("defaults", adapter.defaultFieldMappings(type));
            entry.put("source_fields", adapter.availableSourceFields(type, credentials));
            mappings.put(type, entry);
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("platform", adapter.name());
        props.put("display_name", adapter.displayName());
        props.put("entity_types", adapter.entityTypes());
        props.put("mappings", mappings);
        return renderer.render("Escalated/Admin/Import/Mapping", props);
    }

    @PostMapping("/{platform}/review")
    public Object review(@PathVariable("platform") String platform, HttpServletRequest request) {
        UiInput input = UiInput.of(request);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("platform", platform);
        props.put("field_mappings", input.raw("field_mappings") instanceof Map<?, ?> m ? m : Map.of());
        props.put("entity_types", input.list("entity_types").stream().map(String::valueOf).toList());
        return renderer.render("Escalated/Admin/Import/Review", props);
    }

    @PostMapping("/{platform}/start")
    public Object start(@PathVariable("platform") String platform, HttpServletRequest request, HttpSession session,
                        Authentication authentication) {
        ImportAdapter adapter = adapter(platform);
        Map<String, String> credentials = stored(session, adapter);
        if (credentials == null) {
            return toConnect(adapter);
        }
        ImportJob job = new ImportJob();
        job.setPlatform(adapter.name());
        job.setStatus("mapping");
        job.setUserId(authentication == null ? null : agents.findByEmail(authentication.getName())
                .map(a -> a.getId().toString()).orElse(authentication.getName()));
        job.setCredentials(imports.credentials().seal(credentials));
        Object mappings = UiInput.of(request).raw("field_mappings");
        job.setFieldMappings(ImportJobService.write(mappings instanceof Map<?, ?> ? mappings : Map.of()));
        job.setProgress("{}");
        job.setErrorLog("[]");
        job = jobs.save(job);

        // The job holds them now, sealed.
        session.removeAttribute(sessionKey(adapter));
        dispatcher.dispatch(job.getId());
        return inertia.redirect(progressUrl(job));
    }

    @GetMapping("/progress/{job}")
    public Object progress(@PathVariable("job") String id) {
        return renderer.render("Escalated/Admin/Import/Progress", Map.of("job", job(find(id))));
    }

    @GetMapping("/status/{job}")
    @ResponseBody
    public Map<String, Object> status(@PathVariable("job") String id) {
        ImportJob job = find(id);
        List<Map<String, Object>> errors = ImportJobService.errorsOf(job);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", job.getStatus());
        body.put("progress", ImportJobService.progressOf(job));
        body.put("error_log", errors.subList(Math.max(0, errors.size() - 50), errors.size()));
        body.put("started_at", time(job.getStartedAt()));
        body.put("completed_at", time(job.getCompletedAt()));
        return body;
    }

    @PostMapping("/pause/{job}")
    public ResponseEntity<Void> pause(@PathVariable("job") String id) {
        ImportJob job = find(id);
        if ("importing".equals(job.getStatus())) {
            jobs.setStatus(job.getId(), "paused");
        }
        inertia.flash("success", "Import paused.");
        return inertia.redirect(progressUrl(job));
    }

    @PostMapping("/resume/{job}")
    public ResponseEntity<Void> resume(@PathVariable("job") String id) {
        ImportJob job = find(id);
        if (job.isResumable()) {
            if ("failed".equals(job.getStatus())) {
                // A failed job goes back through mapping, as Laravel's does.
                job.transitionTo("mapping");
                jobs.save(job);
            }
            dispatcher.dispatch(job.getId());
        }
        inertia.flash("success", "Import resumed.");
        return inertia.redirect(progressUrl(job));
    }

    @PostMapping("/cancel/{job}")
    public ResponseEntity<Void> cancel(@PathVariable("job") String id) {
        ImportJob job = find(id);
        job.setStatus("failed");
        job.setCompletedAt(Instant.now());
        job.setCredentials(null);
        jobs.save(job);
        inertia.flash("success", "Import cancelled.");
        return inertia.redirect(progressUrl(job));
    }

    /**
     * The job as the screens read it, from an explicit field list as escalated-laravel's
     * {@code ImportJob::toPageProps()}, so a column added later is not sent to the browser by
     * default. The credentials are a secret no screen reads; the error log is the newest 50.
     */
    private Map<String, Object> job(ImportJob job) {
        List<Map<String, Object>> errors = ImportJobService.errorsOf(job);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", job.getId());
        row.put("platform", job.getPlatform());
        row.put("status", job.getStatus());
        row.put("progress", ImportJobService.progressOf(job));
        row.put("field_mappings", ImportJobService.mappingsOf(job));
        row.put("error_log", errors.subList(Math.max(0, errors.size() - 50), errors.size()));
        row.put("started_at", time(job.getStartedAt()));
        row.put("completed_at", time(job.getCompletedAt()));
        row.put("created_at", time(job.getCreatedAt()));
        return row;
    }

    private static String time(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private ImportAdapter adapter(String platform) {
        return imports.adapter(platform).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import adapter not found: " + platform));
    }

    private ImportJob find(String id) {
        return jobs.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import job not found."));
    }

    private static String sessionKey(ImportAdapter adapter) {
        return "escalated.import." + adapter.name() + ".credentials";
    }

    private Map<String, String> stored(HttpSession session, ImportAdapter adapter) {
        return session.getAttribute(sessionKey(adapter)) instanceof String sealed
                ? imports.credentials().open(sealed) : null;
    }

    private ResponseEntity<Void> toConnect(ImportAdapter adapter) {
        inertia.flash("error", "Credentials expired. Please reconnect.");
        return inertia.redirect(UiRoutes.PREFIX + "/admin/import/" + adapter.name() + "/connect");
    }

    private String progressUrl(ImportJob job) {
        return routes.url("escalated.admin.import.progress", job.getId());
    }
}
