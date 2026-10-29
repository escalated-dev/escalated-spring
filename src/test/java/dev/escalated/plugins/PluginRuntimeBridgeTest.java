package dev.escalated.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.PluginActivationRepository;
import dev.escalated.repositories.PluginStoreRecordRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import pluginfixtures.StandInPlugins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * SDK plugins over the real ProcessBuilder bridge, against a Java stand-in for the Node runtime
 * ({@link FakePluginRuntime}): listing, the activate and deactivate calls, pages, endpoints and
 * webhooks through the runtime, ctx scoping, domain events with the active allow-list, filters, and
 * the ctx domain calls acting as the signed-in user.
 */
@SpringBootTest(classes = HostApplication.class)
@DirtiesContext
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.plugins.runtime.enabled=true",
})
class PluginRuntimeBridgeTest {

    static final String MANIFEST = """
            {
              "@acme/sdk-crm": {
                "name": "@acme/sdk-crm", "version": "3.0.0", "description": "CRM sync",
                "pages": [{"route": "crm", "component": "Settings", "menu": {"label": "CRM"}}],
                "components": [], "widgets": [{"component": "Pipeline", "label": "Pipeline", "hasBadge": false}],
                "actionHooks": ["ticket.created", "plugin.activated"],
                "filterHooks": [{"hook": "subject.filter", "priority": 1}],
                "endpoints": [
                  {"method": "GET", "path": "/echo/:id"}, {"method": "POST", "path": "/echo/:id"},
                  {"method": "GET", "path": "/boom"}, {"method": "GET", "path": "/Settings"},
                  {"method": "GET", "path": "/spy"}, {"method": "POST", "path": "/ctx"},
                  {"method": "POST", "path": "/ctx-together"}
                ],
                "webhooks": [{"method": "POST", "path": "/echo/:id"}],
                "cronSchedules": []
              },
              "@acme/sdk-other": {
                "name": "@acme/sdk-other", "version": "1.0.0",
                "actionHooks": ["ticket.created"], "endpoints": [{"method": "GET", "path": "/spy"}]
              }
            }
            """;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CRM = "acme--sdk-crm";
    private static final String OTHER = "acme--sdk-other";
    private static final String INDEX = "/escalated/admin/plugins";

    @DynamicPropertySource
    static void runtime(DynamicPropertyRegistry registry) {
        List<String> command = FakePluginRuntime.command();
        registry.add("escalated.plugins.runtime.command", () -> command.get(0));
        for (int i = 1; i < command.size(); i++) {
            String arg = command.get(i);
            registry.add("escalated.plugins.runtime.args[" + (i - 1) + "]", () -> arg);
        }
        registry.add("escalated.plugins.runtime.env[" + FakePluginRuntime.MANIFEST + "]", () -> MANIFEST);
    }

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private PluginActivationRepository activations;
    @Autowired private PluginStoreRecordRepository store;
    @Autowired private PluginRegistry registry;
    @Autowired private TicketService tickets;
    @Autowired private TicketRepository ticketRows;

    private String unique;
    private AgentProfile admin;

    @BeforeEach
    void seed() {
        activations.deleteAll();
        store.deleteAll();
        unique = UUID.randomUUID().toString().substring(0, 8);
        admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin = agents.save(admin);
    }

