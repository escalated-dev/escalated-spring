package dev.escalated.scheduling;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.events.TicketEvent;
import dev.escalated.models.EscalationRule;
import dev.escalated.models.RuleExecution;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.EscalationRuleRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.EscalationActions;
import dev.escalated.services.EscalationConditions;
import dev.escalated.services.RuleFireLog;
import dev.escalated.services.SlaService;
import dev.escalated.services.TicketService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The SLA sweep: escalation rules, {@code sla.breached} and
 * {@code sla.warning}.
 *
 * <p>Each acts once per ticket, recorded in {@link RuleFireLog}: an escalation
 * rule acts again only after the ticket has stopped breaching or after the
 * rule's {@code repeatAfterHours}; a breach or warning is announced once per
 * deadline. Before this the sweep re-applied every rule, and wrote an activity
 * row and fired webhooks for it, every minute for as long as the ticket
 * breached.
 */
@Component
public class SlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaScheduler.class);

    private final SlaService slaService;
    private final TicketService ticketService;
    private final EscalationRuleRepository escalationRuleRepository;
    private final TicketRepository ticketRepository;
    private final RuleFireLog fireLog;
    private final ApplicationEventPublisher events;
    private final EscalatedProperties properties;

    public SlaScheduler(SlaService slaService,
                        TicketService ticketService,
                        EscalationRuleRepository escalationRuleRepository,
                        TicketRepository ticketRepository,
                        RuleFireLog fireLog,
                        ApplicationEventPublisher events,
                        EscalatedProperties properties) {
        this.slaService = slaService;
        this.ticketService = ticketService;
        this.escalationRuleRepository = escalationRuleRepository;
        this.ticketRepository = ticketRepository;
        this.fireLog = fireLog;
        this.events = events;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${escalated.sla.check-interval-seconds:60}000")
    public synchronized void checkSlaBreaches() {
        log.debug("Checking for SLA breaches");

        List<Ticket> breaching = slaService.findBreachingSlaTickets();
        List<Ticket> firstResponseBreaching = slaService.findBreachingFirstResponseTickets();

        announce(RuleExecution.SLA_BREACH, RuleExecution.FIRST_RESPONSE, firstResponseBreaching,
                TicketEvent.Type.SLA_BREACHED, "first_response");
        announce(RuleExecution.SLA_BREACH, RuleExecution.RESOLUTION, breaching,
                TicketEvent.Type.SLA_BREACHED, "resolution");

        int warningMinutes = properties == null ? 30 : properties.getSla().getWarningMinutes();
        if (warningMinutes > 0) {
            Instant now = Instant.now();
            Instant threshold = now.plus(Duration.ofMinutes(warningMinutes));
            announce(RuleExecution.SLA_WARNING, RuleExecution.FIRST_RESPONSE,
                    ticketRepository.findTicketsApproachingFirstResponse(now, threshold),
                    TicketEvent.Type.SLA_WARNING, "first_response");
            announce(RuleExecution.SLA_WARNING, RuleExecution.RESOLUTION,
                    ticketRepository.findTicketsApproachingSla(now, threshold),
                    TicketEvent.Type.SLA_WARNING, "resolution");
        }

        List<EscalationRule> rules = escalationRuleRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        if (rules.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<Ticket> open = ticketRepository.findOpenTickets();
        for (EscalationRule rule : rules) {
            List<Ticket> due;
            try {
                List<Ticket> matching = open.stream()
                        .filter(ticket -> EscalationConditions.matches(rule.getConditions(), ticket, now))
                        .toList();
                due = fireLog.due(RuleExecution.ESCALATION_RULE, rule.getId(), rule.getRepeatAfterHours(), matching);
            } catch (RuntimeException ex) {
                log.error("Escalation rule {} could not be evaluated: {}", rule.getId(), ex.getMessage());
                continue;
            }
            for (Ticket ticket : due) {
                try {
                    applyEscalationRule(ticket, rule);
                    fireLog.record(RuleExecution.ESCALATION_RULE, rule.getId(), ticket.getId());
                    events.publishEvent(new TicketEvent(this, ticket, TicketEvent.Type.ESCALATED, "system",
                            null, "Escalation rule: " + rule.getName()));
                } catch (RuntimeException ex) {
                    log.error("Escalation rule {} failed for ticket {}: {}",
                            rule.getId(), ticket.getId(), ex.getMessage());
                }
            }
        }
    }

    /** Announces each due ticket once, and records it. */
    private void announce(String ruleType, long deadline, List<Ticket> matching,
                          TicketEvent.Type type, String slaType) {
        for (Ticket ticket : fireLog.due(ruleType, deadline, null, matching)) {
            fireLog.record(ruleType, deadline, ticket.getId());
            if (type == TicketEvent.Type.SLA_BREACHED) {
                log.warn("{} SLA breached for ticket {}", slaType, ticket.getTicketNumber());
            }
            events.publishEvent(new TicketEvent(this, ticket, type, "system", null, slaType));
        }
    }

    /**
     * Performs the rule's actions in order, as escalated-laravel's {@code EscalationService}; a
     * failing action is logged and the next one still runs. {@code escalate} changes nothing here
     * (this port has no escalated status): every firing is announced as {@code ticket.escalated}.
     */
    private void applyEscalationRule(Ticket ticket, EscalationRule rule) {
        log.info("Applying escalation rule '{}' to ticket {}", rule.getName(), ticket.getTicketNumber());
        for (Map<String, Object> action : EscalationActions.read(rule.getActions())) {
            String type = String.valueOf(action.getOrDefault("type", ""));
            Object raw = action.get("value");
            String value = raw == null ? "" : String.valueOf(raw).trim();
            try {
                switch (type) {
                    case "escalate" -> { }
                    case "change_priority" -> ticketService.update(ticket.getId(), null, null,
                            TicketPriority.valueOf(value.toUpperCase(Locale.ROOT)));
                    case "assign_to" -> ticketService.assign(ticket.getId(), Long.parseLong(value), "system");
                    case "change_department" ->
                            ticketService.changeDepartment(ticket.getId(), Long.valueOf(value), "system");
                    case "notify" -> log.info("Notification escalation for ticket {}: {}",
                            ticket.getTicketNumber(), value);
                    default -> log.warn("Unknown escalation action type: {}", type);
                }
            } catch (RuntimeException ex) {
                log.warn("Escalation rule {} action {} failed for ticket {}: {}",
                        rule.getId(), type, ticket.getId(), ex.getMessage());
            }
        }
    }
}
