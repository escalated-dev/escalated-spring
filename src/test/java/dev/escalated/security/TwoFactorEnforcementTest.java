package dev.escalated.security;

import static dev.escalated.security.AccessTestSupport.staff;
import static dev.escalated.security.AccessTestSupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.TwoFactor;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.TwoFactorRepository;
import dev.escalated.services.ApiTokenService;
import dev.escalated.services.SettingsService;
import dev.escalated.services.TwoFactorService;
import hostapp.HostApplication;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Two-factor switched on (ADR 2026-10-29-configurable-access-features, port test checklist), through
 * the real security chains and router. The switch is read on every request, so it is turned on at
 * runtime, in the context the other UI tests share.
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
class TwoFactorEnforcementTest {

    private static final Pattern SECRET = Pattern.compile("secret=([A-Z2-7]+)");

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private TwoFactorService twoFactor;
    @Autowired private TwoFactorRepository enrolments;
    @Autowired private SettingsService settings;
    @Autowired private ApiTokenService apiTokens;
    @Autowired private ApplicationEventPublisher events;

    @Autowired private dev.escalated.config.EscalatedProperties properties;

    /** Switched at runtime rather than by a property, so this shares the UI tests' cached context. */
    @org.junit.jupiter.api.BeforeEach
    void switchOn() {
        properties.getAccess().setTwoFactor(true);
        properties.getTwoFactor().setEncryptionKey("test-two-factor-key");
    }

    @AfterEach
    void switchOff() {
        settings.set("two_factor_required", "0", "general");
        properties.getAccess().setTwoFactor(null);
        properties.getTwoFactor().setEncryptionKey(null);
    }

    /** A confirmed enrolment for {@code who}; returns its base32 secret. */
    private String enrol(AgentProfile who) {
        TwoFactorService.Setup setup = twoFactor.setup(who);
        Matcher secret = SECRET.matcher(setup.qrUri());
        assertThat(secret.find()).isTrue();
        assertThat(twoFactor.confirm(who.getId(), twoFactor.codeAt(secret.group(1), Instant.now()))).isPresent();
        return secret.group(1);
    }

    /** A code for the next time step: still inside the window, and never used yet. */
    private String nextCode(String secret) {
        return twoFactor.codeAt(secret, Instant.now().plusSeconds(30));
    }

