package dev.escalated.services;

import dev.escalated.models.BusinessSchedule;
import dev.escalated.models.Holiday;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaServiceTest {

    @Mock
    private SlaPolicyRepository slaPolicyRepository;
    @Mock
    private TicketRepository ticketRepository;

    private SlaService slaService;

    @BeforeEach
    void setUp() {
        slaService = new SlaService(slaPolicyRepository, ticketRepository);
    }

    @Test
    void applySlaPolicy_shouldSetDueDatesWithoutSchedule() {
        SlaPolicy policy = new SlaPolicy();
        policy.setId(1L);
        policy.setPriority(TicketPriority.HIGH);
        policy.setFirstResponseMinutes(60);
        policy.setResolutionMinutes(480);

        when(slaPolicyRepository.findByPriorityAndActiveTrue(TicketPriority.HIGH))
                .thenReturn(Optional.of(policy));

        Ticket ticket = new Ticket();
        ticket.setPriority(TicketPriority.HIGH);

        slaService.applySlaPolicy(ticket);

        assertNotNull(ticket.getSlaFirstResponseDueAt());
        assertNotNull(ticket.getSlaDueAt());
        assertTrue(ticket.getSlaDueAt().isAfter(ticket.getSlaFirstResponseDueAt()));
    }

    @Test
    void applySlaPolicy_shouldNotSetDatesWhenNoPolicyMatches() {
        when(slaPolicyRepository.findByPriorityAndActiveTrue(any()))
                .thenReturn(Optional.empty());

        Ticket ticket = new Ticket();
        ticket.setPriority(TicketPriority.LOW);

        slaService.applySlaPolicy(ticket);

        assertNull(ticket.getSlaDueAt());
    }

    @Test
    void applySlaPolicy_shouldUseBusinessSchedule() {
        BusinessSchedule schedule = new BusinessSchedule();
        schedule.setTimezone("UTC");
        schedule.setHolidays(new ArrayList<>());

        SlaPolicy policy = new SlaPolicy();
        policy.setId(1L);
        policy.setPriority(TicketPriority.MEDIUM);
        policy.setFirstResponseMinutes(120);
        policy.setResolutionMinutes(480);
        policy.setBusinessSchedule(schedule);

        when(slaPolicyRepository.findByPriorityAndActiveTrue(TicketPriority.MEDIUM))
                .thenReturn(Optional.of(policy));

        Ticket ticket = new Ticket();
        ticket.setPriority(TicketPriority.MEDIUM);

        slaService.applySlaPolicy(ticket);

        assertNotNull(ticket.getSlaFirstResponseDueAt());
        assertNotNull(ticket.getSlaDueAt());
    }

    @Test
    void addBusinessMinutes_shouldSkipWeekends() {
        BusinessSchedule schedule = new BusinessSchedule();
        schedule.setTimezone("UTC");
        schedule.setSaturdayStart(null);
        schedule.setSaturdayEnd(null);
        schedule.setSundayStart(null);
        schedule.setSundayEnd(null);
        schedule.setHolidays(new ArrayList<>());

        // Start on Friday 16:00 with 120 minutes -> should end Monday
        ZonedDateTime friday = ZonedDateTime.of(2024, 1, 5, 16, 0, 0, 0, ZoneId.of("UTC"));
        Instant result = slaService.addBusinessMinutes(friday.toInstant(), 120, schedule);

        ZonedDateTime resultZdt = result.atZone(ZoneId.of("UTC"));
        // Should be Monday because Saturday and Sunday are skipped
        assertTrue(resultZdt.getDayOfWeek().getValue() == 1); // Monday
    }

    @Test
    void addBusinessMinutes_shouldSkipHolidays() {
        BusinessSchedule schedule = new BusinessSchedule();
        schedule.setTimezone("UTC");
        schedule.setHolidays(new ArrayList<>());

        Holiday holiday = new Holiday();
        holiday.setDate(LocalDate.of(2024, 1, 8)); // Monday
        holiday.setRecurring(false);
        schedule.getHolidays().add(holiday);

        // Start on Friday 16:00 with 120 minutes -> skip sat, sun, mon holiday -> Tuesday
        ZonedDateTime friday = ZonedDateTime.of(2024, 1, 5, 16, 0, 0, 0, ZoneId.of("UTC"));
        Instant result = slaService.addBusinessMinutes(friday.toInstant(), 120, schedule);

        ZonedDateTime resultZdt = result.atZone(ZoneId.of("UTC"));
        assertTrue(resultZdt.getDayOfWeek().getValue() == 2); // Tuesday
    }
}
