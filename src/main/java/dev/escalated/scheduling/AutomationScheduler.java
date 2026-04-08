package dev.escalated.scheduling;

import dev.escalated.models.Ticket;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.TicketService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);

    private final TicketRepository ticketRepository;
    private final TicketService ticketService;

    public AutomationScheduler(TicketRepository ticketRepository, TicketService ticketService) {
        this.ticketRepository = ticketRepository;
        this.ticketService = ticketService;
    }

    @Scheduled(fixedDelay = 300000)
    public void autoCloseResolvedTickets() {
        log.debug("Running auto-close automation for resolved tickets");

        Instant threshold = Instant.now().minus(7, ChronoUnit.DAYS);
        Page<Ticket> resolved = ticketRepository.findByStatus(TicketStatus.RESOLVED, PageRequest.of(0, 100));

        for (Ticket ticket : resolved.getContent()) {
            if (ticket.getResolvedAt() != null && ticket.getResolvedAt().isBefore(threshold)) {
                ticketService.changeStatus(ticket.getId(), TicketStatus.CLOSED, "system");
                log.info("Auto-closed resolved ticket {} after 7 days", ticket.getTicketNumber());
            }
        }
    }

    @Scheduled(fixedDelay = 3600000)
    public void autoAssignUnassignedTickets() {
        log.debug("Running auto-assign automation for unassigned tickets");

        var unassigned = ticketRepository.findUnassignedTickets();
        for (Ticket ticket : unassigned) {
            log.debug("Ticket {} is unassigned, could be auto-assigned", ticket.getTicketNumber());
        }
    }
}
