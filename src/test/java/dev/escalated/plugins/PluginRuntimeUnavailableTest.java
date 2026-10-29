package dev.escalated.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.PluginActivationRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The host keeps serving when the plugin runtime cannot start, or cannot serve HTTP. */
class PluginRuntimeUnavailableTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static AgentProfile admin(AgentProfileRepository agents) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AgentProfile admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        return agents.save(admin);
    }

    /** No Node, no package: the admin screen, tickets and filters work without SDK plugins. */
    @Nested
    @SpringBootTest(classes = HostApplication.class)
    @DirtiesContext
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
        "spring.mail.host=localhost",
        "escalated.ui.enabled=true",
        "escalated.ui.version=build-1",
        "escalated.plugins.runtime.enabled=true",
        "escalated.plugins.runtime.command=escalated-no-such-plugin-runtime",
    })
    class WhenItCannotStart {

        @Autowired private MockMvc mvc;
        @Autowired private AgentProfileRepository agents;
        @Autowired private PluginRegistry registry;
        @Autowired private TicketService tickets;

        @Test
        void theHostCarriesOn() throws Exception {
            AgentProfile admin = admin(agents);
            MvcResult result = mvc.perform(get("/escalated/admin/plugins").with(user(admin.getEmail()))
                            .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                    .andExpect(status().isOk()).andReturn();
            JsonNode page = JSON.readTree(result.getResponse().getContentAsString());
            assertThat(page.get("props").get("plugins")).isEmpty();
            assertThat(page.get("props").get("escalated").get("plugins").size()).isZero();

            assertThat(tickets.create("No runtime", "Body", "Casey", "casey@example.com", null, null).getId())
                    .isNotNull();
            assertThat(registry.applyFilter("subject.filter", "x")).isEqualTo("x");
            mvc.perform(get("/escalated/api/plugins/acme--sdk-crm/echo/1").with(user(admin.getEmail())))
                    .andExpect(status().isNotFound());
        }
    }

    /** A runtime without HTTP contract 1: its plugins' endpoints answer 503. */
    @Nested
    @SpringBootTest(classes = HostApplication.class)
    @DirtiesContext
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
        "spring.mail.host=localhost",
        "escalated.ui.enabled=true",
        "escalated.ui.version=build-1",
        "escalated.plugins.runtime.enabled=true",
        "escalated.plugins.runtime.env[" + FakePluginRuntime.NO_HTTP + "]=1",
    })
    class WhenItCannotServeHttp {

        @DynamicPropertySource
        static void runtime(DynamicPropertyRegistry registry) {
            List<String> command = FakePluginRuntime.command();
            registry.add("escalated.plugins.runtime.command", () -> command.get(0));
            for (int i = 1; i < command.size(); i++) {
                String arg = command.get(i);
                registry.add("escalated.plugins.runtime.args[" + (i - 1) + "]", () -> arg);
            }
            registry.add("escalated.plugins.runtime.env[" + FakePluginRuntime.MANIFEST + "]",
                    () -> PluginRuntimeBridgeTest.MANIFEST);
        }

        @Autowired private MockMvc mvc;
        @Autowired private AgentProfileRepository agents;
        @Autowired private PluginActivationRepository activations;
        @Autowired private PluginRegistry registry;

        @BeforeEach
        void clean() {
            activations.deleteAll();
        }

        @Test
        void endpointsAnswer503() throws Exception {
            AgentProfile admin = admin(agents);
            registry.activate("acme--sdk-crm", null);
            mvc.perform(post("/escalated/api/plugins/acme--sdk-crm/echo/1").with(user(admin.getEmail()))
                            .header("X-Requested-With", "XMLHttpRequest")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isServiceUnavailable());
            mvc.perform(post("/escalated/webhooks/plugins/acme--sdk-crm/echo/1")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isServiceUnavailable());
        }
    }
}
