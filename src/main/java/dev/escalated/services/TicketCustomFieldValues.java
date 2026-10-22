package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.CustomField;
import dev.escalated.models.CustomFieldValue;
import dev.escalated.repositories.CustomFieldRepository;
import dev.escalated.repositories.CustomFieldValueRepository;
import dev.escalated.repositories.TicketRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A ticket's custom field values: the {@code custom_fields} object ticket writes take, keyed by
 * field slug (or numeric id), and the {@code custom_fields} list ticket reads carry.
 *
 * <p>Only active fields with context {@code ticket} count. A write is validated whole before the
 * ticket is touched ({@link #validate}), then stored ({@link #apply}). Stored as text:
 * {@code multi_select} a JSON array, {@code checkbox} {@code "1"}/{@code "0"}, the rest as given.
 */
@Service
public class TicketCustomFieldValues {

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern FORM_KEY = Pattern.compile("^custom_fields\\[([^\\]]+)](?:\\[(\\d*)])?$");

    private final CustomFieldRepository fields;
    private final CustomFieldValueRepository values;
    private final TicketRepository tickets;

    public TicketCustomFieldValues(CustomFieldRepository fields, CustomFieldValueRepository values,
                                   TicketRepository tickets) {
        this.fields = fields;
        this.values = values;
        this.tickets = tickets;
    }

    /** Validated changes, ready for {@link #apply}: each field's stored text, or null to clear it. */
    public static final class Changes {
        private final Map<Long, String> byField = new LinkedHashMap<>();

        public boolean isEmpty() {
            return byField.isEmpty();
        }

        Map<Long, String> byField() {
            return byField;
        }
    }

    /**
     * The {@code custom_fields} a request carried: a JSON object, or form fields named
     * {@code custom_fields[slug]} (and {@code custom_fields[slug][]} for a list). Null when absent.
     */
    public static Object extract(Map<String, ?> body) {
        if (body == null) {
            return null;
        }
        if (body.containsKey("custom_fields")) {
            return body.get("custom_fields");
        }
        Map<String, Object> found = new LinkedHashMap<>();
        Map<String, TreeMap<Integer, Object>> lists = new LinkedHashMap<>();
        body.forEach((key, value) -> {
            Matcher matcher = FORM_KEY.matcher(key);
            if (!matcher.matches()) {
                return;
            }
            if (matcher.group(2) == null) {
                found.put(matcher.group(1), value);
            } else {
                TreeMap<Integer, Object> items = lists.computeIfAbsent(matcher.group(1), k -> new TreeMap<>());
                if (value instanceof List<?> many) {
                    many.forEach(item -> items.put(items.size(), item));
                } else {
                    items.put(matcher.group(2).isEmpty() ? items.size() : Integer.parseInt(matcher.group(2)), value);
                }
            }
        });
        lists.forEach((key, items) -> found.put(key, new ArrayList<>(items.values())));
        return found.isEmpty() ? null : found;
    }

    /** Active ticket fields, by position, as a ticket form shows them. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Map<String, Object>> definitions() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CustomField field : ticketFields()) {
            rows.add(CustomFieldService.definition(field));
        }
        return rows;
    }

    /** {@link #definitions()}, each with the ticket's {@code value} (decoded; null when unset). */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Map<String, Object>> forTicket(Long ticketId) {
        Map<Long, String> stored = new LinkedHashMap<>();
        for (CustomFieldValue value : values.findByTicketId(ticketId)) {
            stored.put(value.getCustomField().getId(), value.getValue());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CustomField field : ticketFields()) {
            Map<String, Object> row = CustomFieldService.definition(field);
            row.put("value", decode(field, stored.get(field.getId())));
            rows.add(row);
        }
        return rows;
    }

    /**
     * Validates {@code raw} (the request's {@code custom_fields}) against the active ticket fields.
     * Creating, every required field must be present and non-empty; updating, only the keys sent are
     * checked. Errors are keyed {@code custom_fields.<slug>}. Writes nothing.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Changes validate(Object raw, boolean creating) {
        Changes changes = new Changes();
        List<CustomField> active = ticketFields();
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, Object> given = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((key, value) -> given.put(String.valueOf(key), value));
        } else if (raw != null && !(raw instanceof List<?> empty && empty.isEmpty())) {
            throw CustomFieldService.invalid("custom_fields", "The custom fields field must be an object.");
        }

        Map<Long, Object> byField = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : given.entrySet()) {
            CustomField field = resolve(active, entry.getKey());
            if (field == null) {
                errors.put("custom_fields." + entry.getKey(),
                        List.of("The custom field " + entry.getKey() + " does not exist."));
                continue;
            }
            byField.put(field.getId(), entry.getValue());
        }

        for (CustomField field : active) {
            String key = "custom_fields." + field.getFieldKey();
            boolean sent = byField.containsKey(field.getId());
            Object value = byField.get(field.getId());
            if (!sent) {
                if (creating && field.isRequired()) {
                    errors.put(key, List.of("The " + field.getName() + " field is required."));
                }
                continue;
            }
            if (empty(value)) {
                if (field.isRequired()) {
                    errors.put(key, List.of("The " + field.getName() + " field is required."));
                } else {
                    changes.byField().put(field.getId(), null);
                }
                continue;
            }
            String problem = null;
            String stored = null;
            List<String> options = CustomFieldService.options(field);
            switch (field.getFieldType()) {
                case "number" -> {
                    BigDecimal number = number(value);
                    if (number == null) {
                        problem = "The " + field.getName() + " field must be a number.";
                    } else {
                        stored = value instanceof Number ? number.stripTrailingZeros().toPlainString()
                                : String.valueOf(value).trim();
                    }
                }
                case "date" -> {
                    String text = value instanceof String s ? s.trim() : null;
                    if (text == null || !DATE.matcher(text).matches() || !validDate(text)) {
                        problem = "The " + field.getName() + " field must be a valid date (YYYY-MM-DD).";
                    } else {
                        stored = text;
                    }
                }
                case "checkbox" -> {
                    Boolean flag = checkbox(value);
                    if (flag == null) {
                        problem = "The " + field.getName() + " field must be true or false.";
                    } else {
                        stored = flag ? "1" : "0";
                    }
                }
                case "select" -> {
                    String choice = value instanceof String || value instanceof Number ? String.valueOf(value).trim() : null;
                    if (choice == null || options == null || !options.contains(choice)) {
                        problem = "The selected " + field.getName() + " is invalid.";
                    } else {
                        stored = choice;
                    }
                }
                case "multi_select" -> {
                    if (!(value instanceof List<?> list)) {
                        problem = "The " + field.getName() + " field must be an array.";
                    } else {
                        List<String> chosen = new ArrayList<>();
                        for (Object item : list) {
                            String choice = item instanceof String || item instanceof Number
                                    ? String.valueOf(item).trim() : null;
                            if (choice == null || options == null || !options.contains(choice)) {
                                problem = "The selected " + field.getName() + " is invalid.";
                                break;
                            }
                            if (!chosen.contains(choice)) {
                                chosen.add(choice);
                            }
                        }
                        stored = CustomFieldService.write(chosen);
                    }
                }
                case "textarea" -> {
                    if (!(value instanceof String text)) {
                        problem = "The " + field.getName() + " field must be a string.";
                    } else {
                        stored = text;
                    }
                }
                default -> {
                    if (!(value instanceof String text)) {
                        problem = "The " + field.getName() + " field must be a string.";
                    } else if (text.length() > 255) {
                        problem = "The " + field.getName() + " field must not be greater than 255 characters.";
                    } else {
                        stored = text;
                    }
                }
            }
            if (problem != null) {
                errors.put(key, List.of(problem));
            } else {
                changes.byField().put(field.getId(), stored);
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return changes;
    }

    /** Stores validated changes on the ticket: a value is written, a null clears it. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void apply(Long ticketId, Changes changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, String> change : changes.byField().entrySet()) {
            CustomFieldValue existing = values.findByTicketIdAndCustomFieldId(ticketId, change.getKey()).orElse(null);
            if (change.getValue() == null) {
                if (existing != null) {
                    values.delete(existing);
                }
                continue;
            }
            if (existing == null) {
                existing = new CustomFieldValue();
                existing.setTicket(tickets.getReferenceById(ticketId));
                existing.setCustomField(fields.getReferenceById(change.getKey()));
            } else if (Objects.equals(existing.getValue(), change.getValue())) {
                continue;
            }
            existing.setValue(change.getValue());
            values.save(existing);
        }
    }

    /** A stored value as the field's type reads it. */
    public static Object decode(CustomField field, String stored) {
        if (stored == null) {
            return null;
        }
        switch (field.getFieldType()) {
            case "multi_select" -> {
                Object parsed = CustomFieldService.read(stored);
                if (parsed instanceof List<?> list) {
                    return list;
                }
                return stored.isBlank() ? List.of() : List.of(stored);
            }
            case "checkbox" -> {
                return "1".equals(stored) || "true".equalsIgnoreCase(stored);
            }
            case "number" -> {
                BigDecimal number = number(stored);
                if (number == null) {
                    return stored;
                }
                BigDecimal plain = number.stripTrailingZeros();
                if (plain.scale() <= 0) {
                    try {
                        return plain.longValueExact();
                    } catch (ArithmeticException tooBig) {
                        return plain;
                    }
                }
                return plain.doubleValue();
            }
            default -> {
                return stored;
            }
        }
    }

    private List<CustomField> ticketFields() {
        return fields.findByActiveTrueAndContextOrderBySortOrderAscIdAsc("ticket");
    }

    private static CustomField resolve(List<CustomField> active, String key) {
        for (CustomField field : active) {
            if (key.equals(field.getFieldKey())) {
                return field;
            }
        }
        if (key.matches("\\d+")) {
            for (CustomField field : active) {
                if (key.equals(String.valueOf(field.getId()))) {
                    return field;
                }
            }
        }
        return null;
    }

    private static boolean empty(Object value) {
        return value == null
                || value instanceof String text && text.isBlank()
                || value instanceof List<?> list && list.isEmpty();
    }

    private static BigDecimal number(Object value) {
        if (value instanceof Boolean) {
            return null;
        }
        try {
            BigDecimal number = new BigDecimal(String.valueOf(value).trim());
            return number;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static boolean validDate(String text) {
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeParseException invalid) {
            return false;
        }
    }

    private static Boolean checkbox(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            return d == 0 ? Boolean.FALSE : d == 1 ? Boolean.TRUE : null;
        }
        String text = String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT);
        return switch (text) {
            case "1", "true" -> Boolean.TRUE;
            case "0", "false" -> Boolean.FALSE;
            default -> null;
        };
    }
}
