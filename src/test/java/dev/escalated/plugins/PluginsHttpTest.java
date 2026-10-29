package dev.escalated.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.events.EventNames;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.PluginActivation;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.PluginActivationRepository;
import dev.escalated.repositories.PluginStoreRecordRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import pluginfixtures.StandInPlugins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The plugin contract's port test checklist (escalated-developer-context
 * {@code domain-model/plugin-contract.md}) through the real HTTP stack, with native stand-in
 * plugins ({@link StandInPlugins}). The SDK bridge has its own test, {@link PluginRuntimeBridgeTest}.
 */
@SpringBootTest(classes = HostApplication.class)
@DirtiesContext
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StandInPlugins.class)
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
})
class PluginsHttpTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BILLING = "acme--billing";
    private static final String SECOND = "acme--second";
    private static final String INDEX = "/escalated/admin/plugins";

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private PluginActivationRepository activations;
    @Autowired private PluginStoreRecordRepository store;
    @Autowired private PluginRegistry registry;
    @Autowired private TicketService tickets;
    @Autowired private TicketRepository ticketRows;

    private String unique;
    private AgentProfile admin;
    private AgentProfile agent;

    @BeforeEach
    void seed() {
        activations.deleteAll();
        store.deleteAll();
        StandInPlugins.reset();
        unique = UUID.randomUUID().toString().substring(0, 8);
        admin = profile("admin-" + unique + "@example.com", true);
        agent = profile("agent-" + unique + "@example.com", false);
    }

    private AgentProfile profile(String email, boolean isAdmin) {
        AgentProfile profile = new AgentProfile();
        profile.setName(email.substring(0, email.indexOf('@')));
        profile.setEmail(email);
        profile.setAgent(true);
        profile.setAdmin(isAdmin);
        return agents.save(profile);
    }

    /** An Inertia visit from a client on the current asset version. */
    private static final RequestPostProcessor INERTIA = request -> {
        request.addHeader("X-Inertia", "true");
        request.addHeader("X-Inertia-Version", "build-1");
        return request;
    };

    private static RequestPostProcessor xsrf() {
        return request -> {
            String token = UUID.randomUUID().toString();
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private void lifecycle(MockHttpSession session, String method, String slug, String flash) throws Exception {
        MockHttpServletRequestBuilder request = "delete".equals(method)
                ? delete(INDEX + "/" + slug)
                : post(INDEX + "/" + slug + "/" + method);
        mvc.perform(request.session(session).with(user(admin.getEmail())).with(xsrf()).with(INERTIA))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", INDEX));
        JsonNode page = inertia(get(INDEX).session(session).with(user(admin.getEmail())));
        assertThat(page.get("props").get("flash").get(flash).isTextual()).as(method + " " + slug + " flash").isTrue();
    }

    private void activate(String slug) throws Exception {
        lifecycle(new MockHttpSession(), "activate", slug, "success");
    }

    private static void eventually(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting for " + what);
            }
            Thread.sleep(50);
        }
    }

    private JsonNode plugin(JsonNode page, String slug) {
        for (JsonNode plugin : page.get("props").get("plugins")) {
            if (slug.equals(plugin.get("slug").asText())) {
                return plugin;
            }
        }
        throw new AssertionError(slug + " is not listed");
    }

    // ---- Admin screen

    @Test
    void theIndexListsEveryRegisteredPluginForAnAdmin() throws Exception {
        JsonNode page = inertia(get(INDEX).with(user(admin.getEmail())));

        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Plugins/Index");
        assertThat(page.get("props").get("can_upload").asBoolean(true)).isFalse();
        JsonNode billing = plugin(page, BILLING);
        assertThat(billing.get("name").asText()).isEqualTo("acme/billing");
        assertThat(billing.get("version").asText()).isEqualTo("2.1.0");
        assertThat(billing.get("description").asText()).isEqualTo("Invoices on tickets");
        assertThat(billing.get("author").asText()).isEqualTo("Acme");
        assertThat(billing.get("author_url").asText()).isEqualTo("https://acme.example");
        assertThat(billing.get("homepage").asText()).isEqualTo("https://acme.example");
        assertThat(billing.get("is_active").asBoolean(true)).isFalse();
        assertThat(billing.get("activated_at").isNull()).isTrue();
        assertThat(billing.get("source").asText()).isEqualTo("registered");
        assertThat(billing.get("provides").get("menu_items").asInt()).isEqualTo(3);
        assertThat(billing.get("provides").get("widgets").asInt()).isEqualTo(1);
        assertThat(billing.get("provides").get("page_components").asInt()).isEqualTo(1);
        assertThat(plugin(page, SECOND).get("description").isNull()).isTrue();
    }

    @Test
    void agentsCustomersAndGuestsAreRefused() throws Exception {
        for (RequestPostProcessor who : List.of(user(agent.getEmail()), user("customer-" + unique + "@example.com"))) {
            MvcResult result = mvc.perform(get(INDEX).with(who).with(INERTIA)).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("Plugins/Index");
            mvc.perform(post(INDEX + "/" + BILLING + "/activate").with(who).with(xsrf()).with(INERTIA))
                    .andExpect(status().isForbidden());
        }
        MvcResult guest = mvc.perform(get(INDEX).with(INERTIA)).andReturn();
        assertThat(guest.getResponse().getStatus()).isNotEqualTo(200);
        assertThat(guest.getResponse().getContentAsString()).doesNotContain("Plugins/Index");
        assertThat(activations.findBySlug(BILLING)).isEmpty();
    }

    @Test
    void activatingAndDeactivatingRedirectWithAFlashAndAreIdempotent() throws Exception {
        MockHttpSession session = new MockHttpSession();
        lifecycle(session, "activate", BILLING, "success");
        PluginActivation row = activations.findBySlug(BILLING).orElseThrow();
        assertThat(row.isActive()).isTrue();
        assertThat(row.getActivatedAt()).isNotNull();
        assertThat(row.getDeactivatedAt()).isNull();
        assertThat(plugin(inertia(get(INDEX).with(user(admin.getEmail()))), BILLING).get("is_active").asBoolean())
                .isTrue();

        lifecycle(session, "activate", BILLING, "success");
        assertThat(activations.findBySlug(BILLING).orElseThrow().getActivatedAt()).isEqualTo(row.getActivatedAt());
        assertThat(StandInPlugins.CALLS.stream().filter("acme/billing onActivate"::equals).count()).isEqualTo(1);

        lifecycle(session, "deactivate", BILLING, "success");
        PluginActivation off = activations.findBySlug(BILLING).orElseThrow();
        assertThat(off.isActive()).isFalse();
        assertThat(off.getDeactivatedAt()).isNotNull();
        lifecycle(session, "deactivate", BILLING, "success");
        assertThat(StandInPlugins.CALLS.stream().filter("acme/billing onDeactivate"::equals).count()).isEqualTo(1);
    }

    @Test
    void anUnknownSlugIsAnErrorFlashOrA404ForJsonAndCreatesNoRow() throws Exception {
        lifecycle(new MockHttpSession(), "activate", "nope", "error");
        mvc.perform(post(INDEX + "/nope/activate").with(user(admin.getEmail())).with(xsrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        mvc.perform(delete(INDEX + "/nope").with(user(admin.getEmail())).with(xsrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        assertThat(activations.findBySlug("nope")).isEmpty();
    }

    @Test
    void uninstallingARegisteredPluginForgetsItsRowButKeepsItListedAndItsStore() throws Exception {
        activate(BILLING);
        registry.contextFor(BILLING).store().set("settings", "token", Map.of("value", "kept"));
        StandInPlugins.CALLS.clear();

        lifecycle(new MockHttpSession(), "delete", BILLING, "success");

        assertThat(activations.findBySlug(BILLING)).isEmpty();
        JsonNode listed = plugin(inertia(get(INDEX).with(user(admin.getEmail()))), BILLING);
        assertThat(listed.get("is_active").asBoolean(true)).isFalse();
        assertThat(registry.contextFor(BILLING).store().get("settings", "token")).isEqualTo(Map.of("value", "kept"));
        assertThat(StandInPlugins.CALLS).containsSubsequence(
                "acme/billing plugin.uninstalling", "acme/billing plugin.deactivated", "acme/billing onDeactivate");
    }

    @Test
    void thereIsNoUploadRoute() throws Exception {
        mvc.perform(post(INDEX + "/upload").with(user(admin.getEmail())).with(xsrf()).with(INERTIA))
                .andExpect(status().isNotFound());
    }

    @Test
    void theLifecycleRunsInTheContractsOrderAndAThrowingCallbackUndoesNothing() throws Exception {
        activate(BILLING);
        assertThat(StandInPlugins.CALLS).containsExactly("acme/billing onActivate", "acme/billing plugin.activated");

        StandInPlugins.CALLS.clear();
        activate(SECOND);
        assertThat(activations.findBySlug(SECOND).orElseThrow().isActive()).isTrue();
        // The subject hears its own activation, as does every other active plugin.
        assertThat(StandInPlugins.CALLS).containsExactlyInAnyOrder("acme/second onActivate",
                "acme/billing plugin.activated", "acme/second plugin.activated");
        assertThat(StandInPlugins.CALLS.get(0)).isEqualTo("acme/second onActivate");

        StandInPlugins.CALLS.clear();
        lifecycle(new MockHttpSession(), "deactivate", BILLING, "success");
        assertThat(StandInPlugins.CALLS).containsSubsequence("acme/billing plugin.deactivated", "acme/billing onDeactivate");
        Map<String, Object> event = StandInPlugins.events("acme/second", "plugin.deactivated").get(0);
        assertThat(StandInPlugins.record(event.get("event"))).isEqualTo(Map.of("plugin", "acme/billing", "slug", BILLING));
    }

    // ---- Pages, endpoints, webhooks

    @Test
    void anActivePluginsPageRendersWithThePropsOfItsOwnEndpoint() throws Exception {
        activate(BILLING);

        JsonNode page = inertia(get(INDEX + "/" + BILLING + "/settings").with(user(admin.getEmail())));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Plugin/Page");
        assertThat(page.get("props").get("plugin").asText()).isEqualTo("acme/billing");
        assertThat(page.get("props").get("component").asText()).isEqualTo("Settings");
        assertThat(page.get("props").get("layout").asText()).isEqualTo("admin");
        assertThat(page.get("props").get("props").get("greeting").asText()).isEqualTo("hello from acme/billing");

        JsonNode plain = inertia(get(INDEX + "/" + BILLING + "/plain").with(user(admin.getEmail())));
        assertThat(plain.get("props").get("props").isObject()).isTrue();
        assertThat(plain.get("props").get("props").size()).isZero();
        assertThat(inertia(get(INDEX + "/" + BILLING + "/agent-view").with(user(admin.getEmail())))
                .get("props").get("layout").asText()).isEqualTo("agent");
    }

    @Test
    void anInactivePluginsPageIsA404AndNonAdminsAreRefused() throws Exception {
        mvc.perform(get(INDEX + "/" + BILLING + "/settings").with(user(admin.getEmail())).with(INERTIA))
                .andExpect(status().isNotFound());
        activate(BILLING);
        mvc.perform(get(INDEX + "/" + BILLING + "/settings").with(user(agent.getEmail())).with(INERTIA))
                .andExpect(status().isForbidden());
        mvc.perform(get(INDEX + "/" + BILLING + "/missing").with(user(admin.getEmail())).with(INERTIA))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCapabilityTheUserLacksIsA403OnPagesAndEndpoints() throws Exception {
        activate(BILLING);
        mvc.perform(get(INDEX + "/" + BILLING + "/secret").with(user(admin.getEmail())).with(INERTIA))
                .andExpect(status().isForbidden());
        mvc.perform(get("/escalated/api/plugins/" + BILLING + "/secure").with(user(admin.getEmail())))
                .andExpect(status().isForbidden());

        AgentProfile boss = profile("boss-" + unique + "@example.com", true);
        inertia(get(INDEX + "/" + BILLING + "/secret").with(user(boss.getEmail())));
        mvc.perform(get("/escalated/api/plugins/" + BILLING + "/secure").with(user(boss.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void anEndpointGetsItsParamsQueryAndBodyAndNeverTheCredentials() throws Exception {
        activate(BILLING);
        JsonNode got = json(mvc.perform(get("/escalated/api/plugins/" + BILLING + "/items/42?expand=lines&x=1&x=2")
                        .with(user(admin.getEmail()))
                        .cookie(new Cookie("SESSION", "secret-session"))
                        .header("Authorization", "Basic c2VjcmV0")
                        .header("X-XSRF-TOKEN", "t").header("X-CSRF-TOKEN", "t").header("Proxy-Authorization", "p")
                        .header("X-Custom", "kept"))
                .andExpect(status().isOk()).andReturn());
        assertThat(got.get("path").asText()).isEqualTo("/items/:id");
        assertThat(got.get("params").get("id").asText()).isEqualTo("42");
        assertThat(got.get("query").get("expand").asText()).isEqualTo("lines");
        assertThat(got.get("query").get("x").asText()).isEqualTo("1,2");
        Set<String> headers = new HashSet<>();
        got.get("headers").fieldNames().forEachRemaining(headers::add);
        assertThat(headers).contains("x-custom")
                .doesNotContain("cookie", "authorization", "x-xsrf-token", "x-csrf-token", "proxy-authorization");

        JsonNode posted = json(mvc.perform(post("/escalated/api/plugins/" + BILLING + "/items/7")
                        .with(user(admin.getEmail())).header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":12}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(posted.get("params").get("id").asText()).isEqualTo("7");
        assertThat(posted.get("body").get("amount").asInt()).isEqualTo(12);
        assertThat(posted.get("raw").asText()).isEqualTo("{\"amount\":12}");

        // A state-changing call without a page script's header is refused.
        mvc.perform(post("/escalated/api/plugins/" + BILLING + "/items/7").with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // Endpoints are behind the admin guard.
        mvc.perform(get("/escalated/api/plugins/" + BILLING + "/items/1").with(user(agent.getEmail())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/escalated/api/plugins/" + BILLING + "/items/1")).andExpect(status().is4xxClientError());
        mvc.perform(get("/escalated/api/plugins/" + BILLING + "/nothing").with(user(admin.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEnvelopeChoosesTheStatusAndHeadersAndABadOneIsA502() throws Exception {
        activate(BILLING);
        mvc.perform(get("/escalated/api/plugins/" + BILLING + "/envelope").with(user(admin.getEmail())))
                .andExpect(status().isCreated())
                .andExpect(header().string("x-plugin", "yes"));
        mvc.perform(get("/escalated/api/plugins/" + BILLING + "/bad-header").with(user(admin.getEmail())))
                .andExpect(status().isBadGateway());
    }

    @Test
    void aWebhookAnswersWithoutAuthAndAnInactivePluginsIsA404() throws Exception {
        mvc.perform(post("/escalated/webhooks/plugins/" + BILLING + "/hook").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"a\":1}"))
                .andExpect(status().isNotFound());
        activate(BILLING);
        JsonNode got = json(mvc.perform(post("/escalated/webhooks/plugins/" + BILLING + "/hook")
                        .header("Authorization", "Bearer provider-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(got.get("body").get("a").asInt()).isEqualTo(1);
        assertThat(got.get("raw").asText()).isEqualTo("{\"a\":1}");
        assertThat(got.get("headers").has("authorization")).isFalse();
    }

    @Test
    void aThrowingEndpointIsA500WithoutTheMessage() throws Exception {
        activate(BILLING);
        MvcResult result = mvc.perform(get("/escalated/api/plugins/" + BILLING + "/boom").with(user(admin.getEmail())))
                .andExpect(status().isInternalServerError()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret failure detail");
    }

    // ---- Hooks and context

    @Test
    void creatingATicketOverHttpDeliversTicketCreatedToActivePluginsOnly() throws Exception {
        activate(BILLING);
        String customer = "customer-" + unique + "@example.com";
        mvc.perform(post("/escalated").with(user(customer)).with(xsrf()).with(INERTIA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Printer " + unique + "\",\"description\":\"Broken\",\"priority\":\"high\","
                                + "\"department_id\":\"\",\"ticket_type\":\"question\",\"attachments\":[]}"))
                .andExpect(status().isSeeOther());
        Ticket created = ticketRows.findAll().stream().filter(t -> t.getSubject().equals("Printer " + unique))
                .findFirst().orElseThrow();

        eventually("ticket.created", () -> StandInPlugins.events("acme/billing", "ticket.created").stream()
                .anyMatch(e -> created.getId().equals(Long.valueOf(String.valueOf(
                        StandInPlugins.record(e.get("event")).get("id"))))));
        Map<String, Object> event = StandInPlugins.record(StandInPlugins.events("acme/billing", "ticket.created").stream()
                .filter(e -> created.getId().toString().equals(String.valueOf(StandInPlugins.record(e.get("event")).get("id"))))
                .findFirst().orElseThrow().get("event"));
        assertThat(event.get("reference")).isEqualTo(created.getTicketNumber());
        assertThat(event.get("subject")).isEqualTo("Printer " + unique);
        assertThat(event.get("title")).isEqualTo("Printer " + unique);
        assertThat(event.get("status")).isEqualTo("open");
        assertThat(event.get("priority")).isEqualTo("high");
        assertThat(event).containsKeys("department_id", "assigned_to", "requester_id", "metadata", "created_at",
                "updated_at");
        Map<String, Object> ticket = StandInPlugins.record(event.get("ticket"));
        assertThat(ticket.get("id")).isEqualTo(event.get("id"));
        assertThat(ticket.get("reference")).isEqualTo(created.getTicketNumber());
        assertThat(StandInPlugins.events("acme/second", "ticket.created")).isEmpty();
    }

    @Test
    void aReplyDeliversReplyCreatedWithReplyAndTicketAndNoLegacyName() throws Exception {
        activate(BILLING);
        activate(SECOND);
        Ticket ticket = tickets.create("Reply " + unique, "Body", "Casey", "casey-" + unique + "@example.com", null, null);
        String show = "/escalated/agent/tickets/" + ticket.getTicketNumber();
        mvc.perform(post(show + "/reply").with(user(agent.getEmail())).with(xsrf()).with(INERTIA)
                        .header("Referer", "http://localhost" + show).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"On it " + unique + "\",\"is_internal_note\":false,\"attachments\":[]}"))
                .andExpect(status().isSeeOther());
        mvc.perform(post(show + "/note").with(user(agent.getEmail())).with(xsrf()).with(INERTIA)
                        .header("Referer", "http://localhost" + show).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Internal " + unique + "\"}"))
                .andExpect(status().isSeeOther());

        eventually("reply.created", () -> !StandInPlugins.events("acme/billing", "reply.created").isEmpty());
        eventually("note.created", () -> !StandInPlugins.events("acme/billing", "note.created").isEmpty());
        Map<String, Object> event = StandInPlugins.record(
                StandInPlugins.events("acme/billing", "reply.created").get(0).get("event"));
        assertThat(event.get("body")).isEqualTo("On it " + unique);
        assertThat(event.get("is_internal")).isEqualTo(false);
        assertThat(StandInPlugins.record(event.get("reply")).get("id")).isEqualTo(event.get("id"));
        assertThat(StandInPlugins.record(event.get("ticket")).get("reference")).isEqualTo(ticket.getTicketNumber());
        assertThat(StandInPlugins.record(StandInPlugins.events("acme/billing", "note.created").get(0).get("event"))
                .get("is_internal")).isEqualTo(true);

        Set<String> allowed = new HashSet<>(EventNames.CANONICAL);
        allowed.addAll(PluginRegistry.LIFECYCLE_ACTIONS);
        synchronized (StandInPlugins.EVENTS) {
            assertThat(StandInPlugins.EVENTS).allSatisfy(e -> assertThat(allowed).contains((String) e.get("hook")));
        }
    }

    @Test
    void aThrowingActionDoesNotFailTheRequestThatFiredIt() throws Exception {
        activate(SECOND);
        activate(BILLING);
        String customer = "customer-" + unique + "@example.com";
        mvc.perform(post("/escalated").with(user(customer)).with(xsrf()).with(INERTIA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Throw " + unique + "\",\"description\":\"x\",\"priority\":\"low\","
                                + "\"department_id\":\"\",\"attachments\":[]}"))
                .andExpect(status().isSeeOther());
        eventually("both plugins to hear ticket.created", () -> StandInPlugins.CALLS.contains("acme/second ticket.created")
                && !StandInPlugins.events("acme/billing", "ticket.created").isEmpty());
        assertThat(ticketRows.findAll().stream().anyMatch(t -> t.getSubject().equals("Throw " + unique))).isTrue();
    }

    @Test
    void filtersRunByPriorityForActivePluginsOnlyAndAThrowingOneIsSkipped() throws Exception {
        assertThat(registry.applyFilter("subject.filter", "x")).isEqualTo("x");
        activate(BILLING);
        assertThat(registry.applyFilter("subject.filter", "x")).isEqualTo("x|billing");
        activate(SECOND);
        assertThat(registry.applyFilter("subject.filter", "x")).isEqualTo("x|second|billing");
        lifecycle(new MockHttpSession(), "deactivate", BILLING, "success");
        assertThat(registry.applyFilter("subject.filter", "x")).isEqualTo("x|second");
    }

    @Test
    void theContextsConfigMergesTheStoreQueriesAndPluginsCannotReadEachOther() throws Exception {
        PluginContext billing = registry.contextFor(BILLING);
        PluginContext second = registry.contextFor(SECOND);
        billing.config().set(Map.of("api_key", "k1", "region", "eu"));
        billing.config().set(Map.of("region", "us"));
        assertThat(billing.config().all()).isEqualTo(Map.of("api_key", "k1", "region", "us"));
        assertThat(second.config().all()).isEmpty();

        billing.store().insert("invoices", Map.of("key", "a", "amount", 5, "tags", List.of("x", "y"), "meta", Map.of("n", 1)));
        billing.store().insert("invoices", Map.of("key", "b", "amount", 15, "tags", List.of("y")));
        billing.store().insert("invoices", Map.of("key", "c", "amount", 25, "status", "paid"));
        assertThat(keys(billing.store().query("invoices", Map.of("amount", Map.of("$gt", 5)), null))).containsExactly("b", "c");
        assertThat(keys(billing.store().query("invoices", Map.of("amount", Map.of("$gte", 5, "$lt", 25)), null)))
                .containsExactly("a", "b");
        assertThat(keys(billing.store().query("invoices", Map.of("amount", Map.of("$lte", 15)), null)))
                .containsExactly("a", "b");
        assertThat(keys(billing.store().query("invoices", Map.of("key", Map.of("$ne", "a")), null))).containsExactly("b", "c");
        assertThat(keys(billing.store().query("invoices", Map.of("key", Map.of("$in", List.of("a", "c"))), null)))
                .containsExactly("a", "c");
        assertThat(keys(billing.store().query("invoices", Map.of("key", Map.of("$nin", List.of("a", "c"))), null)))
                .containsExactly("b");
        assertThat(keys(billing.store().query("invoices", Map.of("tags", "y"), null))).containsExactly("a", "b");
        assertThat(keys(billing.store().query("invoices", Map.of("meta.n", 1), null))).containsExactly("a");
        assertThat(keys(billing.store().query("invoices", Map.of(), Map.of("orderBy", "amount", "order", "desc", "limit", 2))))
                .containsExactly("c", "b");
        assertThat(billing.store().query("invoices", Map.of("key", "a"), null).get(0)).containsKey("_id");
        assertThatThrownBy(() -> billing.store().query("invoices", Map.of("amount) OR 1=1 --", 1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> billing.store().query("invoices", Map.of("amount", Map.of("$where", "1")), null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(billing.store().update("invoices", "a", Map.of("status", "void")))
                .containsEntry("status", "void").containsEntry("amount", 5);
        billing.store().set("secrets", "token", "s3cret");
        assertThat(second.store().get("secrets", "token")).isNull();
        assertThat(second.store().query("invoices", Map.of(), null)).isEmpty();
        billing.store().delete("invoices", "a");
        assertThat(keys(billing.store().query("invoices", Map.of(), null))).containsExactly("b", "c");
    }

    private static List<String> keys(List<Map<String, Object>> records) {
        return records.stream().map(r -> String.valueOf(r.get("key"))).toList();
    }

    @Test
    void escalatedPluginsCarriesTheActivePluginsMenusSlotsAndWidgets() throws Exception {
        JsonNode none = inertia(get(INDEX).with(user(admin.getEmail()))).get("props").get("escalated").get("plugins");
        assertThat(none.isObject()).isTrue();
        assertThat(none.size()).isZero();

        activate(BILLING);
        JsonNode plugins = inertia(get(INDEX).with(user(admin.getEmail()))).get("props").get("escalated").get("plugins");
        JsonNode menu = plugins.get("menuItems");
        assertThat(menu).hasSize(2);
        assertThat(menu.get(0).get("label").asText()).isEqualTo("Billing");
        assertThat(menu.get(0).get("route").asText()).isEqualTo("/escalated/admin/plugins/" + BILLING + "/settings");
        assertThat(menu.get(0).get("icon").asText()).isEqualTo("credit-card");
        assertThat(menu.get(0).get("position").asInt()).isEqualTo(30);
        assertThat(menu.get(0).get("section").asText()).isEqualTo("admin");
        assertThat(menu.get(0).get("plugin").asText()).isEqualTo(BILLING);
        assertThat(menu.get(1).get("section").asText()).isEqualTo("agent");
        assertThat(menu.get(1).get("position").asInt()).isEqualTo(100);
        JsonNode slot = plugins.get("pageComponents").get("ticket.show.sidebar").get(0);
        assertThat(slot.get("plugin").asText()).isEqualTo(BILLING);
        assertThat(slot.get("component").asText()).isEqualTo("InvoiceCard");
        assertThat(slot.get("data").get("compact").asBoolean()).isTrue();
        assertThat(slot.get("position").asInt()).isEqualTo(100);
        JsonNode widget = plugins.get("dashboardWidgets").get(0);
        assertThat(widget.get("id").asText()).isEqualTo(BILLING + ".RevenueWidget");
        assertThat(widget.get("title").asText()).isEqualTo("Revenue");
        assertThat(widget.get("width").asText()).isEqualTo("half");
        assertThat(widget.get("position").asInt()).isEqualTo(5);

        JsonNode forAgent = inertia(get("/escalated/agent").with(user(agent.getEmail())))
                .get("props").get("escalated").get("plugins");
        assertThat(forAgent.get("menuItems")).hasSize(1);
        assertThat(forAgent.get("menuItems").get(0).get("section").asText()).isEqualTo("agent");
        assertThat(plugins.toString()).doesNotContain("api_key");
    }
}
