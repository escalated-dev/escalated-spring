package dev.escalated.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.CustomField;
import dev.escalated.repositories.CustomFieldRepository;
import jakarta.persistence.EntityNotFoundException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom field definitions, as escalated-laravel's {@code CustomField} and
 * {@code Admin\CustomFieldController}: validated writes, slug generation and the admin shape.
 *
 * <p>This port's table predates Laravel's column names: {@code field_key} is the slug,
 * {@code field_type} the type, {@code is_required}, {@code sort_order} the position and
 * {@code is_active}. Every shape here uses Laravel's names.
 */
@Service
public class CustomFieldService {

    public static final List<String> TYPES =
            List.of("text", "textarea", "select", "multi_select", "checkbox", "date", "number");
    public static final List<String> CONTEXTS = List.of("ticket", "user", "organization");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CustomFieldRepository customFieldRepository;

    public CustomFieldService(CustomFieldRepository customFieldRepository) {
        this.customFieldRepository = customFieldRepository;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<CustomField> findActiveFields() {
        return customFieldRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    /** Every field, ordered by position. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<CustomField> findAll() {
        return customFieldRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public CustomField findById(Long id) {
        return customFieldRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custom field not found: " + id));
    }

    /**
     * Creates ({@code id == null}) or updates a field from Laravel's request keys: {@code name, type,
     * context, options, required, placeholder, description, validation_rules, conditions, position,
     * active} and an optional {@code slug}. On update a key left out keeps its value.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public CustomField save(Long id, Map<String, Object> input) {
        CustomField field = id == null ? new CustomField() : findById(id);
        Map<String, List<String>> errors = new LinkedHashMap<>();

        String name = text(input.get("name"));
        if (name == null && id != null && !input.containsKey("name")) {
            name = field.getName();
        }
        if (name == null) {
            errors.put("name", List.of("The name field is required."));
        } else if (name.length() > 255) {
            errors.put("name", List.of("The name field must not be greater than 255 characters."));
        }

        String type = input.containsKey("type") ? text(input.get("type")) : (id == null ? null : field.getFieldType());
        if (type == null) {
            errors.put("type", List.of("The type field is required."));
        } else if (!TYPES.contains(type)) {
            errors.put("type", List.of("The selected type is invalid."));
        }

        String context = input.containsKey("context") ? text(input.get("context")) : field.getContext();
        if (context == null) {
            context = "ticket";
        }
        if (!CONTEXTS.contains(context)) {
            errors.put("context", List.of("The selected context is invalid."));
        }

        List<String> options = input.containsKey("options") ? options(input.get("options"), errors) : options(field);
        if (type != null && ("select".equals(type) || "multi_select".equals(type))
                && (options == null || options.isEmpty()) && !errors.containsKey("options")) {
            errors.put("options", List.of("The options field must have at least one item for select fields."));
        }

        String placeholder = input.containsKey("placeholder") ? text(input.get("placeholder")) : field.getPlaceholder();
        if (placeholder != null && placeholder.length() > 255) {
            errors.put("placeholder", List.of("The placeholder field must not be greater than 255 characters."));
        }

        Integer position = null;
        if (input.get("position") != null && !(input.get("position") instanceof String blank && blank.isBlank())) {
            position = integer(input.get("position"));
            if (position == null) {
                errors.put("position", List.of("The position field must be an integer."));
            }
        }

        String slug = text(input.get("slug"));
        if (slug != null) {
            if (slug.length() > 255) {
                errors.put("slug", List.of("The slug field must not be greater than 255 characters."));
            } else if (customFieldRepository.findByFieldKey(slug)
                    .filter(other -> !other.getId().equals(field.getId())).isPresent()) {
                errors.put("slug", List.of("The slug has already been taken."));
            }
        }

        Object rules = input.get("validation_rules");
        if (rules != null && !(rules instanceof Map) && !(rules instanceof List)) {
            errors.put("validation_rules", List.of("The validation rules field must be an array."));
        }
        Object conditions = input.get("conditions");
        if (conditions != null && !(conditions instanceof Map) && !(conditions instanceof List)) {
            errors.put("conditions", List.of("The conditions field must be an array."));
        }

        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        field.setName(name);
        field.setFieldType(type);
        field.setContext(context);
        field.setOptions(options == null || options.isEmpty() ? null : write(options));
        field.setPlaceholder(placeholder);
        if (input.containsKey("description")) {
            field.setDescription(text(input.get("description")));
        }
        if (input.containsKey("validation_rules")) {
            field.setValidationRules(rules == null ? null : write(rules));
        }
        if (input.containsKey("conditions")) {
            field.setConditions(conditions == null ? null : write(conditions));
        }
        field.setRequired(bool(input.get("required"), field.isRequired()));
        field.setActive(bool(input.get("active"), field.isActive()));
        if (position != null) {
            field.setSortOrder(position);
        }
        if (slug != null) {
            field.setFieldKey(slug);
        } else if (field.getFieldKey() == null) {
            field.setFieldKey(uniqueSlug(slugify(name)));
        }
        return customFieldRepository.save(field);
    }

    /** Sets each listed field's position: {@code [{id, position}]}, as Laravel's {@code reorder}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void reorder(Object positions) {
        if (!(positions instanceof List<?> items) || items.isEmpty()) {
            throw invalid("positions", "The positions field is required.");
        }
        Map<CustomField, Integer> changes = new LinkedHashMap<>();
        for (int index = 0; index < items.size(); index++) {
            if (!(items.get(index) instanceof Map<?, ?> item)) {
                throw invalid("positions." + index, "The positions." + index + " field must be an array.");
            }
            Integer fieldId = integer(item.get("id"));
            if (fieldId == null) {
                throw invalid("positions." + index + ".id", "The positions." + index + ".id field is required.");
            }
            Integer position = integer(item.get("position"));
            if (position == null) {
                throw invalid("positions." + index + ".position",
                        "The positions." + index + ".position field is required.");
            }
            String key = "positions." + index + ".id";
            CustomField field = customFieldRepository.findById(fieldId.longValue())
                    .orElseThrow(() -> invalid(key, "The selected " + key + " is invalid."));
            changes.put(field, position);
        }
        changes.forEach((field, position) -> {
            field.setSortOrder(position);
            customFieldRepository.save(field);
        });
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void delete(Long id) {
        customFieldRepository.delete(findById(id));
    }

    // ── Shapes ──────────────────────────────────────────────────────────

    /** Every column, under Laravel's names, as the admin pages read a field. */
    public static Map<String, Object> row(CustomField field) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", field.getId());
        row.put("name", field.getName());
        row.put("slug", field.getFieldKey());
        row.put("type", field.getFieldType());
        row.put("context", field.getContext());
        row.put("options", options(field));
        row.put("required", field.isRequired());
        row.put("placeholder", field.getPlaceholder());
        row.put("description", field.getDescription());
        row.put("validation_rules", read(field.getValidationRules()));
        row.put("conditions", read(field.getConditions()));
        row.put("position", field.getSortOrder());
        row.put("active", field.isActive());
        row.put("created_at", field.getCreatedAt() == null ? null : field.getCreatedAt().toString());
        row.put("updated_at", field.getUpdatedAt() == null ? null : field.getUpdatedAt().toString());
        return row;
    }

    /** What a ticket form needs of a field. */
    public static Map<String, Object> definition(CustomField field) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", field.getId());
        row.put("name", field.getName());
        row.put("slug", field.getFieldKey());
        row.put("type", field.getFieldType());
        row.put("options", options(field));
        row.put("required", field.isRequired());
        row.put("placeholder", field.getPlaceholder());
        row.put("description", field.getDescription());
        row.put("position", field.getSortOrder());
        return row;
    }