    private ResultActions verify(MockHttpSession session, AgentProfile who, String json) throws Exception {
        return mvc.perform(post("/escalated/two-factor/challenge").session(session).with(user(who.getEmail()))
                .with(xsrf()).header("X-Inertia", "true")
                .header("Referer", "http://localhost/escalated/two-factor/challenge")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode page(MockHttpSession session, AgentProfile who, String path) throws Exception {
        String body = mvc.perform(get(path).session(session).with(user(who.getEmail())).header("X-Inertia", "true")
                        .header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return AccessTestSupport.JSON.readTree(body);
    }

    @Test
    void anEnrolledUserIsSentToTheChallengeFromAdminAndAgentPages() throws Exception {
        AgentProfile agent = staff(agents, "agent", false, null);
        AgentProfile admin = staff(agents, "admin", true, null);
        enrol(agent);
        enrol(admin);
        mvc.perform(get("/escalated/agent").session(new MockHttpSession()).with(user(agent.getEmail())))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/escalated/two-factor/challenge"));
        mvc.perform(get("/escalated/admin/settings").session(new MockHttpSession()).with(user(admin.getEmail())))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/escalated/two-factor/challenge"));
        // A JSON client is told, not redirected.
        mvc.perform(get("/escalated/agent/tickets").with(user(agent.getEmail())).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.two_factor_required").value(true));

        JsonNode challenge = page(new MockHttpSession(), agent, "/escalated/two-factor/challenge");
        assertThat(challenge.get("component").asText()).isEqualTo("Escalated/Auth/TwoFactorChallenge");
        assertThat(challenge.get("props").get("action").asText())
                .isEqualTo("http://localhost/escalated/two-factor/challenge");
        assertThat(challenge.get("props").get("error").asText()).isEmpty();
    }

    @Test
    void aValidCodeReturnsToTheIntendedPageAndWorksOnce() throws Exception {
        AgentProfile agent = staff(agents, "agent", false, null);
        String secret = enrol(agent);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/escalated/agent/tickets").session(session).with(user(agent.getEmail())))
                .andExpect(status().isFound());

        // A wrong code is an error on `code`.
        verify(session, agent, "{\"code\":\"000000\"}").andExpect(status().isSeeOther());
        assertThat(page(session, agent, "/escalated/two-factor/challenge").get("props").get("errors").get("code")
                .asText()).isEqualTo("That code is not valid.");

        String code = nextCode(secret);
        verify(session, agent, "{\"code\":\"" + code + "\"}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/agent/tickets"));
        mvc.perform(get("/escalated/agent/tickets").session(session).with(user(agent.getEmail())))
                .andExpect(status().isOk());

        // The same code (same time step) in another session is refused.
        MockHttpSession other = new MockHttpSession();
        verify(other, agent, "{\"code\":\"" + code + "\"}").andExpect(status().isSeeOther());
        mvc.perform(get("/escalated/agent").session(other).with(user(agent.getEmail())))
                .andExpect(status().isFound());
    }

    @Test
    void aRecoveryCodePassesOnce() throws Exception {
        AgentProfile agent = staff(agents, "agent", false, null);
        TwoFactorService.Setup setup = twoFactor.setup(agent);
        Matcher secret = SECRET.matcher(setup.qrUri());
        assertThat(secret.find()).isTrue();
        twoFactor.confirm(agent.getId(), twoFactor.codeAt(secret.group(1), Instant.now()));
        String recovery = setup.recoveryCodes().get(0);

        MockHttpSession first = new MockHttpSession();
        verify(first, agent, "{\"recovery_code\":\"" + recovery + "\"}").andExpect(status().isSeeOther());
        mvc.perform(get("/escalated/agent").session(first).with(user(agent.getEmail()))).andExpect(status().isOk());

        MockHttpSession second = new MockHttpSession();
        verify(second, agent, "{\"recovery_code\":\"" + recovery + "\"}").andExpect(status().isSeeOther());
        assertThat(page(second, agent, "/escalated/two-factor/challenge").get("props").get("errors")
                .get("recovery_code").asText()).isEqualTo("That recovery code is not valid.");
        mvc.perform(get("/escalated/agent").session(second).with(user(agent.getEmail()))).andExpect(status().isFound());
    }

    @Test
    void theVerifyRouteIsThrottled() throws Exception {
        AgentProfile agent = staff(agents, "agent", false, null);
        enrol(agent);
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < 5; i++) {
            verify(session, agent, "{\"code\":\"000000\"}").andExpect(status().isSeeOther());
        }
        verify(session, agent, "{\"code\":\"000000\"}").andExpect(status().isTooManyRequests());
    }

    @Test
    void aNewLoginIsChallengedAgain() throws Exception {
        AgentProfile agent = staff(agents, "agent", false, null);
        String secret = enrol(agent);
        MockHttpSession session = new MockHttpSession();
        verify(session, agent, "{\"code\":\"" + nextCode(secret) + "\"}").andExpect(status().isSeeOther());
        mvc.perform(get("/escalated/agent").session(session).with(user(agent.getEmail()))).andExpect(status().isOk());

        // The host's login (an interactive sign-in) in this session clears the mark.
        MockHttpServletRequest login = new MockHttpServletRequest();
        login.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(login));
        try {
            events.publishEvent(new InteractiveAuthenticationSuccessEvent(
                    UsernamePasswordAuthenticationToken.authenticated(agent.getEmail(), null, List.of()), getClass()));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        mvc.perform(get("/escalated/agent").session(session).with(user(agent.getEmail()))).andExpect(status().isFound());
        // And a session that passed for one user does not count for another.
        AgentProfile other = staff(agents, "other", false, null);
        enrol(other);
        mvc.perform(get("/escalated/agent").session(session).with(user(other.getEmail()))).andExpect(status().isFound());
    }

    @Test
    void anAgentCanEnrol() throws Exception {
        AgentProfile agent = staff(agents, "agent", false, null);
        MockHttpSession session = new MockHttpSession();
        JsonNode index = page(session, agent, "/escalated/admin/settings/two-factor");
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/Settings/TwoFactor");
        assertThat(index.get("props").get("enabled").asBoolean()).isFalse();
        assertThat(index.get("props").get("pending").asBoolean()).isFalse();
        assertThat(index.get("props").get("required").asBoolean()).isFalse();

        mvc.perform(post("/escalated/admin/settings/two-factor/setup").session(session).with(user(agent.getEmail()))
                .with(xsrf()).header("X-Inertia", "true")).andExpect(status().isSeeOther());
        JsonNode pending = page(session, agent, "/escalated/admin/settings/two-factor");
        JsonNode setup = pending.get("props").get("flash").get("two_factor_setup");
        assertThat(setup.get("qr_uri").asText()).startsWith("otpauth://totp/");
        assertThat(setup.get("recovery_codes").size()).isEqualTo(8);
        assertThat(pending.get("props").get("pending").asBoolean()).isTrue();
        Matcher secret = SECRET.matcher(setup.get("qr_uri").asText());
        assertThat(secret.find()).isTrue();
        // Stored encrypted, never as the base32 secret itself.
        TwoFactor stored = enrolments.findByAgentId(agent.getId()).orElseThrow();
        assertThat(stored.getSecret()).doesNotContain(secret.group(1));
        assertThat(pending.toString()).doesNotContain(stored.getSecret());

        mvc.perform(post("/escalated/admin/settings/two-factor/confirm").session(session).with(user(agent.getEmail()))
                        .with(xsrf()).header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + twoFactor.codeAt(secret.group(1), Instant.now()) + "\"}"))
                .andExpect(status().isSeeOther());
        JsonNode confirmed = page(session, agent, "/escalated/admin/settings/two-factor");
        assertThat(confirmed.get("props").get("enabled").asBoolean()).isTrue();
        assertThat(confirmed.get("props").get("flash").get("two_factor_confirmed").get("recovery_codes").size())
                .isEqualTo(8);

        mvc.perform(post("/escalated/admin/settings/two-factor/disable").session(session).with(user(agent.getEmail()))
                .with(xsrf()).header("X-Inertia", "true")).andExpect(status().isSeeOther());
        assertThat(enrolments.findByAgentId(agent.getId())).isEmpty();
    }

    @Test
    void whenRequiredAnUnenrolledUserCanReachOnlyEnrolment() throws Exception {
        AgentProfile admin = staff(agents, "admin", true, null);
        AgentProfile agent = staff(agents, "agent", false, null);
        mvc.perform(get("/escalated/agent").with(user(agent.getEmail()))).andExpect(status().isOk());
        assertThat(AccessTestSupport.shared(mvc, "/escalated/agent", agent).get("two_factor_required").asBoolean())
                .isFalse();

        // The frontend's "require for all staff" switch posts only this key (escalated-laravel #275).
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/settings").session(session).with(user(admin.getEmail())).with(xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost/escalated/admin/settings/two-factor")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"two_factor_required\":true}"))
                .andExpect(status().isSeeOther());
        assertThat(settings.get("two_factor_required")).contains("1");

        mvc.perform(get("/escalated/agent").with(user(agent.getEmail())))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/escalated/admin/settings/two-factor"));
        mvc.perform(get("/escalated/admin/settings").with(user(admin.getEmail()))).andExpect(status().isFound());
        JsonNode enrolment = page(new MockHttpSession(), agent, "/escalated/admin/settings/two-factor");
        assertThat(enrolment.get("props").get("required").asBoolean()).isTrue();
        assertThat(enrolment.get("props").get("escalated").get("two_factor_required").asBoolean()).isTrue();
    }

    @Test
    void theTokenApiIsNotChallenged() throws Exception {
        AgentProfile agent = staff(agents, "agent", false, null);
        enrol(agent);
        String token = (String) apiTokens.createToken("cli", agent.getId(), "*", null).get("plainTextToken");
        mvc.perform(get("/escalated/api/agent/tickets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void theSwitchIsShared() throws Exception {
        AgentProfile agent = staff(agents, "agent", false, null);
        assertThat(AccessTestSupport.shared(mvc, "/escalated/agent", agent).get("features").get("two_factor")
                .asBoolean()).isTrue();
    }

    @Test
    void anAdminCanResetAnEnrolment() {
        AgentProfile agent = staff(agents, "agent", false, null);
        enrol(agent);
        twoFactor.reset(agent.getEmail());
        assertThat(enrolments.findByAgentId(agent.getId())).isEmpty();
    }
}
