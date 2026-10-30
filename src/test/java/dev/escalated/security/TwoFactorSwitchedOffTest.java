package dev.escalated.security;

import static dev.escalated.security.AccessTestSupport.staff;
import static dev.escalated.security.AccessTestSupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.SettingsService;
import dev.escalated.services.TwoFactorService;
import hostapp.HostApplication;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Two-factor off, the default (ADR 2026-10-29-configurable-access-features): nobody is challenged,
 * the enrolment and challenge routes answer 404 and enrolments are kept.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class TwoFactorSwitchedOffTest {

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private TwoFactorService twoFactor;
    @Autowired private SettingsService settings;
    @Autowired private EscalatedProperties properties;

    @AfterEach
    void reset() {
        properties.getTwoFactor().setEncryptionKey(null);
        settings.set("two_factor_required", "0", "general");
    }

    @Test
    void anEnrolledUserIsNotChallengedAndTheRoutesAnswer404() throws Exception {
        properties.getTwoFactor().setEncryptionKey("test-two-factor-key");
        AgentProfile agent = staff(agents, "agent", false, null);
        TwoFactorService.Setup setup = twoFactor.setup(agent);
        Matcher secret = Pattern.compile("secret=([A-Z2-7]+)").matcher(setup.qrUri());
        assertThat(secret.find()).isTrue();
        assertThat(twoFactor.confirm(agent.getId(), twoFactor.codeAt(secret.group(1), Instant.now()))).isPresent();
        // Even marked required: the switch is off, so nothing is required.
        settings.set("two_factor_required", "1", "general");
        settings.set("two_factor", "1", "general");

        mvc.perform(get("/escalated/agent").session(new MockHttpSession()).with(user(agent.getEmail())))
                .andExpect(status().isOk());
        for (String path : new String[] {"/escalated/admin/settings/two-factor", "/escalated/two-factor/challenge"}) {
            mvc.perform(get(path).with(user(agent.getEmail()))).andExpect(status().isNotFound());
        }
        for (String path : new String[] {"/escalated/admin/settings/two-factor/setup",
                "/escalated/admin/settings/two-factor/confirm", "/escalated/admin/settings/two-factor/disable",
                "/escalated/two-factor/challenge"}) {
            mvc.perform(post(path).with(user(agent.getEmail())).with(xsrf()).header("X-Inertia", "true"))
                    .andExpect(status().isNotFound());
        }
        assertThat(twoFactor.confirmedFor(agent.getId())).isPresent();
        var escalated = AccessTestSupport.shared(mvc, "/escalated/agent", agent);
        assertThat(escalated.get("features").get("two_factor").asBoolean()).isFalse();
        assertThat(escalated.get("two_factor_required").asBoolean()).isFalse();
    }
}
