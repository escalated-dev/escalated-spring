package dev.escalated.scheduling;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.services.SettingsService;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Whether the recurring jobs are running.
 *
 * <p>Escalated runs its recurring jobs in-process with Spring's {@code @Scheduled} (its
 * auto-configuration turns on {@code @EnableScheduling}), so a host schedules nothing itself. They
 * still stop when the application's task scheduler does not run them: scheduling switched off, a
 * scheduler starved by other work, or a job that fails every time. The SLA sweep
 * ({@link SlaScheduler#checkSlaBreaches()}) records a heartbeat in the {@code scheduler_heartbeat_at}
 * setting each time it runs; when the heartbeat is missing or older than {@link #STALE}, admins see
 * a warning ({@code scheduler} on {@code Escalated/Admin/Settings} and the admin report pages, and
 * {@code GET /escalated/api/admin/settings/scheduler}) and a warning is logged.
 */
@Component
public class SchedulerHeartbeat {

    /** The setting the heartbeat is kept in, as an ISO 8601 instant. */
    public static final String SETTING = "scheduler_heartbeat_at";
    static final String GROUP = "scheduler";
    public static final Duration STALE = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(SchedulerHeartbeat.class);

    private final SettingsService settings;
    private final EscalatedProperties properties;
    private volatile Instant lastWarned;

    public SchedulerHeartbeat(SettingsService settings, EscalatedProperties properties) {
        this.settings = settings;
        this.properties = properties;
    }

    /** Records that the SLA sweep ran now. A failure is logged, never thrown into the sweep. */
    public void beat() {
        beat(Instant.now());
    }

    void beat(Instant now) {
        try {
            settings.set(SETTING, now.toString(), GROUP);
        } catch (RuntimeException ex) {
            log.warn("Could not record the scheduler heartbeat: {}", ex.getMessage());
        }
    }

    /** When the SLA sweep last ran, or {@code null} if it never has (or the value is unreadable). */
    public Instant lastRun() {
        String value = settings.get(SETTING).orElse(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * The {@code scheduler} prop: {@code {healthy, last_run_at, message, commands: [{command,
     * interval}]}}. Unhealthy when the heartbeat is missing or older than 15 minutes, which also logs
     * a warning: once when the jobs are found stopped, then at most every 15 minutes until they run.
     */
    public Map<String, Object> status() {
        return status(Instant.now());
    }

    Map<String, Object> status(Instant now) {
        Instant last = lastRun();
        boolean healthy = last != null && !last.isBefore(now.minus(STALE));
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("healthy", healthy);
        status.put("last_run_at", last == null ? null : last.toString());
        status.put("message", healthy ? "The recurring jobs are running." : message(last));
        status.put("commands", commands());
        if (healthy) {
            lastWarned = null;
        } else {
            warn(now, last);
        }
        return status;
    }

    /** The jobs that must run, each a {@code @Scheduled} method, with its interval. */
    public List<Map<String, String>> commands() {
        return List.of(
                command("dev.escalated.scheduling.SlaScheduler#checkSlaBreaches (SLA checks and escalation rules)",
                        seconds(properties.getSla().getCheckIntervalSeconds(), "escalated.sla.check-interval-seconds")),
                command("dev.escalated.scheduling.AutomationScheduler#runAutomations (automations)",
                        seconds(properties.getAutomations().getCheckIntervalSeconds(),
                                "escalated.automations.check-interval-seconds")),
                command("dev.escalated.scheduling.AutomationScheduler#autoCloseResolvedTickets (auto-close)",
                        "every 5 minutes"),
                command("dev.escalated.scheduling.SnoozeScheduler#wakeUpSnoozedTickets (snooze wake-ups)",
                        seconds(properties.getSnooze().getCheckIntervalSeconds(),
                                "escalated.snooze.check-interval-seconds")));
    }

    private static String message(Instant last) {
        String when = last == null ? "has never run" : "last ran at " + last + ", more than 15 minutes ago";
        return "The recurring jobs are not running: the SLA check " + when + ". Escalated runs them in-process"
                + " with Spring's @Scheduled; make sure the application's task scheduler is running and that"
                + " the jobs listed here are not disabled or failing.";
    }

    private void warn(Instant now, Instant last) {
        Instant previous = lastWarned;
        if (previous != null && previous.isAfter(now.minus(STALE))) {
            return;
        }
        lastWarned = now;
        log.warn("Escalated's recurring jobs are not running: the SLA check {}. Each of these must run: {}",
                last == null ? "has never run" : "last ran at " + last, commands());
    }

    private static Map<String, String> command(String command, String interval) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("command", command);
        entry.put("interval", interval);
        return entry;
    }

    private static String seconds(int seconds, String property) {
        return "every " + seconds + " seconds (" + property + ")";
    }
}
