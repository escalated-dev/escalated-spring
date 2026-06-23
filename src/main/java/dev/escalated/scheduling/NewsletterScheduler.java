package dev.escalated.scheduling;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.newsletter.Newsletter;
import dev.escalated.repositories.NewsletterRepository;
import dev.escalated.services.newsletter.NewsletterDispatcher;
import dev.escalated.services.newsletter.NewsletterPlanner;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class NewsletterScheduler {

    private static final Logger log = LoggerFactory.getLogger(NewsletterScheduler.class);

    private final EscalatedProperties properties;
    private final NewsletterRepository newsletters;
    private final NewsletterPlanner planner;
    private final NewsletterDispatcher dispatcher;
    private final AtomicBoolean tickRunning = new AtomicBoolean(false);

    public NewsletterScheduler(
            EscalatedProperties properties,
            NewsletterRepository newsletters,
            NewsletterPlanner planner,
            NewsletterDispatcher dispatcher) {
        this.properties = properties;
        this.newsletters = newsletters;
        this.planner = planner;
        this.dispatcher = dispatcher;
    }

    @Scheduled(cron = "0 * * * * *")
    public void dispatchNewsletters() {
        if (!properties.getNewsletters().isEnabled()) {
            return;
        }
        if (!tickRunning.compareAndSet(false, true)) {
            log.warn("Previous newsletter dispatch tick is still running; skipping");
            return;
        }
        try {
            List<Newsletter> due = newsletters.findByStatusAndScheduledAtLessThanEqual(
                    "scheduled", Instant.now());
            for (Newsletter newsletter : due) {
                planner.plan(newsletter);
            }
            dispatcher.dispatchBatch();
        } catch (Exception ex) {
            log.error("Error dispatching newsletters", ex);
        } finally {
            tickRunning.set(false);
        }
    }
}
