package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.CustomObject;
import dev.escalated.models.CustomObjectRecord;
import dev.escalated.repositories.CustomObjectRecordRepository;
import dev.escalated.repositories.CustomObjectRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom objects and their records, as escalated-laravel's {@code CustomObject},
 * {@code CustomObjectRecord} and {@code Admin\CustomObjectController}. A record's {@code data} is
 * checked against its object's schema: required keys non-empty, {@code number} numeric,
 * {@code date} a valid date, {@code select} one of its options, and no keys the schema lacks.
 */
@Service
public class CustomObjectService {

    public static final List<String> FIELD_TYPES = List.of("text", "number", "select", "date", "lookup");

    private static final Pattern ALPHA_DASH = Pattern.compile("[\\p{L}\\p{N}_-]+");

    private final CustomObjectRepository objects;
    private final CustomObjectRecordRepository records;

    public CustomObjectService(CustomObjectRepository objects, CustomObjectRecordRepository records) {
        this.objects = objects;
        this.records = records;
    }

    /** Every object by name, each with its {@code records_count}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Map<String, Object>> index() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : records.countByObject()) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CustomObject object : objects.findAllByOrderByNameAscIdAsc()) {
            Map<String, Object> row = row(object);
            row.put("records_count", counts.getOrDefault(object.getId(), 0L));
            rows.add(row);
        }
        return rows;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public CustomObject find(Long id) {
        return objects.findById(id).orElseThrow(() -> new EntityNotFoundException("Custom object not found: " + id));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> show(Long id) {
        return row(find(id));
    }

    /** The object's records, newest first. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Map<String, Object>> records(Long objectId) {
        find(objectId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CustomObjectRecord record : records.findByObjectIdOrderByIdDesc(objectId)) {
            rows.add(record(record));
        }
        return rows;
    }

    /** Creates ({@code id == null}) or updates an object from {@code name, slug, fields_schema}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public CustomObject save(Long id, Map<String, Object> input) {
        CustomObject object = id == null ? new CustomObject() : find(id);
        Map<String, List<String>> errors = new LinkedHashMap<>();

        String name = CustomFieldService.text(input.get("name"));
        if (name == null) {
            errors.put("name", List.of("The name field is required."));
        } else if (name.length() > 255) {
            errors.put("name", List.of("The name field must not be greater than 255 characters."));
        }

        String slug = CustomFieldService.text(input.get("slug"));
        if (slug == null) {
            errors.put("slug", List.of("The slug field is required."));
        } else if (slug.length() > 100) {
            errors.put("slug", List.of("The slug field must not be greater than 100 characters."));
        } else if (!ALPHA_DASH.matcher(slug).matches()) {
            errors.put("slug", List.of("The slug field must only contain letters, numbers, dashes, and underscores."));
        } else if (objects.findBySlug(slug).filter(other -> !other.getId().equals(object.getId())).isPresent()) {
            errors.put("slug", List.of("The slug has already been taken."));
        }

        List<Map<String, Object>> schema = schema(input.get("fields_schema"), errors);

        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        object.setName(name);
        object.setSlug(slug);
        object.setFieldsSchema(CustomFieldService.write(schema));
        return objects.save(object);
    }

    /** Deletes the object and its records. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void delete(Long id) {
        CustomObject object = find(id);
        records.deleteByObjectId(object.getId());
        objects.delete(object);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public CustomObjectRecord saveRecord(Long objectId, Long recordId, Map<String, Object> input) {
        CustomObject object = find(objectId);
        CustomObjectRecord record = recordId == null ? new CustomObjectRecord() : findRecord(objectId, recordId);
        Map<String, Object> data = data(object, input.get("data"));
        record.setObjectId(object.getId());
        record.setData(CustomFieldService.write(data));
        return records.save(record);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteRecord(Long objectId, Long recordId) {
        find(objectId);
        records.delete(findRecord(objectId, recordId));
    }

    // ── Shapes ──────────────────────────────────────────────────────────

    public static Map<String, Object> row(CustomObject object) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", object.getId());
        row.put("name", object.getName());
        row.put("slug", object.getSlug());
        Object schema = CustomFieldService.read(object.getFieldsSchema());
        row.put("fields_schema", schema instanceof List ? schema : List.of());
        row.put("created_at", object.getCreatedAt() == null ? null : object.getCreatedAt().toString());
        row.put("updated_at", object.getUpdatedAt() == null ? null : object.getUpdatedAt().toString());
        return row;
    }

    public static Map<String, Object> record(CustomObjectRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.getId());
        row.put("object_id", record.getObjectId());
        Object data = CustomFieldService.read(record.getData());
        row.put("data", data instanceof Map ? data : Map.of());
        row.put("created_at", record.getCreatedAt() == null ? null : record.getCreatedAt().toString());
        row.put("updated_at", record.getUpdatedAt() == null ? null : record.getUpdatedAt().toString());
        return row;
    }

    // ── Validation ──────────────────────────────────────────────────────

    private CustomObjectRecord findRecord(Long objectId, Long recordId) {
        return records.findByIdAndObjectId(recordId, objectId)
                .orElseThrow(() -> new EntityNotFoundException("Record not found: " + recordId));
    }

    private static List<Map<String, Object>> schema(Object given, Map<String, List<String>> errors) {
        List<Map<String, Object>> schema = new ArrayList<>();
        if (!(given instanceof List<?> list) || list.isEmpty()) {
            errors.put("fields_schema", List.of(given == null || given instanceof List
                    ? "The fields schema field is required." : "The fields schema field must be an array."));
            return schema;
        }
        for (int index = 0; index < list.size(); index++) {
            String prefix = "fields_schema." + index;
            if (!(list.get(index) instanceof Map<?, ?> item)) {
                errors.put(prefix, List.of("The " + prefix + " field must be an array."));
                continue;
            }
            Map<String, Object> field = new LinkedHashMap<>();
            String name = CustomFieldService.text(item.get("name"));
            if (name == null) {
                errors.put(prefix + ".name", List.of("The " + prefix + ".name field is required."));
            } else if (name.length() > 100) {
                errors.put(prefix + ".name", List.of("The " + prefix + ".name field must not be greater than 100 characters."));
            }
            String type = CustomFieldService.text(item.get("type"));
            if (type == null) {
                errors.put(prefix + ".type", List.of("The " + prefix + ".type field is required."));
            } else if (!FIELD_TYPES.contains(type)) {
                errors.put(prefix + ".type", List.of("The selected " + prefix + ".type is invalid."));
            }
            field.put("name", name);
            field.put("type", type);
            if (item.containsKey("required")) {
                Object required = item.get("required");
                if (!(required instanceof Boolean) && !(required instanceof Number)
                        && !(required instanceof String s && List.of("0", "1", "true", "false").contains(s))) {
                    errors.put(prefix + ".required", List.of("The " + prefix + ".required field must be true or false."));
                }
                field.put("required", CustomFieldService.bool(required, false));
            }
            if (item.containsKey("options")) {
                Object options = item.get("options");
                if (!(options instanceof List<?>)) {
                    errors.put(prefix + ".options", List.of("The " + prefix + ".options field must be an array."));
                } else {
                    field.put("options", options);
                }
            }
            schema.add(field);
        }
        return schema;
    }

    private static Map<String, Object> data(CustomObject object, Object given) {
        if (!(given instanceof Map<?, ?> map)) {
            throw CustomFieldService.invalid("data", given == null || given instanceof List<?> l && l.isEmpty()
                    ? "The data field is required." : "The data field must be an array.");
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        Object parsed = CustomFieldService.read(object.getFieldsSchema());
        if (parsed instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> field && field.get("name") != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) field;
                    schema.put(String.valueOf(field.get("name")), typed);
                }
            }
        }

        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            String name = String.valueOf(key);
            if (!schema.containsKey(name)) {
                errors.put("data." + name, List.of("The data." + name + " field is not part of this object."));
            } else {
                data.put(name, value);
            }
        });
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String name = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> field = (Map<String, Object>) entry.getValue();
            String key = "data." + name;
            Object value = data.get(name);
            boolean blank = value == null || value instanceof String s && s.isBlank();
            if (blank) {
                if (CustomFieldService.bool(field.get("required"), false)) {
                    errors.put(key, List.of("The " + key + " field is required."));
                }
                continue;
            }
            String type = String.valueOf(field.get("type"));
            switch (type) {
                case "number" -> {
                    if (value instanceof Boolean || !numeric(value)) {
                        errors.put(key, List.of("The " + key + " field must be a number."));
                    }
                }
                case "date" -> {
                    if (!(value instanceof String text) || !date(text.trim())) {
                        errors.put(key, List.of("The " + key + " field must be a valid date."));
                    }
                }
                case "select" -> {
                    Object options = field.get("options");
                    boolean known = false;
                    if (options instanceof List<?> list) {
                        for (Object option : list) {
                            known |= option != null && String.valueOf(option).equals(String.valueOf(value));
                        }
                    }
                    if (!known) {
                        errors.put(key, List.of("The selected " + key + " is invalid."));
                    }
                }
                default -> {
                    // text and lookup: any scalar.
                    if (value instanceof Map || value instanceof List) {
                        errors.put(key, List.of("The " + key + " field must be a string."));
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return data;
    }

    private static boolean numeric(Object value) {
        try {
            new BigDecimal(String.valueOf(value).trim());
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    private static boolean date(String text) {
        try {
            LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
            return text.length() == 10 || text.charAt(10) == 'T' || text.charAt(10) == ' ';
        } catch (DateTimeParseException | StringIndexOutOfBoundsException invalid) {
            return false;
        }
    }
}
