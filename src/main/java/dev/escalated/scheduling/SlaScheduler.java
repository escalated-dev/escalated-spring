package dev.escalated.scheduling;

import dev.escalated.models.EscalationRule;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.EscalationRuleRepository;
import dev.escalated.services.AgentService;
import dev.escalated.services.SlaService;
import dev.escalated.services.TicketService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaScheduler.class);

    private final SlaService slaService;
    private final TicketService ticketService;
    private final EscalationRuleRepository escalationRuleRepository;
    private final AgentService agentService;

    public SlaScheduler(SlaService slaService,
                        TicketService ticketService,
                        EscalationRuleRepository escalationRuleRepository,
                        AgentService agentService) {
        this.slaService = slaService;
        this.ticketService = ticketService;
        this.escalationRuleRepository = escalationRuleRepository;
        this.agentService = agentService;
    }

    @Scheduled(fixedDelayString = "${escalated.sla.check-interval-seconds:60}000")
    public void checkSlaBreaches() {
        log.debug("Checking for SLA breaches");

        List<Ticket> breaching = slaService.findBreachingSlaTickets();
        for (Ticket ticket : breaching) {
            if (ticket.getSlaPolicy() != null) {
                List<EscalationRule> rules = escalationRuleRepository
                        .findBySlaPolicyIdAndActiveTrueOrderByMinutesBeforeOrAfterAsc(
                                ticket.getSlaPolicy().getId());
                for (EscalationRule rule : rules) {
                    applyEscalationRule(ticket, rule);
                }
            }
        }

        List<Ticket> firstResponseBreaching = slaService.findBreachingFirstResponseTickets();
        for (Ticket ticket : firstResponseBreaching) {
            log.warn("First response SLA breached for ticket {}", ticket.getTicketNumber());
        }
    }

    private void applyEscalationRule(Ticket ticket, EscalationRule rule) {
        log.info("Applying escalation rule '{}' to ticket {}", rule.getName(), ticket.getTicketNumber());

        switch (rule.getActionType()) {
            case "reassign" -> {
                if (rule.getActionTarget() != null) {
                    try {
                        Long agentId = Long.parseLong(rule.getActionTarget());
                        ticketService.assign(ticket.getId(), agentId, "system");
                    } catch (NumberFormatException ex) {
                        log.error("Invalid agent ID in escalation rule: {}", rule.getActionTarget());
                    }
                }
            }
            case "notify" -> log.info("Notification escalation for ticket {}: {}",
                    ticket.getTicketNumber(), rule.getNotifyEmails());
            case "change_priority" -> {
                try {
                    ticketService.update(ticket.getId(), null, null,
                            dev.escalated.models.TicketPriority.valueOf(rule.getActionTarget()));
                } catch (Exception ex) {
                    log.error("Failed to change priority for escalation: {}", ex.getMessage());
                }
            }
            default -> log.warn("Unknown escalation action type: {}", rule.getActionType());
        }
    }
}
