package dev.escalated.ui.controllers;

import dev.escalated.models.CustomField;
import dev.escalated.services.CustomFieldService;
import dev.escalated.services.CustomObjectService;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * The custom field and custom object pages, as escalated-laravel's {@code routes/admin.php}:
 * {@code Escalated/Admin/CustomFields/Index} and {@code Form}, and
 * {@code Escalated/Admin/CustomObjects/Index}, {@code Form} and {@code Records}. Admins only (the web
 * security chain).
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin")
public class AdminCustomFieldsUiController {

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final CustomFieldService fields;
    private final CustomObjectService objects;
    private final UiRoutes routes;

    public AdminCustomFieldsUiController(EscalatedUiRenderer renderer, Inertia inertia, CustomFieldService fields,
                                         CustomObjectService objects, UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.fields = fields;
        this.objects = objects;
        this.routes = routes;
    }

    // ── Custom fields ───────────────────────────────────────────────────

    @GetMapping("/custom-fields")
    public Object fields() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CustomField field : fields.findAll()) {
            rows.add(CustomFieldService.row(field));
        }
        return renderer.render("Escalated/Admin/CustomFields/Index", Map.of("fields", rows));
    }

    @GetMapping("/custom-fields/create")
    public Object createField() {
        return renderer.render("Escalated/Admin/CustomFields/Form", Map.of("contexts", CustomFieldService.CONTEXTS));
    }

    @GetMapping("/custom-fields/{field:\\d+}/edit")
    public Object editField(@PathVariable("field") Long id) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("field", CustomFieldService.row(fields.findById(id)));
        props.put("contexts", CustomFieldService.CONTEXTS);
        return renderer.render("Escalated/Admin/CustomFields/Form", props);
    }

    @PostMapping("/custom-fields")
    public ResponseEntity<Void> storeField(HttpServletRequest request) {
        fields.save(null, UiInput.of(request).all());
        return toIndex("custom-fields", "Custom field created.");
    }

    @RequestMapping(value = "/custom-fields/{field:\\d+}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<Void> updateField(@PathVariable("field") Long id, HttpServletRequest request) {
        fields.save(id, UiInput.of(request).all());
        return toIndex("custom-fields", "Custom field updated.");
    }

    @DeleteMapping("/custom-fields/{field:\\d+}")
    public ResponseEntity<Void> destroyField(@PathVariable("field") Long id) {
        fields.delete(id);
        return toIndex("custom-fields", "Custom field deleted.");
    }

    @PostMapping("/custom-fields/reorder")
    public ResponseEntity<Void> reorderFields(HttpServletRequest request) {
        fields.reorder(UiInput.of(request).raw("positions"));
        inertia.flash("success", "Fields reordered.");
        return inertia.back(routes.url("escalated.admin.custom-fields.index"));
    }

    // ── Custom objects ──────────────────────────────────────────────────

    @GetMapping("/custom-objects")
    public Object objects() {
        return renderer.render("Escalated/Admin/CustomObjects/Index", Map.of("objects", objects.index()));
    }

    @GetMapping("/custom-objects/create")
    public Object createObject() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("object", null);
        return renderer.render("Escalated/Admin/CustomObjects/Form", props);
    }

    @GetMapping("/custom-objects/{object:\\d+}/edit")
    public Object editObject(@PathVariable("object") Long id) {
        return renderer.render("Escalated/Admin/CustomObjects/Form", Map.of("object", objects.show(id)));
    }

    @PostMapping("/custom-objects")
    public ResponseEntity<Void> storeObject(HttpServletRequest request) {
        objects.save(null, UiInput.of(request).all());
        return toIndex("custom-objects", "Custom object created.");
    }

    @RequestMapping(value = "/custom-objects/{object:\\d+}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<Void> updateObject(@PathVariable("object") Long id, HttpServletRequest request) {
        objects.save(id, UiInput.of(request).all());
        return toIndex("custom-objects", "Custom object updated.");
    }

    @DeleteMapping("/custom-objects/{object:\\d+}")
    public ResponseEntity<Void> destroyObject(@PathVariable("object") Long id) {
        objects.delete(id);
        return toIndex("custom-objects", "Custom object deleted.");
    }

    @GetMapping("/custom-objects/{object:\\d+}/records")
    public Object records(@PathVariable("object") Long id) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("object", objects.show(id));
        props.put("records", objects.records(id));
        return renderer.render("Escalated/Admin/CustomObjects/Records", props);
    }

    @PostMapping("/custom-objects/{object:\\d+}/records")
    public ResponseEntity<Void> storeRecord(@PathVariable("object") Long id, HttpServletRequest request) {
        objects.saveRecord(id, null, UiInput.of(request).all());
        return back(id, "Record created.");
    }

    @RequestMapping(value = "/custom-objects/{object:\\d+}/records/{record:\\d+}",
            method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<Void> updateRecord(@PathVariable("object") Long id, @PathVariable("record") Long record,
                                             HttpServletRequest request) {
        objects.saveRecord(id, record, UiInput.of(request).all());
        return back(id, "Record updated.");
    }

    @DeleteMapping("/custom-objects/{object:\\d+}/records/{record:\\d+}")
    public ResponseEntity<Void> destroyRecord(@PathVariable("object") Long id, @PathVariable("record") Long record) {
        objects.deleteRecord(id, record);
        return back(id, "Record deleted.");
    }

    private ResponseEntity<Void> back(Long objectId, String message) {
        inertia.flash("success", message);
        return inertia.back(routes.url("escalated.admin.custom-objects.records", objectId));
    }

    private ResponseEntity<Void> toIndex(String plural, String message) {
        inertia.flash("success", message);
        return inertia.redirect(routes.url("escalated.admin." + plural + ".index"));
    }
}
