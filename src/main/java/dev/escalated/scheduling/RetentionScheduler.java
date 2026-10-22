package dev.escalated.scheduling;

import dev.escalated.services.DataRetentionService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the data retention purge once a day (03:30 by default; {@code escalated.retention.cron}),
 * as escalated-laravel schedules {@code escalated:purge-expired}. Every retention period defaults
 * to {@code never}, so until an admin sets one it only removes expired guest verification codes.
 */
@Component
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final DataRetentionService retention;

    public RetentionScheduler(DataRetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "${escalated.retention.cron:0 30 3 * * *}")
    public void scheduled() {
        purgeExpired();
    }

    /** One purge run, now. */
    public DataRetentionService.Report purgeExpired() {
        DataRetentionService.Report report = retention.purgeExpired(false, Instant.now());
        if (report.closedTickets() + report.attachments() + report.auditLogs() > 0) {
            log.info("Data retention purged {} closed tickets, {} attachments and {} audit logs",
                    report.closedTickets(), report.attachments(), report.auditLogs());
        }
        return report;
    }
}