    static RequestPostProcessor xsrf() {
        return request -> {
            String token = UUID.randomUUID().toString();
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.with(user(admin.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode lifecycle(String path, String flash) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequestBuilder request = path.endsWith("activate") ? post(INDEX + path) : delete(INDEX + path);
        mvc.perform(request.session(session).with(user(admin.getEmail())).with(xsrf())
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isSeeOther());
        JsonNode page = inertia(get(INDEX).session(session));
        assertThat(page.get("props").get("flash").get(flash).isTextual()).as(path).isTrue();
        return page;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        MvcResult result = mvc.perform(request.with(user(admin.getEmail())).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().is(expected)).andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isEmpty() ? null : JSON.readTree(body);
    }

    private static void eventually(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting for " + what);
            }
            Thread.sleep(50);
        }
    }

    private List<Map<String, Object>> rows(String slug, String collection) {
        return registry.contextFor(slug).store().query(collection, Map.of(), null);
    }

    @Test
    void sdkPluginsAreListedAsPackageManagedAndCannotBeUninstalled() throws Exception {
        JsonNode page = inertia(get(INDEX));
        JsonNode crm = null;
        for (JsonNode plugin : page.get("props").get("plugins")) {
            if (CRM.equals(plugin.get("slug").asText())) {
                crm = plugin;
            }
        }
        assertThat(crm).isNotNull();
        assertThat(crm.get("name").asText()).isEqualTo("@acme/sdk-crm");
        assertThat(crm.get("version").asText()).isEqualTo("3.0.0");
        assertThat(crm.get("source").asText()).isEqualTo("composer");
        assertThat(crm.get("provides").get("menu_items").asInt()).isEqualTo(1);
        assertThat(crm.get("provides").get("widgets").asInt()).isEqualTo(1);

        lifecycle("/" + CRM + "/activate", "success");
        eventually("the activate call", () -> rows(CRM, "lifecycle").stream()
                .anyMatch(row -> "activate".equals(row.get("event"))));

        JsonNode refused = lifecycle("/" + CRM, "error");
        assertThat(refused.get("props").get("flash").get("error").asText()).contains("package manager");
        assertThat(activations.findBySlug(CRM).orElseThrow().isActive()).isTrue();

        lifecycle("/" + CRM + "/deactivate", "success");
        assertThat(rows(CRM, "lifecycle").stream().map(row -> row.get("event")).toList())
                .containsExactly("activate", "deactivate");
    }

    @Test
    void endpointsWebhooksAndPagesGoThroughTheRuntimeForActivePluginsOnly() throws Exception {
        call(get("/escalated/api/plugins/" + CRM + "/echo/1"), 404);
        lifecycle("/" + CRM + "/activate", "success");

        JsonNode echo = call(post("/escalated/api/plugins/" + CRM + "/echo/42?q=1").cookie(new Cookie("S", "x"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"hello\":\"world\"}"), 200);
        assertThat(echo.get("kind").asText()).isEqualTo("endpoint");
        assertThat(echo.get("path").asText()).isEqualTo("/echo/:id");
        assertThat(echo.get("params").get("id").asText()).isEqualTo("42");
        assertThat(echo.get("query").get("q").asText()).isEqualTo("1");
        assertThat(echo.get("body").get("hello").asText()).isEqualTo("world");
        assertThat(echo.get("httpContract").asInt()).isEqualTo(1);
        assertThat(new String(Base64.getDecoder().decode(echo.get("rawBodyBase64").asText()), StandardCharsets.UTF_8))
                .isEqualTo("{\"hello\":\"world\"}");
        assertThat(echo.get("headers").has("cookie")).isFalse();

        MvcResult webhook = mvc.perform(post("/escalated/webhooks/plugins/" + CRM + "/echo/9")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(JSON.readTree(webhook.getResponse().getContentAsString()).get("kind").asText()).isEqualTo("webhook");

        MvcResult boom = mvc.perform(get("/escalated/api/plugins/" + CRM + "/boom").with(user(admin.getEmail())))
                .andExpect(status().isInternalServerError()).andReturn();
        assertThat(boom.getResponse().getContentAsString()).doesNotContain("secret failure detail");

        JsonNode page = inertia(get(INDEX + "/" + CRM + "/crm"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Plugin/Page");
        assertThat(page.get("props").get("plugin").asText()).isEqualTo("@acme/sdk-crm");
        assertThat(page.get("props").get("props").get("greeting").asText()).isEqualTo("hello from @acme/sdk-crm");

        call(get("/escalated/api/plugins/" + OTHER + "/spy"), 404);
        JsonNode plugins = inertia(get(INDEX)).get("props").get("escalated").get("plugins");
        assertThat(plugins.get("menuItems").get(0).get("route").asText()).isEqualTo(INDEX + "/" + CRM + "/crm");
        assertThat(plugins.get("dashboardWidgets").get(0).get("id").asText()).isEqualTo(CRM + ".Pipeline");
    }

    @Test
    void aCtxCallActsOnlyForThePluginBeingServed() throws Exception {
        lifecycle("/" + CRM + "/activate", "success");
        lifecycle("/" + OTHER + "/activate", "success");
        registry.contextFor(OTHER).store().set("secrets", "token", "other-secret");

        JsonNode spy = call(get("/escalated/api/plugins/" + CRM + "/spy?as=@acme/sdk-other"), 200);
        assertThat(spy.has("error")).isTrue();
        assertThat(spy.toString()).doesNotContain("other-secret");
    }

    @Test
    void ticketEventsReachActiveSdkPluginsWithTheActiveAllowList() throws Exception {
        lifecycle("/" + CRM + "/activate", "success");
        Ticket ticket = tickets.create("SDK " + unique, "Body", "Casey", "casey-" + unique + "@example.com", null, null);

        eventually("ticket.created in the runtime", () -> rows(CRM, "received").stream()
                .anyMatch(row -> "ticket.created".equals(row.get("hook"))
                        && ticket.getId().toString().equals(String.valueOf(
                                StandInPlugins.record(row.get("event")).get("id")))));
        Map<String, Object> received = rows(CRM, "received").stream()
                .filter(row -> "ticket.created".equals(row.get("hook"))).findFirst().orElseThrow();
        assertThat(received.get("plugins")).isEqualTo(List.of("@acme/sdk-crm"));
        assertThat(StandInPlugins.record(StandInPlugins.record(received.get("event")).get("ticket")).get("reference"))
                .isEqualTo(ticket.getTicketNumber());
        // The inactive plugin's own write was refused: no dispatch was serving it.
        assertThat(rows(OTHER, "received")).isEmpty();
    }

    @Test
    void theRuntimesCtxCallsWriteThroughTheTicketServiceAsTheSignedInUser() throws Exception {
        lifecycle("/" + CRM + "/activate", "success");

        JsonNode created = call(post("/escalated/api/plugins/" + CRM + "/ctx").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"ctx.tickets.create\",\"params\":{\"data\":{\"title\":\"From CRM " + unique
                        + "\",\"body\":\"Synced\",\"requester_email\":\"lead-" + unique + "@example.com\","
                        + "\"requester_name\":\"Lead\",\"status\":\"pending\"}}}"), 200);
        assertThat(created.has("error")).as(created.toString()).isFalse();
        JsonNode ticket = created.get("result");
        assertThat(ticket.get("subject").asText()).isEqualTo("From CRM " + unique);
        assertThat(ticket.get("channel").asText()).isEqualTo("api");
        assertThat(ticket.get("status").asText()).isEqualTo("pending");
        Ticket stored = ticketRows.findById(ticket.get("id").asLong()).orElseThrow();
        assertThat(stored.getRequesterEmail()).isEqualTo("lead-" + unique + "@example.com");
        // The create fired ticket.created, which the runtime handles for this plugin in the background.
        // While that dispatch overlaps a request's, the plugin's ctx calls act as no user (docs/plugins.md).
        eventually("ticket.created for the ctx-created ticket", () -> rows(CRM, "received").stream()
                .anyMatch(row -> stored.getId().toString().equals(String.valueOf(
                        StandInPlugins.record(row.get("event")).get("id")))));
        Thread.sleep(200);

        JsonNode me = call(post("/escalated/api/plugins/" + CRM + "/ctx").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"ctx.currentUser\"}"), 200);
        assertThat(me.get("result").get("email").asText()).isEqualTo(admin.getEmail());

        JsonNode reply = call(post("/escalated/api/plugins/" + CRM + "/ctx").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"ctx.replies.create\",\"params\":{\"data\":{\"ticket_id\":" + stored.getId()
                        + ",\"body\":\"Noted\",\"is_internal\":true}}}"), 200);
        assertThat(reply.get("result").get("type").asText()).isEqualTo("note");
        assertThat(reply.get("result").get("author_name").asText()).isEqualTo(admin.getName());

        JsonNode bad = call(post("/escalated/api/plugins/" + CRM + "/ctx").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"ctx.tickets.query\",\"params\":{\"filter\":{\"subject; DROP TABLE x\":1}}}"), 200);
        assertThat(bad.get("error").asText()).contains("cannot filter");
        JsonNode found = call(post("/escalated/api/plugins/" + CRM + "/ctx").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"ctx.tickets.query\",\"params\":{\"filter\":{\"reference\":\""
                        + stored.getTicketNumber() + "\"}}}"), 200);
        assertThat(found.get("result")).hasSize(1);
    }

