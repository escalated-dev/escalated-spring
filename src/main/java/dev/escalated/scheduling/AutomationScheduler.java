package dev.escalated.scheduling;

import dev.escalated.models.Ticket;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.AutomationRunner;
import dev.escalated.services.TicketService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The time-based sweeps: admin-defined automations
 * ({@link AutomationRunner}) and the built-in auto-close of resolved tickets
 * ({@code escalated.automations.auto-close-days}, 7 by default, 0 = off).
 */
@Component
public class AutomationScheduler {
    @org.springframework.beans.factory.annotation.Autowired
    private dev.escalated.config.EscalatedProperties properties;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AutomationRunner automationRunner;

    public boolean matches(String task, Ticket ticket) {
        String conditions =
                properties == null ? null : properties.getAutomationConditions().get(task);
        return dev.escalated.services.TicketRoutingConditions.matches(conditions, ticket);
    }

    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);
    private static final int BATCH = 100;

    private final TicketRepository ticketRepository;
    private final TicketService ticketService;

    public AutomationScheduler(TicketRepository ticketRepository, TicketService ticketService) {
        this.ticketRepository = ticketRepository;
        this.ticketService = ticketService;
    }

    @Scheduled(fixedDelayString = "${escalated.automations.check-interval-seconds:60}000")
    public void runAutomations() {
        if (automationRunner != null) {
            int affected = automationRunner.run();
            if (affected > 0) {
                log.info("Automations acted on {} ticket(s)", affected);
            }
        }
    }

    @Scheduled(fixedDelay = 300000)
    public void autoCloseResolvedTickets() {
        int days = properties == null ? 7 : properties.getAutomations().getAutoCloseDays();
        if (days <= 0) {
            return;
        }
        log.debug("Running auto-close automation for resolved tickets");

        Instant threshold = Instant.now().minus(days, ChronoUnit.DAYS);
        // Oldest first, and page past the tickets a condition keeps open, so
        // every eligible ticket is reached however many are resolved.
        int page = 0;
        while (true) {
            List<Ticket> resolved = ticketRepository.findResolvedBefore(threshold, PageRequest.of(page, BATCH));
            int closed = 0;
            for (Ticket ticket : resolved) {
                if (!matches("auto-close", ticket)) {
                    continue;
                }
                ticketService.changeStatus(ticket.getId(), TicketStatus.CLOSED, "system");
                closed++;
                log.info("Auto-closed resolved ticket {} after {} days", ticket.getTicketNumber(), days);
            }
            if (resolved.size() < BATCH) {
                return;
            }
            // Closed tickets drop out of the query; skip only the ones kept.
            if (closed == 0) {
                page++;
            }
        }
    }

    @Scheduled(fixedDelay = 3600000)
    public void autoAssignUnassignedTickets() {
        log.debug("Running auto-assign automation for unassigned tickets");

        var unassigned = ticketRepository.findUnassignedTickets();
        for (Ticket ticket : unassigned) {
            if (!matches("auto-assign", ticket)) {
                continue;
            }
            log.debug("Ticket {} is unassigned, could be auto-assigned", ticket.getTicketNumber());
        }
    }
}
