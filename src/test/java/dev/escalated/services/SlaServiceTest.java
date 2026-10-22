package dev.escalated.services;

import dev.escalated.models.BusinessSchedule;
import dev.escalated.models.Holiday;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.BusinessScheduleRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        SlaPolicy policy = policy(1L, "high", 1, 8);
        when(slaPolicyRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(policy));

        Ticket ticket = new Ticket();
        ticket.setPriority(TicketPriority.HIGH);
        Instant before = Instant.now();

        slaService.applySlaPolicy(ticket);

        assertEquals(policy, ticket.getSlaPolicy());
        assertNotNull(ticket.getSlaFirstResponseDueAt());
        assertTrue(ticket.getSlaFirstResponseDueAt().isAfter(before.plusSeconds(3599)));
        assertTrue(ticket.getSlaDueAt().isAfter(before.plusSeconds(8 * 3600 - 1)));
        assertTrue(ticket.getSlaDueAt().isAfter(ticket.getSlaFirstResponseDueAt()));
    }

    @Test
    void applySlaPolicy_takesTheFirstDefaultPolicyWithATargetForThePriority() {
        SlaPolicy lowOnly = policy(1L, "low", 4, 24);
        SlaPolicy notDefault = policy(2L, "high", 2, 6);
        notDefault.setDefaultPolicy(false);
        SlaPolicy high = policy(3L, "high", 1, 8);
        SlaPolicy laterHigh = policy(4L, "high", 9, 9);
        when(slaPolicyRepository.findByActiveTrueOrderByIdAsc())
                .thenReturn(List.of(lowOnly, notDefault, high, laterHigh));

        Ticket ticket = new Ticket();
        ticket.setPriority(TicketPriority.HIGH);
        slaService.applySlaPolicy(ticket);

        assertEquals(high, ticket.getSlaPolicy());
    }

    @Test
    void applySlaPolicy_clearsADeadlineThePolicyHasNoFigureFor() {
        SlaPolicy policy = policy(1L, "high", 1, 8);
        policy.setResolutionHours(Map.of());
        when(slaPolicyRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(policy));

        Ticket ticket = new Ticket();
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setSlaDueAt(Instant.now());
        slaService.applySlaPolicy(ticket);

        assertNotNull(ticket.getSlaFirstResponseDueAt());
        assertNull(ticket.getSlaDueAt());
    }

    @Test
    void applySlaPolicy_shouldNotSetDatesWhenNoPolicyMatches() {
        when(slaPolicyRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(policy(1L, "high", 1, 8)));

        Ticket ticket = new Ticket();
        ticket.setPriority(TicketPriority.LOW);

        slaService.applySlaPolicy(ticket);

        assertNull(ticket.getSlaPolicy());
        assertNull(ticket.getSlaDueAt());
    }

    @Test
    void applySlaPolicy_shouldUseBusinessSchedule() {
        BusinessSchedule schedule = new BusinessSchedule();
        schedule.setTimezone("UTC");
        schedule.setHolidays(new ArrayList<>());

        SlaPolicy policy = policy(1L, "medium", 2, 8);
        policy.setBusinessHoursOnly(true);
        policy.setBusinessSchedule(schedule);

        when(slaPolicyRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(policy));

        Ticket ticket = new Ticket();
        ticket.setPriority(TicketPriority.MEDIUM);

        slaService.applySlaPolicy(ticket);

        assertNotNull(ticket.getSlaFirstResponseDueAt());
        assertNotNull(ticket.getSlaDueAt());
    }

    @Test
    void aBusinessHoursPolicyWithoutAScheduleFollowsTheDefaultSchedule() {
        BusinessSchedule allDay = new BusinessSchedule();
        allDay.setTimezone("UTC");
        allDay.setDefaultSchedule(true);
        for (String day : new String[] {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"}) {
            ReflectionTestUtils.setField(allDay, Character.toLowerCase(day.charAt(0)) + day.substring(1) + "Start", "00:00");
            ReflectionTestUtils.setField(allDay, Character.toLowerCase(day.charAt(0)) + day.substring(1) + "End", "24:00");
        }
        BusinessScheduleRepository schedules = org.mockito.Mockito.mock(BusinessScheduleRepository.class);
        when(schedules.findFirstByDefaultScheduleTrueAndActiveTrueOrderByIdAsc()).thenReturn(Optional.of(allDay));
        ReflectionTestUtils.setField(slaService, "businessSchedules", schedules);

        SlaPolicy policy = policy(1L, "high", 1, 2);
        policy.setBusinessHoursOnly(true);
        when(slaPolicyRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(policy));
        Ticket ticket = new Ticket();
        ticket.setPriority(TicketPriority.HIGH);
        Instant before = Instant.now();

        slaService.applySlaPolicy(ticket);

        // Open around the clock: an hour of business time is an hour.
        long minutes = java.time.Duration.between(before, ticket.getSlaFirstResponseDueAt()).toMinutes();
        assertTrue(minutes >= 59 && minutes <= 61, "first response due in " + minutes + " minutes");
    }

    @Test
    void addBusinessMinutes_readsTwentyFourHundredAsTheEndOfTheDay() {
        BusinessSchedule schedule = new BusinessSchedule();
        schedule.setTimezone("UTC");
        schedule.setFridayEnd("24:00");
        schedule.setHolidays(new ArrayList<>());

        ZonedDateTime friday = ZonedDateTime.of(2024, 1, 5, 16, 0, 0, 0, ZoneId.of("UTC"));
        Instant result = slaService.addBusinessMinutes(friday.toInstant(), 420, schedule);

        assertEquals(friday.withHour(23).toInstant(), result);
        assertEquals(friday.plusDays(3).withHour(9).plusMinutes(60).toInstant(),
                slaService.addBusinessMinutes(friday.toInstant(), 540, schedule));
    }

    private static SlaPolicy policy(Long id, String priority, double firstResponse, double resolution) {
        SlaPolicy policy = new SlaPolicy();
        policy.setId(id);
        policy.setDefaultPolicy(true);
        policy.setFirstResponseHours(Map.of(priority, firstResponse));
        policy.setResolutionHours(Map.of(priority, resolution));
        return policy;
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