    @Test
    void ctxCallsInFlightAtOnceEachActAsTheirOwnRequestsUser() throws Exception {
        lifecycle("/" + CRM + "/activate", "success");
        AgentProfile second = new AgentProfile();
        second.setName("Second " + unique);
        second.setEmail("second-" + unique + "@example.com");
        second.setAgent(true);
        second.setAdmin(true);
        second = agents.save(second);
        Ticket ticket = tickets.create("Together " + unique, "Body", "Casey", "casey-" + unique + "@example.com",
                null, null);

        Map<String, JsonNode> who = together(List.of(admin, second), "{\"method\":\"ctx.currentUser\"}");
        assertThat(who.get(admin.getEmail()).path("result").path("email").asText()).isEqualTo(admin.getEmail());
        assertThat(who.get(second.getEmail()).path("result").path("email").asText()).isEqualTo(second.getEmail());

        Map<String, JsonNode> wrote = together(List.of(admin, second),
                "{\"method\":\"ctx.replies.create\",\"params\":{\"data\":{\"ticket_id\":" + ticket.getId()
                        + ",\"body\":\"Noted\",\"is_internal\":true}}}");
        assertThat(wrote.get(admin.getEmail()).path("result").path("author_name").asText()).isEqualTo(admin.getName());
        assertThat(wrote.get(second.getEmail()).path("result").path("author_name").asText()).isEqualTo(second.getName());
    }

