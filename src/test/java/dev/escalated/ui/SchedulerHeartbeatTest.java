package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.scheduling.SchedulerHeartbeat;
import dev.escalated.scheduling.SlaScheduler;
import dev.escalated.services.SettingsService;
import hostapp.HostApplication;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The SLA sweep records a heartbeat each time it runs, and admins are told when it has not run for
 * 15 minutes: {@code scheduler} on the settings and report pages, the admin API's status, and a log
 * warning. The sweep runs once at start-up and then not for a day here, so nothing overwrites what a
 * test sets.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
    "escalated.sla.check-interval-seconds=86400",
})
class SchedulerHeartbeatTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private SettingsService settings;
    @Autowired private SchedulerHeartbeat heartbeat;
    @Autowired private SlaScheduler sla;
    @Autowired private AgentProfileRepository agents;

    private static volatile boolean startupRunSeen;

    private AgentProfile admin;

    @BeforeEach
    void seed() throws InterruptedException {
        if (!startupRunSeen) {
            // The scheduled start-up run records the first heartbeat on its own; wait for it before
            // any test changes the setting.
            Instant deadline = Instant.now().plusSeconds(30);
            while (heartbeat.lastRun() == null && Instant.now().isBefore(deadline)) {
                Thread.sleep(100);
            }
            assertThat(heartbeat.lastRun()).as("heartbeat recorded by the scheduled SLA sweep").isNotNull();
            startupRunSeen = true;
        }

        String unique = UUID.randomUUID().toString().substring(0, 8);
        admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin = agents.save(admin);
    }

    @Test
    void theSlaSweepRecordsAHeartbeat() {
        settings.delete(SchedulerHeartbeat.SETTING);
        assertThat(heartbeat.lastRun()).isNull();

        Instant before = Instant.now().minusSeconds(1);
        sla.checkSlaBreaches();

        assertThat(heartbeat.lastRun()).isNotNull().isAfter(before);
        assertThat(heartbeat.status().get("healthy")).isEqualTo(true);
    }

    @Test
    void aMissingHeartbeatIsUnhealthyAndNamesTheJobs(CapturedOutput output) throws Exception {
        // Healthy first, so the stop below is a fresh one and is logged.
        heartbeat.beat();
        assertThat(heartbeat.status().get("healthy")).isEqualTo(true);
        settings.delete(SchedulerHeartbeat.SETTING);

        JsonNode scheduler = adminSettingsScheduler();
        assertThat(scheduler.get("healthy").asBoolean()).isFalse();
        assertThat(scheduler.get("last_run_at").isNull()).isTrue();
        assertThat(scheduler.get("message").asText()).contains("not running").contains("never run");
        assertThat(scheduler.get("commands")).hasSize(4);
        assertThat(scheduler.get("commands").get(0).get("command").asText())
                .contains("SlaScheduler#checkSlaBreaches");
        assertThat(scheduler.get("commands").get(0).get("interval").asText())
                .contains("86400 seconds").contains("escalated.sla.check-interval-seconds");
        assertThat(scheduler.toString()).contains("SnoozeScheduler#wakeUpSnoozedTickets")
                .contains("AutomationScheduler#runAutomations");
        assertThat(output.getAll()).contains("recurring jobs are not running");
    }

    @Test
    void aHeartbeatOlderThanFifteenMinutesIsUnhealthy() throws Exception {
        Instant stale = Instant.now().minus(Duration.ofMinutes(16));
        settings.set(SchedulerHeartbeat.SETTING, stale.toString(), "scheduler");

        JsonNode scheduler = adminSettingsScheduler();
        assertThat(scheduler.get("healthy").asBoolean()).isFalse();
        assertThat(scheduler.get("last_run_at").asText()).isEqualTo(stale.toString());
        assertThat(scheduler.get("message").asText()).contains("more than 15 minutes ago");
    }

    @Test
    void aRecentHeartbeatIsHealthy() throws Exception {
        Instant recent = Instant.now().minus(Duration.ofMinutes(14));
        settings.set(SchedulerHeartbeat.SETTING, recent.toString(), "scheduler");

        JsonNode scheduler = adminSettingsScheduler();
        assertThat(scheduler.get("healthy").asBoolean()).isTrue();
        assertThat(scheduler.get("last_run_at").asText()).isEqualTo(recent.toString());
    }

    @Test
    void theReportPagesAndTheAdminApiCarryTheSameStatus() throws Exception {
        settings.delete(SchedulerHeartbeat.SETTING);

        for (String path : new String[] {"/escalated/admin/reports", "/escalated/admin/reports/dashboard"}) {
            JsonNode scheduler = inertia(get(path)).get("props").get("scheduler");
            assertThat(scheduler).as(path).isNotNull();
            assertThat(scheduler.get("healthy").asBoolean()).as(path).isFalse();
            assertThat(scheduler.get("commands")).as(path).hasSize(4);
        }

        String body = mvc.perform(get("/escalated/api/admin/settings/scheduler").with(user(admin.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode api = JSON.readTree(body);
        assertThat(api.get("healthy").asBoolean()).isFalse();
        assertThat(api.has("last_run_at")).isTrue();
        assertThat(api.get("commands")).hasSize(4);

        mvc.perform(get("/escalated/api/admin/settings/scheduler").with(user("nobody-" + UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    private JsonNode adminSettingsScheduler() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/settings"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Settings");
        return page.get("props").get("scheduler");
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request.with(user(admin.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }
}