    /**
     * The field's options as a list: stored as a JSON array, or (written before that) as a
     * comma-separated string. Null when it has none.
     */
    public static List<String> options(CustomField field) {
        String stored = field.getOptions();
        if (stored == null || stored.isBlank()) {
            return null;
        }
        Object parsed = read(stored);
        List<String> options = new ArrayList<>();
        if (parsed instanceof List<?> list) {
            list.forEach(item -> {
                if (item != null) {
                    options.add(String.valueOf(item));
                }
            });
            return options;
        }
        for (String part : stored.split(",")) {
            if (!part.isBlank()) {
                options.add(part.trim());
            }
        }
        return options;
    }

    /** Laravel's {@code Str::slug($name, '_')}, near enough: ASCII, lower case, runs of others to {@code _}. */
    public static String slugify(String name) {
        String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replace("@", "_at_")
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.length() > 240) {
            slug = slug.substring(0, 240);
        }
        return slug.isEmpty() ? "field" : slug;
    }

    private String uniqueSlug(String base) {
        String slug = base;
        int suffix = 2;
        while (customFieldRepository.existsByFieldKey(slug)) {
            slug = base + "_" + suffix++;
        }
        return slug;
    }

    // ── Input ───────────────────────────────────────────────────────────

    private static List<String> options(Object given, Map<String, List<String>> errors) {
        if (given == null || given instanceof String blank && blank.isBlank()) {
            return null;
        }
        List<String> options = new ArrayList<>();
        if (given instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map || item instanceof List) {
                    errors.put("options", List.of("The options field must be a list of strings."));
                    return null;
                }
                if (item != null && !String.valueOf(item).isBlank()) {
                    options.add(String.valueOf(item).trim());
                }
            }
            return options;
        }
        if (given instanceof String text) {
            // The JSON API took options as one string; a JSON array or a comma-separated list.
            Object parsed = read(text);
            if (parsed instanceof List<?>) {
                return options(parsed, errors);
            }
            for (String part : text.split(",")) {
                if (!part.isBlank()) {
                    options.add(part.trim());
                }
            }
            return options;
        }
        errors.put("options", List.of("The options field must be an array."));
        return null;
    }

    static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue() == Math.rint(number.doubleValue()) ? number.intValue() : null;
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException notAnInteger) {
                return null;
            }
        }
        return null;
    }

    static boolean bool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text);
    }

    static Object read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, Object.class);
        } catch (JsonProcessingException notJson) {
            return null;
        }
    }

    static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static ValidationFailedException invalid(String key, String message) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        errors.put(key, List.of(message));
        return new ValidationFailedException(errors);
    }
}
