package dev.escalated.scheduling;

import dev.escalated.services.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SnoozeScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnoozeScheduler.class);

    private final TicketService ticketService;

    public SnoozeScheduler(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Scheduled(fixedDelayString = "${escalated.snooze.check-interval-seconds:60}000")
    public void wakeUpSnoozedTickets() {
        log.debug("Checking for snoozed tickets to wake up");
        ticketService.wakeUpSnoozedTickets();
    }
}
