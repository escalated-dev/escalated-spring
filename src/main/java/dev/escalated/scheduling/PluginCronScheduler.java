package dev.escalated.scheduling;

import dev.escalated.plugins.PluginRegistry;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs active plugins' (native and SDK) due cron schedules at the start of every minute (UTC). */
@Component
public class PluginCronScheduler {

    private final ObjectProvider<PluginRegistry> plugins;

    public PluginCronScheduler(ObjectProvider<PluginRegistry> plugins) {
        this.plugins = plugins;
    }

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    public void runPluginCron() {
        PluginRegistry registry = plugins.getIfAvailable();
        if (registry != null && registry.hasPlugins()) {
            registry.runDueCron(Instant.now());
        }
    }
}
