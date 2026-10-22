package dev.escalated.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * An SLA policy as escalated-laravel's: first-response and resolution targets in hours per
 * priority ({@code {"high": 4, "low": 48}}), applied to new tickets when it is the default
 * policy (or, with targeted SLAs, the most specific match by department and ticket type).
 */
@Entity
@Table(name = "escalated_sla_policies")
public class SlaPolicy extends BaseEntity {

    private static final ObjectMapper JSON = new ObjectMapper();

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPolicy;

    @Column(name = "first_response_hours", columnDefinition = "TEXT", nullable = false)
    private String firstResponseHours = "{}";

    @Column(name = "resolution_hours", columnDefinition = "TEXT", nullable = false)
    private String resolutionHours = "{}";

    @Column(name = "business_hours_only", nullable = false)
    private boolean businessHoursOnly;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** The schedule business hours follow; null uses the first active schedule. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_schedule_id")
    private BusinessSchedule businessSchedule;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "ticket_type", length = 100)
    private String ticketType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(boolean defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    /** Hours per priority (lower-case names), as stored. */
    public Map<String, Double> getFirstResponseHours() {
        return read(firstResponseHours);
    }

    public void setFirstResponseHours(Map<String, ? extends Number> hours) {
        firstResponseHours = write(hours);
    }

    public Map<String, Double> getResolutionHours() {
        return read(resolutionHours);
    }

    public void setResolutionHours(Map<String, ? extends Number> hours) {
        resolutionHours = write(hours);
    }

    /** The first-response target for a priority, or null when this policy has none. */
    public Double firstResponseHoursFor(TicketPriority priority) {
        return priority == null ? null : getFirstResponseHours().get(priority.name().toLowerCase(Locale.ROOT));
    }

    public Double resolutionHoursFor(TicketPriority priority) {
        return priority == null ? null : getResolutionHours().get(priority.name().toLowerCase(Locale.ROOT));
    }

    /** Whether this policy has any target for the priority. */
    public boolean covers(TicketPriority priority) {
        return positive(firstResponseHoursFor(priority)) || positive(resolutionHoursFor(priority));
    }

    public boolean isBusinessHoursOnly() {
        return businessHoursOnly;
    }

    public void setBusinessHoursOnly(boolean businessHoursOnly) {
        this.businessHoursOnly = businessHoursOnly;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public BusinessSchedule getBusinessSchedule() {
        return businessSchedule;
    }

    public void setBusinessSchedule(BusinessSchedule businessSchedule) {
        this.businessSchedule = businessSchedule;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long value) {
        departmentId = value;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String value) {
        ticketType = value;
    }

    private static boolean positive(Double hours) {
        return hours != null && hours > 0;
    }

    private static Map<String, Double> read(String json) {
        Map<String, Double> hours = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return hours;
        }
        try {
            Map<String, Object> stored = JSON.readValue(json, new TypeReference<>() {});
            stored.forEach((priority, value) -> {
                Double number = value instanceof Number n ? Double.valueOf(n.doubleValue()) : parse(value);
                if (number != null) {
                    hours.put(priority.toLowerCase(Locale.ROOT), number);
                }
            });
        } catch (JsonProcessingException malformed) {
            // Unreadable targets are no targets.
        }
        return hours;
    }

    private static Double parse(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static String write(Map<String, ? extends Number> hours) {
        Map<String, Number> stored = new LinkedHashMap<>();
        if (hours != null) {
            hours.forEach((priority, value) -> {
                if (priority != null && value != null) {
                    stored.put(priority.toLowerCase(Locale.ROOT), value);
                }
            });
        }
        try {
            return JSON.writeValueAsString(stored);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
