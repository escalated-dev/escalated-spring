package dev.escalated.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.Automation;
import dev.escalated.models.RuleExecution;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AutomationRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the admin-defined automations against open tickets. Mirrors Laravel's
 * {@code AutomationRunner}.
 *
 * <p>An automation acts on a ticket once; it acts again only after the ticket
 * has stopped matching, or after the automation's {@code repeat_after_hours}
 * ({@link RuleFireLog}). A failure on one ticket is logged and the run carries
 * on with the next.
 *
 * <p>Conditions are a list, all of which must hold:
 * {@code hours_since_created}, {@code hours_since_updated} and
 * {@code hours_since_assigned} take an operator ({@code >}, {@code >=},
 * {@code <}, {@code <=}, {@code =}) and a number of hours; {@code status},
 * {@code priority}, {@code ticket_type}, {@code department_id},
 * {@code account_type} and {@code account_id} compare equal;
 * {@code assigned} is {@code assigned} or {@code unassigned};
 * {@code subject_contains} is a substring. Actions: {@code change_status},
 * {@code change_priority}, {@code assign}, {@code add_tag},
 * {@code set_ticket_type}, {@code add_note}.
 */
@Service
public class AutomationRunner {

    public static final List<String> CONDITION_FIELDS = List.of(
            "hours_since_created", "hours_since_updated", "hours_since_assigned", "status", "priority",
            "assigned", "ticket_type", "department_id", "account_type", "account_id", "subject_contains");
    public static final List<String> TIME_FIELDS =
            List.of("hours_since_created", "hours_since_updated", "hours_since_assigned");
    public static final List<String> OPERATORS = List.of(">", ">=", "<", "<=", "=");
    public static final List<String> ACTION_TYPES = List.of(
            "change_status", "change_priority", "assign", "add_tag", "set_ticket_type", "add_note");

