package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.escalated.models.AgentProfile;
import embedhost.EmbedHost;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * iframe mode (host type C): embedded responses may be framed by the listed origins only, and
 * nothing else changes.
 */
class EmbeddedAdminIframeTest extends EmbedTestSupport {

    private static final String CSP = "Content-Security-Policy";

    @Autowired private EscalatedEmbed embed;

    @BeforeEach
    void iframeMode() {
        properties.getUi().getEmbed().setMode("iframe");
        properties.getUi().getEmbed().setFrameAncestors(new ArrayList<>(List.of("https://allowed.example")));
    }

    @Test
    void embeddedResponsesAllowTheListedOriginsToFrameThem() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        MockHttpServletResponse response = mvc.perform(get("/admin/support/tickets")
                        .session(signedIn(admin.getEmail())).with(INERTIA))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeaders(CSP)).contains("frame-ancestors 'self' https://allowed.example")
                // The host's own policy is kept, as a second header.
                .contains(EmbedHost.HOST_CSP);
        assertThat(response.getHeader("X-Frame-Options")).isNull();

        JsonNode embedProp = JSON.readTree(response.getContentAsString()).get("props").get("escalated").get("embed");
        assertThat(embedProp.get("mode").asText()).isEqualTo("iframe");
        assertThat(embedProp.get("allowed_origins")).hasSize(1);
        assertThat(embedProp.get("allowed_origins").get(0).asText()).isEqualTo("https://allowed.example");

        // Full page loads and redirects too.
        MockHttpServletResponse full = mvc.perform(get("/admin/support/tickets").session(signedIn(admin.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(full.getHeaders(CSP)).contains("frame-ancestors 'self' https://allowed.example");
        assertThat(full.getHeader("X-Frame-Options")).isNull();
        MockHttpServletResponse redirect = mvc.perform(post("/admin/support/tags").session(signedIn(admin.getEmail()))
                        .with(INERTIA).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Framed " + unique + "\"}"))
                .andExpect(status().isSeeOther()).andReturn().getResponse();
        assertThat(redirect.getHeaders(CSP)).contains("frame-ancestors 'self' https://allowed.example");
        assertThat(redirect.getHeader("X-Frame-Options")).isNull();
    }

    @Test
    void theCsrfCookieCanBeSentFromAFrameOnAnotherSite() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        Cookie framed = mvc.perform(get("/admin/support/tickets").session(signedIn(admin.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(framed).isNotNull();
        assertThat(framed.getAttribute("SameSite")).isEqualTo("None");
        assertThat(framed.getSecure()).isTrue();

        properties.getUi().getEmbed().setMode("layout");
        Cookie layout = mvc.perform(get("/admin/support/tickets").session(signedIn(admin.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(layout).isNotNull();
        assertThat(layout.getAttribute("SameSite")).isNull();
        assertThat(layout.getSecure()).isFalse();
    }

    @Test
    void everythingElseKeepsItsHeaders() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        MockHttpServletResponse agentPage = mvc.perform(get("/escalated/agent/tickets")
                        .session(signedIn(admin.getEmail())).with(INERTIA))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(agentPage.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(agentPage.getHeaders(CSP)).noneMatch(value -> value.contains("frame-ancestors"));
        JsonNode embedProp = JSON.readTree(agentPage.getContentAsString()).get("props").get("escalated").get("embed");
        assertThat(embedProp.get("enabled").asBoolean()).isFalse();
    }

    @Test
    void layoutModeAddsNoHeader() throws Exception {
        properties.getUi().getEmbed().setMode("layout");
        AgentProfile admin = profile("admin", true, true);
        MockHttpServletResponse response = mvc.perform(get("/admin/support/tickets")
                        .session(signedIn(admin.getEmail())).with(INERTIA))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeaders(CSP)).containsExactly(EmbedHost.HOST_CSP);
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        JsonNode embedProp = JSON.readTree(response.getContentAsString()).get("props").get("escalated").get("embed");
        assertThat(embedProp.get("allowed_origins")).as("[] in layout mode").isEmpty();
    }

    @Test
    void onlyExactHttpOriginsAreAllowed() {
        properties.getUi().getEmbed().setFrameAncestors(new ArrayList<>(List.of(
                "https://allowed.example", " https://allowed.example/ ", "*", "https://*.example.com", "ftp://files.example",
                "https://admin.example/path", "https://user@admin.example", "HTTPS://Upper.Example:8443", "not a url",
                "'self'")));
        assertThat(embed.frameAncestors()).containsExactly("https://allowed.example", "https://upper.example:8443");
        assertThat(embed.frameAncestorsPolicy())
                .isEqualTo("frame-ancestors 'self' https://allowed.example https://upper.example:8443");

        properties.getUi().getEmbed().setFrameAncestors(new ArrayList<>(List.of("*")));
        assertThat(embed.frameAncestorsPolicy()).isEqualTo("frame-ancestors 'self'");
    }
}
