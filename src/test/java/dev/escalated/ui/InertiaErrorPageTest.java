package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import errorprobe.ErrorProbe;
import hostapp.HostApplication;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A refusal or a failure inside the panel stays inside the panel.
 *
 * <p>An Inertia visit that gets an error body back has nowhere to put it: the client opens it in
 * a modal over the screen the user was on. So an Inertia visit to one of Escalated's pages that
 * ends in 403, 404, 419, 429, 500 or 503 gets the shared {@code Escalated/Error} page, with the
 * props Laravel and Rails send: the status and a generic message. Full page loads, JSON and API
 * clients and the host's own routes are left alone.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ErrorProbe.class)
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
})
class InertiaErrorPageTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private EscalatedProperties properties;

    private AgentProfile admin;
    private AgentProfile agent;

    @BeforeEach
    void seed() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin = agents.save(admin);

        agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent = agents.save(agent);
    }

    @AfterEach
    void debugOff() {
        properties.setDebug(false);
    }

    private static MockHttpServletRequestBuilder inertia(MockHttpServletRequestBuilder request) {
        return request.header("X-Inertia", "true").header("X-Inertia-Version", "build-1");
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private static JsonNode errorPage(MockHttpServletResponse response, int status, String message)
            throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeader("X-Inertia")).isEqualTo("true");
        JsonNode page = JSON.readTree(response.getContentAsString());
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Error");
        assertThat(page.get("props").get("status").asInt()).isEqualTo(status);
        assertThat(page.get("props").get("message").asText()).isEqualTo(message);
        return page;
    }

    @Test
    void aRefusedInertiaVisitGetsTheErrorPage() throws Exception {
        MockHttpServletResponse response =
                perform(inertia(get("/escalated/admin/settings")).with(user(agent.getEmail())));

        errorPage(response, 403, "You are not authorized to perform this action.");
    }

    @Test
    void aMissingRecordGetsTheErrorPageWithoutTheControllersReason() throws Exception {
        MockHttpServletResponse response =
                perform(inertia(get("/escalated/admin/import/nowhere/connect")).with(user(admin.getEmail())));

        errorPage(response, 404, "The requested resource was not found.");
        assertThat(response.getContentAsString()).doesNotContain("Import adapter");
    }

    @Test
    void aRefusedCsrfTokenOnAnInertiaWriteGetsTheErrorPage() throws Exception {
        MockHttpServletResponse response = perform(inertia(post("/escalated/admin/settings"))
                .with(user(admin.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        errorPage(response, 403, "You are not authorized to perform this action.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "419|Your session has expired. Refresh the page and try again.",
        "429|Too many requests. Wait a moment and try again.",
        "500|Something went wrong on our end.",
        "503|The service is temporarily unavailable. Try again shortly.",
    })
    void eachStatusThePageHasWordingFor(int status, String message) throws Exception {
        MockHttpServletResponse response =
                perform(inertia(get("/escalated/error-probe/" + status)).with(user(agent.getEmail())));

        errorPage(response, status, message);
        assertThat(response.getContentAsString())
                .doesNotContain("upstream detail")
                .doesNotContain("escalated_secret_table");
    }

    @Test
    void aFailedResponseGetsTheErrorPageWithoutItsBody() throws Exception {
        MockHttpServletResponse response =
                perform(inertia(get("/escalated/error-probe/500-response")).with(user(agent.getEmail())));

        errorPage(response, 500, "Something went wrong on our end.");
        assertThat(response.getContentAsString()).doesNotContain("escalated_secret_table");
    }

    /*
     * With escalated.debug on, a 500 is the developer's to read: an exception goes on to the
     * container and Boot's error handling, and a 500 a controller returned goes out as written, the
     * way escalated-laravel leaves a 500 to the framework's debug page under APP_DEBUG. Every other
     * status still gets the page. (The flag is read per request, so these share the cached context.)
     */

    @Test
    void inDebugModeAnUnhandledExceptionReachesTheFrameworksOwnErrorHandling() {
        properties.setDebug(true);

        assertThatThrownBy(() -> perform(inertia(get("/escalated/error-probe/500")).with(user(agent.getEmail()))))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessage("relation escalated_secret_table does not exist");
    }

    @Test
    void inDebugModeAFailedResponseGoesOutAsWritten() throws Exception {
        properties.setDebug(true);

        MockHttpServletResponse response =
                perform(inertia(get("/escalated/error-probe/500-response")).with(user(agent.getEmail())));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader("X-Inertia")).isNull();
        assertThat(response.getContentAsString())
                .isEqualTo("upstream detail: relation escalated_secret_table does not exist");
    }

    @Test
    void inDebugModeOtherStatusesStillGetTheErrorPage() throws Exception {
        properties.setDebug(true);

        errorPage(
                perform(inertia(get("/escalated/error-probe/503")).with(user(agent.getEmail()))),
                503,
                "The service is temporarily unavailable. Try again shortly.");
    }

    @Test
    void sendsOnlyTheStatusAndTheMessage() throws Exception {
        JsonNode page = errorPage(
                perform(inertia(get("/escalated/admin/settings")).with(user(agent.getEmail()))),
                403,
                "You are not authorized to perform this action.");

        assertThat(page.get("props").size()).isEqualTo(2);
        assertThat(page.get("version").asText()).isEqualTo("build-1");
        assertThat(page.get("url").asText()).isEqualTo("/escalated/admin/settings");
    }

    @Test
    void leavesAFullPageLoadAlone() throws Exception {
        MockHttpServletResponse response = perform(get("/escalated/admin/settings").with(user(agent.getEmail())));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("X-Inertia")).isNull();
        assertThat(response.getContentAsString()).doesNotContain("Escalated/Error");
    }

    @Test
    void leavesAJsonRequestAlone() throws Exception {
        MockHttpServletResponse response = perform(get("/escalated/admin/settings")
                .accept(MediaType.APPLICATION_JSON)
                .with(user(agent.getEmail())));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("X-Inertia")).isNull();
    }

    @Test
    void leavesTheApiAlone() throws Exception {
        MockHttpServletResponse response =
                perform(inertia(get("/escalated/api/error-probe")).with(user(agent.getEmail())));

        assertThat(response.getHeader("X-Inertia")).isNull();
        assertThat(response.getContentAsString()).doesNotContain("Escalated/Error");
    }

    @Test
    void leavesAHostRouteAlone() throws Exception {
        MockHttpServletResponse response = perform(inertia(get("/host/error-probe")));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getHeader("X-Inertia")).isNull();
        assertThat(response.getContentAsString()).isEqualTo("host page");
    }

    @Test
    void leavesRedirectsAlone() throws Exception {
        MockHttpServletResponse response =
                perform(inertia(get("/escalated/error-probe/redirect")).with(user(agent.getEmail())));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/escalated/agent");
        assertThat(response.getHeader("X-Inertia")).isNull();
    }
}
