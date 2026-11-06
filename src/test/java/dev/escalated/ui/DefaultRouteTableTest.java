package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import hostapp.HostApplication;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With no embed configuration the route table is exactly what it was before the embeddable admin
 * existed: every Escalated mapping, its security chain, filters and gate decisions, the servlet
 * filters and the {@code route()} names, compared with a snapshot taken from that code.
 *
 * <p>The live table is also written to {@code build/embed/default-routes.actual.json}, to diff.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.newsletters.enabled=true",
})
class DefaultRouteTableTest {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;

    @Test
    void routeTableIsUnchangedWithoutEmbedConfig() throws Exception {
        Map<String, Object> live = RouteTableSnapshot.of(context);
        String actual = JSON.writeValueAsString(live) + "\n";
        Path out = Path.of("build", "embed", "default-routes.actual.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, actual);

        try (InputStream fixture = getClass().getResourceAsStream("/embed/default-routes.json")) {
            assertThat(fixture).as("src/test/resources/embed/default-routes.json").isNotNull();
            Object expected = JSON.readValue(fixture, Object.class);
            assertThat(JSON.readValue(actual, Object.class)).isEqualTo(expected);
        }
    }

    @Test
    void noEmbedPropsHeadersOrUrlsWithoutEmbedConfig() throws Exception {
        assertThat(context.getBeansOfType(EmbedResponseFilter.class)).isEmpty();
        assertThat(context.getBean(EscalatedEmbed.class).enabled()).isFalse();
        AgentProfile admin = new AgentProfile();
        admin.setName("Default admin");
        admin.setEmail("default-admin-" + UUID.randomUUID() + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin = agents.save(admin);

        MockHttpServletResponse response = mvc.perform(get("/escalated/admin/tickets").with(user(admin.getEmail()))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk()).andReturn().getResponse();
        JsonNode escalated = JSON.readTree(response.getContentAsString()).get("props").get("escalated");
        assertThat(escalated.has("embed")).isFalse();
        assertThat(escalated.has("navigation")).isFalse();
        assertThat(response.getHeaders("Content-Security-Policy")).isEmpty();
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");

        mvc.perform(get("/admin/support/tickets").with(user(admin.getEmail())).header("X-Inertia", "true"))
                .andExpect(status().isNotFound());
    }
}
