package dev.escalated.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.services.DataRetentionService;
import dev.escalated.services.SettingsService;
import dev.escalated.services.ValidationFailedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * The admin Settings sub-pages' props and saves, as escalated-laravel's
 * {@code CsatSettingsController}, {@code DataRetentionController}, {@code EmailSettingsController}
 * and {@code PublicTicketsSettingsController}. Every value lives in the settings table under
 * Laravel's keys; the database page is {@link DatabaseConnections}.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class AdminSettingsSubPages {

    public static final String DEFAULT_CSAT_QUESTION = "How would you rate your support experience?";

    private static final Set<String> CSAT_SCALES = Set.of("1-3", "1-5");
    private static final Set<String> CSAT_TRIGGERS = Set.of("on_resolve", "delayed", "manual");
    private static final Set<String> RETENTION_PERIODS = Set.of("never", "1_year", "2_years", "3_years", "5_years");
    private static final Set<String> AUDIT_RETENTION_PERIODS = Set.of("never", "90_days", "180_days", "365_days");
    private static final Set<String> DKIM_STATUSES = Set.of("verified", "pending", "failed", "unknown");
    private static final Set<String> GUEST_POLICY_MODES = Set.of("unassigned", "guest_user", "prompt_signup");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SettingsService settings;
    private final DataRetentionService retention;

    public AdminSettingsSubPages(SettingsService settings, DataRetentionService retention) {
        this.settings = settings;
        this.retention = retention;
    }

    private String setting(String key, String fallback) {
        String value = settings.getOrDefault(key, fallback);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String text(UiInput input, String key) {
        Object raw = input.raw(key);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static void fail(Map<String, List<String>> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
    }

    // ── CSAT ────────────────────────────────────────────────────────────

    public Map<String, Object> csat() {
        int delay;
        try {
            delay = Integer.parseInt(setting("csat_delay_hours", "0"));
        } catch (NumberFormatException e) {
            delay = 0;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("csat_question_text", setting("csat_question_text", DEFAULT_CSAT_QUESTION));
        values.put("csat_scale", setting("csat_scale", "1-5"));
        values.put("csat_delivery_trigger", setting("csat_delivery_trigger", "on_resolve"));
        values.put("csat_delay_hours", delay);
        return Map.of("settings", values);
    }

    public void saveCsat(UiInput input) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        String question = text(input, "csat_question_text");
        if (question == null) {
            errors.put("csat_question_text", List.of("The csat question text field is required."));
        } else if (question.length() > 500) {
            errors.put("csat_question_text", List.of("The csat question text field must not be greater than 500 characters."));
        }
        String scale = text(input, "csat_scale");
        if (scale == null || !CSAT_SCALES.contains(scale)) {
            errors.put("csat_scale", List.of("The selected csat scale is invalid."));
        }
        String trigger = text(input, "csat_delivery_trigger");
        if (trigger == null || !CSAT_TRIGGERS.contains(trigger)) {
            errors.put("csat_delivery_trigger", List.of("The selected csat delivery trigger is invalid."));
        }
        String rawDelay = text(input, "csat_delay_hours");
        Integer hours = null;
        if (rawDelay == null) {
            errors.put("csat_delay_hours", List.of("The csat delay hours field is required."));
        } else {
            try {
                hours = Integer.valueOf(rawDelay);
                if (hours < 0 || hours > 168) {
                    errors.put("csat_delay_hours", List.of("The csat delay hours field must be between 0 and 168."));
                }
            } catch (NumberFormatException e) {
                errors.put("csat_delay_hours", List.of("The csat delay hours field must be an integer."));
            }
        }
        fail(errors);
        settings.set("csat_question_text", question, "csat");
        settings.set("csat_scale", scale, "csat");
        settings.set("csat_delivery_trigger", trigger, "csat");
        settings.set("csat_delay_hours", String.valueOf(hours), "csat");
    }

    // ── Data retention ──────────────────────────────────────────────────

    public Map<String, Object> dataRetention() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("retention_closed_tickets", setting("retention_closed_tickets", "never"));
        values.put("retention_attachments", setting("retention_attachments", "never"));
        values.put("retention_audit_logs", setting("retention_audit_logs", "never"));
        String gdpr = settings.getOrDefault("retention_user_data_gdpr", "0");
        values.put("retention_user_data_gdpr", "1".equals(gdpr) || "true".equals(gdpr));

        DataRetentionService.Preview preview = retention.preview(Instant.now());
        Map<String, Object> purge = new LinkedHashMap<>();
        purge.put("tickets", preview.tickets());
        purge.put("attachments", preview.attachments());
        purge.put("audit_logs", preview.auditLogs());

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("settings", values);
        props.put("purgePreview", purge);
        return props;
    }

    public void saveDataRetention(UiInput input) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, String> save = new LinkedHashMap<>();
        Map<String, Set<String>> periods = new LinkedHashMap<>();
        periods.put("retention_closed_tickets", RETENTION_PERIODS);
        periods.put("retention_attachments", RETENTION_PERIODS);
        periods.put("retention_audit_logs", AUDIT_RETENTION_PERIODS);
        periods.forEach((key, allowed) -> {
            String value = text(input, key);
            String label = key.replace('_', ' ');
            if (value == null) {
                errors.put(key, List.of("The " + label + " field is required."));
            } else if (!allowed.contains(value)) {
                errors.put(key, List.of("The selected " + label + " is invalid."));
            } else {
                save.put(key, value);
            }
        });
        String gdpr = text(input, "retention_user_data_gdpr");
        if (gdpr == null) {
            errors.put("retention_user_data_gdpr", List.of("The retention user data gdpr field is required."));
        } else {
            switch (gdpr.toLowerCase(Locale.ROOT)) {
                case "1", "true" -> save.put("retention_user_data_gdpr", "1");
                case "0", "false" -> save.put("retention_user_data_gdpr", "0");
                default -> errors.put("retention_user_data_gdpr",
                        List.of("The retention user data gdpr field must be true or false."));
            }
        }
        fail(errors);
        save.forEach((key, value) -> settings.set(key, value, "data_retention"));
    }

    // ── Email addresses ─────────────────────────────────────────────────

    private static Map<String, Object> normalise(Map<?, ?> entry) {
        Object displayName = entry.get("display_name");
        Object department = entry.get("department_id");
        Long departmentId = null;
        if (department instanceof Number number) {
            departmentId = number.longValue();
        } else if (department instanceof String string && string.trim().matches("-?[0-9]+")) {
            departmentId = Long.valueOf(string.trim());
        }
        Object dkim = entry.get("dkim_status");
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("email", entry.get("email") instanceof String email ? email : "");
        address.put("display_name", displayName instanceof String name && !name.isEmpty() ? name : null);
        address.put("department_id", departmentId);
        address.put("dkim_status", dkim instanceof String status && !status.isEmpty() ? status : "unknown");
        return address;
    }

    private List<Map<String, Object>> addresses() {
        String raw = settings.getOrDefault("email_addresses", "");
        List<Map<String, Object>> addresses = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return addresses;
        }
        try {
            JsonNode stored = JSON.readTree(raw);
            if (stored != null && stored.isArray()) {
                for (JsonNode entry : stored) {
                    if (entry.isObject()) {
                        addresses.add(normalise(JSON.convertValue(entry, Map.class)));
                    }
                }
            }
        } catch (JsonProcessingException | RuntimeException e) {
            return new ArrayList<>();
        }
        return addresses;
    }

    public Map<String, Object> email() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("addresses", addresses());
        props.put("defaultReplyAddress", settings.getOrDefault("default_reply_address", ""));
        return props;
    }

    private static boolean isEmail(String value) {
        return value.length() <= 255 && EMAIL.matcher(value).matches();
    }

    /** As in Laravel, the addresses are stored only: outbound mail does not read them. */
    public void saveEmail(UiInput input) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        List<Map<String, Object>> addresses = new ArrayList<>();
        Object list = input.raw("addresses");
        if (list == null) {
            errors.put("addresses", List.of("The addresses field is required."));
        } else if (!(list instanceof List<?> entries)) {
            errors.put("addresses", List.of("The addresses field must be an array."));
        } else {
            for (int i = 0; i < entries.size(); i++) {
                String key = "addresses." + i + ".";
                if (!(entries.get(i) instanceof Map<?, ?> entry)) {
                    errors.put(key + "email", List.of("The email field is required."));
                    continue;
                }
                Object email = entry.get("email");
                if (!(email instanceof String address) || address.isBlank()) {
                    errors.put(key + "email", List.of("The email field is required."));
                } else if (address.length() > 255) {
                    errors.put(key + "email", List.of("The email field must not be greater than 255 characters."));
                } else if (!isEmail(address)) {
                    errors.put(key + "email", List.of("The email field must be a valid email address."));
                }
                Object displayName = entry.get("display_name");
                if (displayName != null && !(displayName instanceof String)) {
                    errors.put(key + "display_name", List.of("The display name field must be a string."));
                } else if (displayName instanceof String name && name.length() > 255) {
                    errors.put(key + "display_name",
                            List.of("The display name field must not be greater than 255 characters."));
                }
                Object department = entry.get("department_id");
                boolean integral = department == null
                        || (department instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue()))
                        || (department instanceof String string && (string.isEmpty() || string.trim().matches("-?[0-9]+")));
                if (!integral) {
                    errors.put(key + "department_id", List.of("The department id field must be an integer."));
                }
                Object dkim = entry.get("dkim_status");
                if (dkim != null && !(dkim instanceof String status && DKIM_STATUSES.contains(status))) {
                    errors.put(key + "dkim_status", List.of("The selected dkim status is invalid."));
                }
                addresses.add(normalise(entry));
            }
        }
        String reply = text(input, "default_reply_address");
        if (reply != null && reply.length() > 255) {
            errors.put("default_reply_address",
                    List.of("The default reply address field must not be greater than 255 characters."));
        } else if (reply != null && !isEmail(reply)) {
            errors.put("default_reply_address", List.of("The default reply address field must be a valid email address."));
        }
        fail(errors);
        try {
            settings.set("email_addresses", JSON.writeValueAsString(addresses), "email");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        if (reply != null) {
            settings.set("default_reply_address", reply, "email");
        }
    }

    // ── Public tickets (guest policy) ───────────────────────────────────

    public Map<String, Object> publicTickets() {
        String userId = settings.getOrDefault("guest_policy_user_id", "");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("guest_policy_mode", setting("guest_policy_mode", "unassigned"));
        values.put("guest_policy_user_id", userId == null || userId.isBlank() ? null : userId.trim());
        values.put("guest_policy_signup_url_template", settings.getOrDefault("guest_policy_signup_url_template", ""));
        return Map.of("settings", values);
    }

    /** The same keys (and group) the JSON admin API and the widget use. */
    public void savePublicTickets(UiInput input) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        String mode = text(input, "guest_policy_mode");
        if (mode == null) {
            errors.put("guest_policy_mode", List.of("The guest policy mode field is required."));
        } else if (!GUEST_POLICY_MODES.contains(mode)) {
            errors.put("guest_policy_mode", List.of("The selected guest policy mode is invalid."));
        }
        String userId = text(input, "guest_policy_user_id");
        if (userId != null && !userId.matches("[0-9]+")) {
            errors.put("guest_policy_user_id", List.of("The guest policy user id field must be an integer."));
        } else if (userId != null && Long.parseLong(userId) < 1) {
            errors.put("guest_policy_user_id", List.of("The guest policy user id field must be at least 1."));
        } else if ("guest_user".equals(mode) && userId == null) {
            errors.put("guest_policy_user_id",
                    List.of("The guest policy user id field is required when guest policy mode is guest_user."));
        }
        String template = text(input, "guest_policy_signup_url_template");
        if (template != null && template.length() > 500) {
            errors.put("guest_policy_signup_url_template",
                    List.of("The guest policy signup url template field must not be greater than 500 characters."));
        }
        fail(errors);
        settings.set("guest_policy_mode", mode, "public_tickets");
        settings.set("guest_policy_user_id", "guest_user".equals(mode) ? userId : "", "public_tickets");
        settings.set("guest_policy_signup_url_template",
                "prompt_signup".equals(mode) && template != null ? template : "", "public_tickets");
    }
}
