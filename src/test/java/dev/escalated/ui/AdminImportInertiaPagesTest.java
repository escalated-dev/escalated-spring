package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.contracts.ImportAdapter;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.ImportJob;
import dev.escalated.models.ImportSourceMap;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ImportJobRepository;
import dev.escalated.repositories.ImportSourceMapRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.AutomationRunner;
import dev.escalated.services.ImportContext;
import dev.escalated.services.ImportCredentials;
import dev.escalated.services.ImportRunner;
import dev.escalated.services.SlaService;
import hostapp.HostApplication;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The admin Import wizard the shared frontend drives
 * ({@code src/pages/Admin/Import/{Index,Connect,Mapping,Review,Progress}.vue}), on escalated-laravel's
 * paths and props ({@code Admin\ImportController}, {@code ImportService}, {@code ImportJob},
 * {@code ImportContext}). The source is {@link FakeImportAdapter}, a bean as a host or plugin
 * library registers a Zendesk or Freshdesk adapter.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminImportInertiaPagesTest.Adapters.class)
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class AdminImportInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/escalated/admin/import";
    private static final String TOKEN = "secret-token";
    private static final Map<String, String> CREDENTIALS = Map.of("subdomain", "acme", "api_token", TOKEN);
    private static final String MAPPINGS = "{\"tickets\":{\"subject\":\"title\",\"state\":\"status\"}}";

    @TestConfiguration
    static class Adapters {
        @Bean
        FakeImportAdapter fakeImportAdapter() {
            return new FakeImportAdapter();
        }
    }

    /** Two contacts, a tag, three tickets two to a page, and two replies. */
    static class FakeImportAdapter implements ImportAdapter {
        final List<Object[]> calls = Collections.synchronizedList(new ArrayList<>());
        volatile BiConsumer<String, String> onExtract;
        volatile Map<String, String> sourceFieldCredentials;
        String agentEmail;

        private Map<String, List<Map<String, Object>>> records() {
            Map<String, List<Map<String, Object>>> records = new LinkedHashMap<>();
            records.put("agents", List.of(Map.of("source_id", "a1", "email", agentEmail, "name", "Agent")));
            records.put("contacts", List.of(
                    Map.of("source_id", "c1", "email", "ada@customer.test", "name", "Ada Customer"),
                    Map.of("source_id", "c2", "email", "bob@customer.test", "name", "Bob Customer")));
            records.put("tags", List.of(Map.of("source_id", "t1", "name", "Billing " + agentEmail)));
            records.put("tickets", List.of(
                    Map.of("source_id", "k1", "title", "Refund please", "description", "Charged twice.",
                            "status", "open", "priority", "high", "requester_source_id", "c1",
                            "assignee_source_id", "a1", "tag_source_ids", List.of("t1"),
                            "created_at", "2024-01-02T03:04:05Z"),
                    Map.of("source_id", "k2", "title", "Login broken", "status", "resolved", "priority", "urgent",
                            "requester_source_id", "c2"),
                    Map.of("source_id", "k3", "title", "Feature idea", "status", "not-a-status",
                            "requester_source_id", "c1")));
            records.put("replies", List.of(
                    Map.of("source_id", "r1", "ticket_source_id", "k1", "body", "Looking into it",
                            "author_source_id", "a1"),
                    Map.of("source_id", "r2", "ticket_source_id", "k2", "body", "Internal note",
                            "is_internal_note", true)));
            return records;
        }

        @Override public String name() {
            return "fakedesk";
        }

        @Override public String displayName() {
            return "FakeDesk";
        }

        @Override
        public List<Map<String, Object>> credentialFields() {
            return List.of(
                    Map.of("name", "subdomain", "label", "Subdomain", "type", "text", "help", "acme"),
                    Map.of("name", "api_token", "label", "API token", "type", "password", "help", ""));
        }

        @Override
        public boolean testConnection(Map<String, String> credentials) {
            if ("down".equals(credentials.get("subdomain"))) {
                throw new IllegalStateException("FakeDesk is unreachable");
            }
            return TOKEN.equals(credentials.get("api_token"));
        }

        @Override public List<String> entityTypes() {
            return List.of("agents", "contacts", "tags", "tickets", "replies");
        }

        @Override
        public Map<String, String> defaultFieldMappings(String entityType) {
            return "tickets".equals(entityType) ? Map.of("subject", "title", "state", "status") : Map.of();
        }

        @Override
        public List<Map<String, Object>> availableSourceFields(String entityType, Map<String, String> credentials) {
            sourceFieldCredentials = credentials;
            return "tickets".equals(entityType)
                    ? List.of(Map.of("name", "subject", "label", "Subject", "escalated_options", List.of("title")),
                              Map.of("name", "state", "label", "State", "escalated_options", List.of("status")))
                    : List.of();
        }

        @Override
        public ExtractResult extract(String entityType, Map<String, String> credentials, String cursor) {
            if (!TOKEN.equals(credentials.get("api_token"))) {
                throw new IllegalStateException("bad credentials");
            }
            calls.add(new Object[] {entityType, cursor, ImportContext.isImporting()});
            BiConsumer<String, String> hook = onExtract;
            if (hook != null) {
                hook.accept(entityType, cursor);
            }
            List<Map<String, Object>> all = records().get(entityType);
            int offset = cursor == null ? 0 : Integer.parseInt(cursor);
            int end = Math.min(offset + 2, all.size());
            return new ExtractResult(all.subList(offset, end), end < all.size() ? String.valueOf(end) : null, all.size());
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private ImportJobRepository jobs;
    @Autowired private ImportSourceMapRepository maps;
    @Autowired private TicketRepository tickets;
    @Autowired private ReplyRepository replies;
    @Autowired private SlaPolicyRepository policies;
    @Autowired private ImportRunner runner;
    @Autowired private ImportCredentials sealer;
    @Autowired private FakeImportAdapter adapter;
    @Autowired private SlaService sla;
    @Autowired private AutomationRunner automations;
    @Autowired
    @Qualifier(EscalatedTransactionManagers.ESCALATED)
    private PlatformTransactionManager transactionManager;

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private AgentProfile admin;
    private AgentProfile importedAgent;
    private MockHttpSession session;

    @BeforeEach
    void seed() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin = agents.save(admin);
        importedAgent = new AgentProfile();
        importedAgent.setName("Agent " + unique);
        importedAgent.setEmail("agent-" + unique + "@example.com");
        importedAgent.setAgent(true);
        importedAgent = agents.save(importedAgent);
        adapter.agentEmail = importedAgent.getEmail();
        adapter.onExtract = null;
        adapter.calls.clear();
        session = new MockHttpSession();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                .header("X-Inertia", "true").header("X-Inertia-Version", "build-1");
    }

    private JsonNode page(String path, String component) throws Exception {
        MvcResult result = mvc.perform(as(get(path))).andExpect(status().isOk()).andReturn();
        JsonNode page = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("component").asText()).isEqualTo(component);
        return page.get("props");
    }

    private static List<String> own(JsonNode props) {
        List<String> names = new ArrayList<>(fieldNames(props));
        names.removeAll(Set.of("errors", "auth", "current_user", "flash", "escalated", "plugin_ui", "page"));
        return names;
    }

    private MvcResult connect(Map<String, String> credentials) throws Exception {
        return mvc.perform(as(post(BASE + "/fakedesk/test-connection")).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("credentials", credentials)))).andReturn();
    }

    private ImportJob start() throws Exception {
        assertThat(connect(CREDENTIALS).getResponse().getContentAsString()).isEqualTo("{\"success\":true}");
        MvcResult result = mvc.perform(as(post(BASE + "/fakedesk/start")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"field_mappings\":" + MAPPINGS + "}")).andExpect(status().is3xxRedirection()).andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).startsWith(BASE + "/progress/");
        runner.awaitIdle();
        return jobs.findById(location.substring((BASE + "/progress/").length())).orElseThrow();
    }

    private List<Ticket> imported(ImportJob job) {
        List<Ticket> out = new ArrayList<>();
        for (ImportSourceMap map : maps.findByImportJobIdAndEntityTypeOrderByIdAsc(job.getId(), "tickets")) {
            out.add(tickets.findById(Long.valueOf(map.getEscalatedId())).orElseThrow());
        }
        return out;
    }

    @Test
    void indexAndConnectRenderWithLaravelsProps() throws Exception {
        ImportJob job = new ImportJob();
        job.setPlatform("fakedesk");
        job.setStatus("completed");
        job.setCredentials(sealer.seal(CREDENTIALS));
        job = jobs.save(job);

        JsonNode index = page(BASE, "Escalated/Admin/Import/Index");
        assertThat(own(index)).containsExactlyInAnyOrder("adapters", "jobs");
        JsonNode adapterRow = index.get("adapters").get(0);
        assertThat(fieldNames(adapterRow)).containsExactly("name", "display_name", "credential_fields");
        assertThat(adapterRow.get("display_name").asText()).isEqualTo("FakeDesk");
        String id = job.getId();
        JsonNode row = null;
        for (JsonNode candidate : index.get("jobs")) {
            if (candidate.get("id").asText().equals(id)) {
                row = candidate;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("status").asText()).isEqualTo("completed");
        assertThat(row.has("created_at")).isTrue();
        assertThat(row.has("credentials")).isFalse();

        JsonNode connect = page(BASE + "/fakedesk/connect", "Escalated/Admin/Import/Connect");
        assertThat(own(connect)).containsExactlyInAnyOrder("platform", "display_name", "credential_fields");
        assertThat(connect.get("credential_fields").get(1).get("name").asText()).isEqualTo("api_token");

        mvc.perform(as(get(BASE + "/nowhere/connect"))).andExpect(status().isNotFound());
    }

    @Test
    void testConnectionAnswersSuccessOrTheSourcesError() throws Exception {
        MvcResult refused = connect(Map.of("subdomain", "acme", "api_token", "wrong"));
        assertThat(refused.getResponse().getStatus()).isEqualTo(200);
        assertThat(refused.getResponse().getContentAsString()).isEqualTo("{\"success\":false}");

        MvcResult down = connect(Map.of("subdomain", "down"));
        assertThat(down.getResponse().getStatus()).isEqualTo(422);
        JsonNode body = JSON.readTree(down.getResponse().getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("error").asText()).isEqualTo("FakeDesk is unreachable");
    }

    @Test
    void mappingNeedsATestedConnectionAndReadsSourceFieldsWithIt() throws Exception {
        mvc.perform(as(get(BASE + "/fakedesk/mapping"))).andExpect(status().is3xxRedirection());

        connect(CREDENTIALS);
        MvcResult result = mvc.perform(as(get(BASE + "/fakedesk/mapping"))).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(TOKEN);
        JsonNode props = JSON.readTree(result.getResponse().getContentAsString()).get("props");
        // Laravel does not send the declared `credentials` either: they stay on the server.
        assertThat(own(props)).containsExactlyInAnyOrder("platform", "display_name", "entity_types", "mappings");
        assertThat(props.get("entity_types").size()).isEqualTo(5);
        JsonNode ticketMappings = props.get("mappings").get("tickets");
        assertThat(ticketMappings.get("defaults").get("subject").asText()).isEqualTo("title");
        assertThat(ticketMappings.get("source_fields").get(1).get("name").asText()).isEqualTo("state");
        assertThat(adapter.sourceFieldCredentials).isEqualTo(CREDENTIALS);
    }

    @Test
    void reviewEchoesTheChoicesAndWritesNothing() throws Exception {
        long before = jobs.count();
        MvcResult result = mvc.perform(as(post(BASE + "/fakedesk/review")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"field_mappings\":" + MAPPINGS + ",\"entity_types\":[\"tickets\",\"replies\"]}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode page = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Import/Review");
        JsonNode props = page.get("props");
        assertThat(own(props)).containsExactlyInAnyOrder("platform", "field_mappings", "entity_types");
        assertThat(props.get("field_mappings").get("tickets").get("subject").asText()).isEqualTo("title");
        assertThat(props.get("entity_types").toString()).isEqualTo("[\"tickets\",\"replies\"]");
        assertThat(jobs.count()).isEqualTo(before);
    }

    @Test
    void startRefusesWithoutATestedConnection() throws Exception {
        long before = jobs.count();
        mvc.perform(as(post(BASE + "/fakedesk/start")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"field_mappings\":" + MAPPINGS + "}")).andExpect(status().is3xxRedirection());
        assertThat(jobs.count()).isEqualTo(before);
    }

    @Test
    void theRunImportsTheTicketsEndToEndInImportMode() throws Exception {
        SlaPolicy policy = new SlaPolicy();
        policy.setName("Import default " + UUID.randomUUID());
        policy.setDefaultPolicy(true);
        policy.setFirstResponseHours(Map.of("high", 1));
        policy.setResolutionHours(Map.of("high", 8));
        policy.setActive(true);
        policies.save(policy);

        ImportJob job = start();

        assertThat(job.getStatus()).isEqualTo("completed");
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getCredentials()).isNull();
        assertThat(job.getUserId()).isEqualTo(admin.getId().toString());
        JsonNode progress = JSON.readTree(job.getProgress()).get("tickets");
        assertThat(progress.get("total").asInt()).isEqualTo(3);
        assertThat(progress.get("processed").asInt()).isEqualTo(3);
        assertThat(progress.get("cursor").asText()).isEqualTo("2");
        assertThat(job.getErrorLog()).isEqualTo("[]");

        List<Ticket> rows = imported(job);
        assertThat(rows).extracting(Ticket::getSubject).containsExactly("Refund please", "Login broken", "Feature idea");
        assertThat(rows).extracting(Ticket::getStatus)
                .containsExactly(TicketStatus.OPEN, TicketStatus.RESOLVED, TicketStatus.OPEN);
        assertThat(rows).extracting(Ticket::getPriority)
                .containsExactly(TicketPriority.HIGH, TicketPriority.URGENT, TicketPriority.MEDIUM);
        Ticket refund = rows.get(0);
        assertThat(refund.getRequesterEmail()).isEqualTo("ada@customer.test");
        assertThat(refund.getContact()).isNotNull();
        assertThat(refund.getAssignedAgent().getId()).isEqualTo(importedAgent.getId());
        assertThat(inTransaction(() -> tickets.findById(refund.getId()).orElseThrow().getTags().size())).isEqualTo(1);
        assertThat(refund.getCreatedAt()).isEqualTo(Instant.parse("2024-01-02T03:04:05Z"));
        assertThat(rows).allSatisfy(t -> assertThat(t.getSlaPolicy()).isNull());
        assertThat(replies.findByTicketIdOrderByCreatedAtAsc(refund.getId())).singleElement()
                .satisfies(r -> assertThat(r.getAuthorEmail()).isEqualTo(importedAgent.getEmail()));
        assertThat(replies.findByTicketIdOrderByCreatedAtAsc(rows.get(1).getId())).singleElement()
                .satisfies(r -> assertThat(r.isInternal()).isTrue());

        // Import mode held in the run, not outside it.
        assertThat(adapter.calls).isNotEmpty().allSatisfy(call -> assertThat(call[2]).isEqualTo(true));
        assertThat(ImportContext.isImporting()).isFalse();

        // The session copy of the credentials went with the start.
        mvc.perform(as(get(BASE + "/fakedesk/mapping"))).andExpect(status().is3xxRedirection());

        JsonNode props = page(BASE + "/progress/" + job.getId(), "Escalated/Admin/Import/Progress");
        assertThat(own(props)).containsExactly("job");
        JsonNode sent = props.get("job");
        assertThat(fieldNames(sent)).contains("id", "platform", "status", "progress", "error_log", "field_mappings",
                "started_at", "completed_at", "created_at").doesNotContain("credentials");
        assertThat(sent.get("field_mappings").get("tickets").get("subject").asText()).isEqualTo("title");

        MvcResult poll = mvc.perform(as(get(BASE + "/status/" + job.getId()))).andExpect(status().isOk()).andReturn();
        JsonNode body = JSON.readTree(poll.getResponse().getContentAsString());
        assertThat(fieldNames(body)).containsExactlyInAnyOrder("status", "progress", "error_log", "started_at",
                "completed_at");
        assertThat(body.get("status").asText()).isEqualTo("completed");
        mvc.perform(as(get(BASE + "/status/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void credentialsAreSealedAtRest() throws Exception {
        adapter.onExtract = (type, cursor) -> {
            throw new IllegalStateException("source down");
        };

        ImportJob job = start();

        assertThat(job.getStatus()).isEqualTo("failed");
        assertThat(job.getCredentials()).isNotNull().doesNotContain(TOKEN);
    }

    @Test
    void pauseMidRunThenResumeImportsNothingTwice() throws Exception {
        adapter.onExtract = (type, cursor) -> {
            if ("tickets".equals(type) && "2".equals(cursor)) {
                for (ImportJob running : jobs.findAll()) {
                    if ("importing".equals(running.getStatus())) {
                        jobs.setStatus(running.getId(), "paused");
                    }
                }
            }
        };

        ImportJob job = start();
        assertThat(job.getStatus()).isEqualTo("paused");
        assertThat(imported(job)).hasSize(3);
        assertThat(JSON.readTree(job.getProgress()).has("replies")).isFalse();
        assertThat(job.getCredentials()).isNotNull();

        adapter.onExtract = null;
        adapter.calls.clear();
        mvc.perform(as(post(BASE + "/resume/" + job.getId()))).andExpect(status().is3xxRedirection());
        runner.awaitIdle();

        ImportJob resumed = jobs.findById(job.getId()).orElseThrow();
        assertThat(resumed.getStatus()).isEqualTo("completed");
        List<Ticket> rows = imported(resumed);
        assertThat(rows).hasSize(3);
        assertThat(replies.findByTicketIdOrderByCreatedAtAsc(rows.get(0).getId())).hasSize(1);
        // The run picked tickets up from its saved cursor, not from the start.
        assertThat(adapter.calls).noneMatch(call -> "tickets".equals(call[0]) && call[1] == null);
        assertThat(adapter.calls).anyMatch(call -> "tickets".equals(call[0]) && "2".equals(call[1]));
    }

    @Test
    void pauseCancelAndResumeOfAFailedJob() throws Exception {
        ImportJob importing = new ImportJob();
        importing.setPlatform("fakedesk");
        importing.setStatus("importing");
        importing.setCredentials(sealer.seal(CREDENTIALS));
        importing = jobs.save(importing);

        mvc.perform(as(post(BASE + "/pause/" + importing.getId()))).andExpect(status().is3xxRedirection());
        assertThat(jobs.findById(importing.getId()).orElseThrow().getStatus()).isEqualTo("paused");

        mvc.perform(as(post(BASE + "/cancel/" + importing.getId()))).andExpect(status().is3xxRedirection());
        ImportJob cancelled = jobs.findById(importing.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo("failed");
        assertThat(cancelled.getCompletedAt()).isNotNull();
        assertThat(cancelled.getCredentials()).isNull();

        ImportJob failed = new ImportJob();
        failed.setPlatform("fakedesk");
        failed.setStatus("failed");
        failed.setCredentials(sealer.seal(CREDENTIALS));
        failed = jobs.save(failed);
        mvc.perform(as(post(BASE + "/resume/" + failed.getId()))).andExpect(status().is3xxRedirection());
        runner.awaitIdle();
        assertThat(jobs.findById(failed.getId()).orElseThrow().getStatus()).isEqualTo("completed");
    }

    @Test
    void anAgentWhoIsNotAnAdminIsRefused() throws Exception {
        mvc.perform(get(BASE).with(user(importedAgent.getEmail()))).andExpect(status().isForbidden());
    }

    @Test
    void importModeHoldsBackSlaAndAutomations() {
        Ticket ticket = new Ticket();
        int affected = ImportContext.suppress(() -> {
            sla.applySlaPolicy(ticket);
            return automations.run();
        });
        assertThat(affected).isZero();
        assertThat(ticket.getSlaPolicy()).isNull();
        assertThat(ImportContext.isImporting()).isFalse();
    }
}