    private static final Logger log = LoggerFactory.getLogger(AutomationRunner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AutomationRepository automations;
    private final TicketRepository tickets;
    private final TicketService ticketService;
    private final TagRepository tags;
    private final RuleFireLog fireLog;

    public AutomationRunner(AutomationRepository automations, TicketRepository tickets,
                            TicketService ticketService, TagRepository tags, RuleFireLog fireLog) {
        this.automations = automations;
        this.tickets = tickets;
        this.ticketService = ticketService;
        this.tags = tags;
        this.fireLog = fireLog;
    }

    /** Evaluates every active automation. Returns the number of automation/ticket pairs acted on. */
    public synchronized int run() {
        int affected = 0;
        for (Automation automation : automations.findByActiveTrueOrderByPositionAscIdAsc()) {
            List<Ticket> due;
            try {
                List<Map<String, Object>> conditions = list(automation.getConditions());
                Instant now = Instant.now();
                List<Ticket> matching = tickets.findOpenTickets().stream()
                        .filter(ticket -> matches(conditions, ticket, now))
                        .toList();
                due = fireLog.due(RuleExecution.AUTOMATION, automation.getId(),
                        automation.getRepeatAfterHours(), matching);
            } catch (RuntimeException ex) {
                log.error("Automation {} could not be evaluated: {}", automation.getId(), ex.getMessage());
                continue;
            }

            List<Map<String, Object>> actions = list(automation.getActions());
            for (Ticket ticket : due) {
                try {
                    execute(automation, actions, ticket);
                    fireLog.record(RuleExecution.AUTOMATION, automation.getId(), ticket.getId());
                    affected++;
                } catch (RuntimeException ex) {
                    log.error("Automation {} failed for ticket {}: {}",
                            automation.getId(), ticket.getId(), ex.getMessage());
                }
            }

            automations.findById(automation.getId()).ifPresent(fresh -> {
                fresh.setLastRunAt(Instant.now());
                automations.save(fresh);
            });
        }
        return affected;
    }

    static boolean matches(List<Map<String, Object>> conditions, Ticket ticket, Instant now) {
        if (conditions.isEmpty()) {
            // An automation with no conditions would act on every open ticket;
            // the admin API refuses to save one, and a stored one does nothing.
            return false;
        }
        for (Map<String, Object> condition : conditions) {
            if (!matchesOne(condition, ticket, now)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesOne(Map<String, Object> condition, Ticket ticket, Instant now) {
        String field = text(condition.get("field"));
        String operator = condition.get("operator") == null ? ">" : text(condition.get("operator"));
        String value = text(condition.get("value"));
        switch (field) {
            case "hours_since_created":
                return compareHours(ticket.getCreatedAt(), now, operator, value);
            case "hours_since_updated":
                return compareHours(ticket.getUpdatedAt(), now, operator, value);
            case "hours_since_assigned":
                // As in Laravel: approximated by the last update of an assigned ticket.
                return ticket.getAssignedAgent() != null
                        && compareHours(ticket.getUpdatedAt(), now, operator, value);
            case "status":
                return ticket.getStatus() != null && ticket.getStatus().name().equalsIgnoreCase(value);
            case "priority":
                return ticket.getPriority() != null && ticket.getPriority().name().equalsIgnoreCase(value);
            case "assigned":
                if ("unassigned".equals(value)) {
                    return ticket.getAssignedAgent() == null;
                }
                if ("assigned".equals(value)) {
                    return ticket.getAssignedAgent() != null;
                }
                return true;
            case "ticket_type":
                return Objects.equals(ticket.getTicketType(), value);
            case "department_id":
                return ticket.getDepartment() != null
                        && String.valueOf(ticket.getDepartment().getId()).equals(value);
            case "account_type":
                return Objects.equals(ticket.getAccountType(), value);
            case "account_id":
                return Objects.equals(ticket.getAccountId(), value);
            case "subject_contains":
                return ticket.getSubject() != null
                        && ticket.getSubject().toLowerCase().contains(value.toLowerCase());
            default:
                // Unknown fields are ignored, as in Laravel.
                return true;
        }
    }

    /**
     * Compares the hours elapsed since {@code since} with {@code value}:
     * {@code > 24} means more than 24 hours ago.
     */
    private static boolean compareHours(Instant since, Instant now, String operator, String value) {
        if (since == null) {
            return false;
        }
        double hours;
        try {
            hours = Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return false;
        }
        double elapsed = Duration.between(since, now).toMillis() / 3_600_000.0;
        return switch (operator) {
            case ">=" -> elapsed >= hours;
            case "<" -> elapsed < hours;
            case "<=" -> elapsed <= hours;
            case "=" -> Math.floor(elapsed) == Math.floor(hours);
            default -> elapsed > hours;
        };
    }

    private void execute(Automation automation, List<Map<String, Object>> actions, Ticket ticket) {
        String actor = "automation:" + automation.getId();
        for (Map<String, Object> action : actions) {
            String type = text(action.get("type"));
            String value = text(action.get("value"));
            try {
                switch (type) {
                    case "change_status" ->
                            ticketService.changeStatus(ticket.getId(), TicketStatus.valueOf(value.toUpperCase()), actor);
                    case "change_priority" ->
                            ticketService.update(ticket.getId(), null, null, TicketPriority.valueOf(value.toUpperCase()));
                    case "assign" -> ticketService.assign(ticket.getId(), Long.valueOf(value), actor);
                    case "add_tag" -> {
                        // Only an existing tag, as in Laravel.
                        if (tags.findByName(value).isPresent()) {
                            ticketService.addTag(ticket.getId(), value);
                        }
                    }
                    case "set_ticket_type" -> {
                        Ticket fresh = ticketService.findById(ticket.getId());
                        fresh.setTicketType(value);
                        tickets.save(fresh);
                    }
                    case "add_note" -> {
                        if (!value.isBlank()) {
                            ticketService.addReply(ticket.getId(), value, "Automation: " + automation.getName(),
                                    "system", "system", true);
                        }
                    }
                    default -> log.warn("Automation {}: unknown action type {}", automation.getId(), type);
                }
            } catch (RuntimeException ex) {
                log.warn("Automation {} action {} failed on ticket {}: {}",
                        automation.getId(), type, ticket.getId(), ex.getMessage());
            }
        }
    }

    static List<Map<String, Object>> list(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