    /** POSTs {@code body} to {@code /ctx-together} as each agent at once; the answers by email. */
    private Map<String, JsonNode> together(List<AgentProfile> who, String body) throws Exception {
        Map<String, JsonNode> out = new java.util.concurrent.ConcurrentHashMap<>();
        List<Thread> threads = new java.util.ArrayList<>();
        List<Throwable> failures = new java.util.concurrent.CopyOnWriteArrayList<>();
        for (AgentProfile agent : who) {
            Thread thread = new Thread(() -> {
                try {
                    MvcResult result = mvc.perform(post("/escalated/api/plugins/" + CRM + "/ctx-together")
                                    .with(user(agent.getEmail())).header("X-Requested-With", "XMLHttpRequest")
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andExpect(status().isOk()).andReturn();
                    out.put(agent.getEmail(), JSON.readTree(result.getResponse().getContentAsString()));
                } catch (Throwable ex) {
                    failures.add(ex);
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join(30_000);
        }
        assertThat(failures).isEmpty();
        return out;
    }

    @Test
    void sdkFiltersRunThroughTheRuntimeForActivePlugins() throws Exception {
        assertThat(registry.applyFilter("subject.filter", "x")).isEqualTo("x");
        lifecycle("/" + CRM + "/activate", "success");
        assertThat(registry.applyFilter("subject.filter", "x")).isEqualTo("x|sdk");
    }
}
