package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.BusinessSchedule;
import dev.escalated.models.Holiday;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class SlaService {
    @org.springframework.beans.factory.annotation.Autowired
    private dev.escalated.config.EscalatedProperties properties;

    private static final Logger log = LoggerFactory.getLogger(SlaService.class);

    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketRepository ticketRepository;

    public SlaService(SlaPolicyRepository slaPolicyRepository, TicketRepository ticketRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void applySlaPolicy(Ticket ticket) {
        dev.escalated.security.TicketAccess.assertVisible(ticket);
        boolean targeted = properties != null && properties.isTargetedSlas();
        java.util.Optional<SlaPolicy> selected;
        if (targeted) {
            Long departmentId =
                    ticket.getDepartment() == null ? null : ticket.getDepartment().getId();
            selected =
                    slaPolicyRepository.findAll().stream()
                            .filter(p -> p.isActive() && p.getPriority() == ticket.getPriority())
                            .filter(
                                    p ->
                                            p.getDepartmentId() == null
                                                    || p.getDepartmentId().equals(departmentId))
                            .filter(
                                    p ->
                                            p.getTicketType() == null
                                                    || p.getTicketType()
                                                            .equals(ticket.getTicketType()))
                            .sorted(
                                    java.util.Comparator.<SlaPolicy>comparingInt(
                                                    p ->
                                                            (p.getDepartmentId() == null ? 0 : 2)
                                                                    + (p.getTicketType() == null
                                                                            ? 0
                                                                            : 1))
                                            .reversed()
                                            .thenComparing(SlaPolicy::getId))
                            .findFirst();
        } else {
            selected =
                    slaPolicyRepository
                            .findByPriorityAndActiveTrue(ticket.getPriority())
                            .filter(p -> p.getDepartmentId() == null && p.getTicketType() == null);
        }
        selected.ifPresent(
                policy -> {
                    ticket.setSlaPolicy(policy);

                    BusinessSchedule schedule = policy.getBusinessSchedule();
                    Instant now =
                            targeted && ticket.getCreatedAt() != null
                                    ? ticket.getCreatedAt()
                                    : Instant.now();

                    if (schedule != null) {
                        ticket.setSlaFirstResponseDueAt(
                                addBusinessMinutes(
                                        now, policy.getFirstResponseMinutes(), schedule));
                        ticket.setSlaDueAt(
                                addBusinessMinutes(now, policy.getResolutionMinutes(), schedule));
                    } else {
                        ticket.setSlaFirstResponseDueAt(
                                now.plusSeconds((long) policy.getFirstResponseMinutes() * 60));
                        ticket.setSlaDueAt(
                                now.plusSeconds((long) policy.getResolutionMinutes() * 60));
                    }
                });
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Ticket> findBreachingSlaTickets() {
        return ticketRepository.findTicketsBreachingSla(Instant.now());
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Ticket> findBreachingFirstResponseTickets() {
        return ticketRepository.findTicketsBreachingFirstResponse(Instant.now());
    }

    public Instant addBusinessMinutes(Instant start, int minutes, BusinessSchedule schedule) {
        ZoneId zoneId = ZoneId.of(schedule.getTimezone());
        ZonedDateTime current = start.atZone(zoneId);
        int remainingMinutes = minutes;

        int maxIterations = minutes + 1440;
        int iterations = 0;

        while (remainingMinutes > 0 && iterations < maxIterations) {
            iterations++;

            if (isHoliday(current.toLocalDate(), schedule)) {
                current = current.plusDays(1).with(LocalTime.of(0, 0));
                continue;
            }

            String[] hours = getBusinessHours(current.getDayOfWeek(), schedule);
            if (hours[0] == null || hours[1] == null) {
                current = current.plusDays(1).with(LocalTime.of(0, 0));
                continue;
            }

            LocalTime startTime = LocalTime.parse(hours[0]);
            LocalTime endTime = LocalTime.parse(hours[1]);

            if (current.toLocalTime().isBefore(startTime)) {
                current = current.with(startTime);
            }

            if (current.toLocalTime().isAfter(endTime) || current.toLocalTime().equals(endTime)) {
                current = current.plusDays(1).with(LocalTime.of(0, 0));
                continue;
            }

            int availableMinutes = (int) java.time.Duration.between(current.toLocalTime(), endTime).toMinutes();
            if (remainingMinutes <= availableMinutes) {
                current = current.plusMinutes(remainingMinutes);
                remainingMinutes = 0;
            } else {
                remainingMinutes -= availableMinutes;
                current = current.plusDays(1).with(LocalTime.of(0, 0));
            }
        }

        return current.toInstant();
    }

    private boolean isHoliday(LocalDate date, BusinessSchedule schedule) {
        if (schedule.getHolidays() == null) {
            return false;
        }
        for (Holiday holiday : schedule.getHolidays()) {
            if (holiday.isRecurring()) {
                if (holiday.getDate().getMonth() == date.getMonth()
                        && holiday.getDate().getDayOfMonth() == date.getDayOfMonth()) {
                    return true;
                }
            } else if (holiday.getDate().equals(date)) {
                return true;
            }
        }
        return false;
    }

    private String[] getBusinessHours(DayOfWeek day, BusinessSchedule schedule) {
        return switch (day) {
            case MONDAY -> new String[]{schedule.getMondayStart(), schedule.getMondayEnd()};
            case TUESDAY -> new String[]{schedule.getTuesdayStart(), schedule.getTuesdayEnd()};
            case WEDNESDAY -> new String[]{schedule.getWednesdayStart(), schedule.getWednesdayEnd()};
            case THURSDAY -> new String[]{schedule.getThursdayStart(), schedule.getThursdayEnd()};
            case FRIDAY -> new String[]{schedule.getFridayStart(), schedule.getFridayEnd()};
            case SATURDAY -> new String[]{schedule.getSaturdayStart(), schedule.getSaturdayEnd()};
            case SUNDAY -> new String[]{schedule.getSundayStart(), schedule.getSundayEnd()};
        };
    }
}
