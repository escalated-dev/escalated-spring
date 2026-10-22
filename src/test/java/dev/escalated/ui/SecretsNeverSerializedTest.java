package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.contracts.ImportAdapter;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.ApiToken;
import dev.escalated.models.ImportJob;
import dev.escalated.models.Webhook;
import dev.escalated.models.WebhookDelivery;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ImportJobRepository;
import dev.escalated.repositories.WebhookDeliveryRepository;
import dev.escalated.repositories.WebhookRepository;
import dev.escalated.services.ApiTokenService;
import dev.escalated.services.ImportJobService;
import hostapp.HostApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Stored secrets never reach the browser, as escalated-laravel's {@code SecretsNeverSerializedTest}.
 *
 * <p>Import source credentials, webhook signing secrets, API token hashes and two-factor seeds are
 * kept server-side so the app can use them. None is shown back to an admin: every page and JSON
 * response that carries one of these records is checked here for the raw secret anywhere in the
 * body. This port has no SSO settings and no inbound-email secrets among its settings (the inbound
 * secret is host configuration), so those Laravel surfaces have no counterpart here.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecretsNeverSerializedTest.Adapters.class)
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class SecretsNeverSerializedTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String IMPORT_API_KEY = "imp-key-7f3a9c2e5b1d";
    private static final String IMPORT_PASSWORD = "imp-pass-q8w7e6r5t4";
    /** RFC 6761: never resolves, so it is accepted at save time and fails at send time. */
    private static final String HOOK_HOST = "https://hooks.invalid";

    @TestConfiguration
    static class Adapters {
        @Bean
        LeakTestAdapter leakTestAdapter() {
            return new LeakTestAdapter();
        }
    }

    /** A source that accepts {@link #IMPORT_API_KEY}. */
    static class LeakTestAdapter implements ImportAdapter {
        @Override
        public String name() {
            return "leakdesk";
        }

        @Override
        public String displayName() {
            return "LeakDesk";
        }

        @Override
        public List<Map<String, Object>> credentialFields() {
            return List.of(
                    Map.of("name", "api_key", "label", "API key", "type", "password", "help", ""),
                    Map.of("name", "password", "label", "Password", "type", "password", "help", ""));
        }

        @Override
        public boolean testConnection(Map<String, String> credentials) {
            return IMPORT_API_KEY.equals(credentials.get("api_key"));
        }

        @Override
        public List<String> entityTypes() {
            return List.of("tickets");
        }

        @Override
        public Map<String, String> defaultFieldMappings(String entityType) {
            return Map.of("subject", "subject");
        }

        @Override
        public List<Map<String, Object>> availableSourceFields(String entityType, Map<String, String> credentials) {
            return List.of(Map.of("name", "subject"), Map.of("name", "description"));
        }

        @Override
        public ExtractResult extract(String entityType, Map<String, String> credentials, String cursor) {
            return new ExtractResult(List.of(), null, 0);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private ImportJobRepository importJobs;
    @Autowired private ImportJobService imports;
    @Autowired private WebhookRepository webhooks;
    @Autowired private WebhookDeliveryRepository deliveries;
    @Autowired private ApiTokenService tokens;

    private String unique;
    private AgentProfile admin;
    private String webhookSecret;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        webhookSecret = "whsec-4c8e1a7b-" + unique;
        admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin.setTwoFactorSecret("TOTPSEED" + unique.toUpperCase());
        admin = agents.save(admin);
    }

    // --- Import ------------------------------------------------------------------

    private ImportJob importJobWithCredentials(int errors) {
        ImportJob job = new ImportJob();
        job.setUserId(admin.getId().toString());
        job.setPlatform("leakdesk");
        job.setStatus("importing");
        job.setCredentials(imports.credentials().seal(Map.of("api_key", IMPORT_API_KEY, "password", IMPORT_PASSWORD)));
        job.setProgress("{\"tickets\":{\"total\":10,\"processed\":4,\"skipped\":0,\"failed\":0,\"cursor\":null}}");
        job.setFieldMappings("{\"tickets\":{\"subject\":\"subject\"}}");
        List<Map<String, Object>> log = new ArrayList<>();
        for (int i = 1; i <= errors; i++) {
            log.add(Map.of("entity_type", "tickets", "source_id", "s" + i, "error", "boom " + i));
        }
        try {
            job.setErrorLog(JSON.writeValueAsString(log));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return importJobs.save(job);
    }

    private void assertNoImportSecrets(String body, ImportJob job) {
        assertThat(body).doesNotContain(IMPORT_API_KEY).doesNotContain(IMPORT_PASSWORD)
                .doesNotContain("\"credentials\"");
        if (job != null) {
            assertThat(body).doesNotContain(job.getCredentials());
        }
    }

    @Test
    void importPagesAndStatusJsonCarryTheJobsFieldsButNeverItsCredentials() throws Exception {
        ImportJob job = importJobWithCredentials(60);

        String index = inertiaBody(get("/escalated/admin/import"));
        assertNoImportSecrets(index, job);
        JsonNode row = null;
        for (JsonNode candidate : JSON.readTree(index).get("props").get("jobs")) {
            if (candidate.get("id").asText().equals(job.getId())) {
                row = candidate;
            }
        }
        assertThat(row).isNotNull();
        assertThat(fieldNames(row)).containsExactlyInAnyOrder("id", "platform", "status", "progress",
                "field_mappings", "error_log", "started_at", "completed_at", "created_at");

        String progress = inertiaBody(get("/escalated/admin/import/progress/" + job.getId()));
        assertNoImportSecrets(progress, job);
        JsonNode props = JSON.readTree(progress).get("props").get("job");
        assertThat(fieldNames(props)).containsExactlyInAnyOrder("id", "platform", "status", "progress",
                "field_mappings", "error_log", "started_at", "completed_at", "created_at");
        assertThat(props.get("status").asText()).isEqualTo("importing");
        assertThat(props.get("progress").get("tickets").get("processed").asInt()).isEqualTo(4);
        // The newest 50, as Laravel's toPageProps().
        assertThat(props.get("error_log").size()).isEqualTo(50);
        assertThat(props.get("error_log").get(49).get("source_id").asText()).isEqualTo("s60");

        String status = mvc.perform(get("/escalated/admin/import/status/" + job.getId())
                        .with(user(admin.getEmail())).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNoImportSecrets(status, job);
        assertThat(JSON.readTree(status).get("status").asText()).isEqualTo("importing");
    }

    @Test
    void theConnectTestConnectionMappingAndReviewStepsNeverEchoTheCredentials() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String connect = inertiaBody(get("/escalated/admin/import/leakdesk/connect").session(session));
        assertNoImportSecrets(connect, null);

        String test = mvc.perform(post("/escalated/admin/import/leakdesk/test-connection").session(session)
                        .with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"credentials\":{\"api_key\":\"" + IMPORT_API_KEY + "\",\"password\":\""
                                + IMPORT_PASSWORD + "\"}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(test).get("success").asBoolean()).isTrue();
        assertNoImportSecrets(test, null);

        String mapping = inertiaBody(get("/escalated/admin/import/leakdesk/mapping").session(session));
        assertThat(JSON.readTree(mapping).get("component").asText()).isEqualTo("Escalated/Admin/Import/Mapping");
        assertNoImportSecrets(mapping, null);

        String review = mvc.perform(post("/escalated/admin/import/leakdesk/review").session(session)
                        .with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entity_types\":[\"tickets\"],\"field_mappings\":{\"tickets\":{}}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNoImportSecrets(review, null);
    }

    @Test
    void theImportRunStillReadsTheStoredCredentialsServerSide() throws Exception {
        ImportJob job = importJobWithCredentials(0);
        ImportJob stored = importJobs.findById(job.getId()).orElseThrow();

        assertThat(imports.credentials().open(stored.getCredentials()))
                .containsEntry("api_key", IMPORT_API_KEY).containsEntry("password", IMPORT_PASSWORD);
        String serialized = JSON.findAndRegisterModules().writeValueAsString(stored);
        assertThat(serialized).doesNotContain("credentials").doesNotContain(stored.getCredentials());
    }

    // --- Webhooks ----------------------------------------------------------------

    private Webhook webhookWithSecret() {
        Webhook webhook = new Webhook();
        webhook.setUrl(HOOK_HOST + "/" + unique);
        webhook.setEvents("ticket.created");
        webhook.setSecret(webhookSecret);
        return webhooks.save(webhook);
    }

    @Test
    void theWebhookIndexEditAndDeliveryLogPagesSayASecretIsSetWithoutSendingIt() throws Exception {
        Webhook webhook = webhookWithSecret();
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhook(webhook);
        delivery.setEventType("ticket.created");
        delivery.setPayload("{\"ok\":true}");
        delivery.setResponseStatus(200);
        delivery.setAttemptCount(1);
        deliveries.save(delivery);

        String index = inertiaBody(get("/escalated/admin/webhooks"));
        assertThat(index).doesNotContain(webhookSecret);
        for (JsonNode row : JSON.readTree(index).get("props").get("webhooks")) {
            if (row.get("id").asLong() == webhook.getId()) {
                assertThat(row.get("has_secret").asBoolean()).isTrue();
                assertThat(row.has("secret")).isFalse();
            }
        }

        String edit = inertiaBody(get("/escalated/admin/webhooks/" + webhook.getId() + "/edit"));
        assertThat(edit).doesNotContain(webhookSecret);
        JsonNode form = JSON.readTree(edit).get("props").get("webhook");
        assertThat(form.get("has_secret").asBoolean()).isTrue();
        assertThat(form.has("secret")).isFalse();

        String log = inertiaBody(get("/escalated/admin/webhooks/" + webhook.getId() + "/deliveries"));
        assertThat(log).doesNotContain(webhookSecret);
    }

    @Test
    void theWebhookJsonApiSaysASecretIsSetWithoutSendingIt() throws Exception {
        Webhook webhook = webhookWithSecret();
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhook(webhook);
        delivery.setEventType("ticket.created");
        delivery.setPayload("{\"ok\":true}");
        delivery.setAttemptCount(1);
        deliveries.save(delivery);

        String index = api(get("/escalated/api/admin/webhooks"));
        assertThat(index).doesNotContain(webhookSecret);
        String show = api(get("/escalated/api/admin/webhooks/" + webhook.getId()));
        assertThat(show).doesNotContain(webhookSecret);
        JsonNode shown = JSON.readTree(show);
        assertThat(shown.get("has_secret").asBoolean()).isTrue();
        assertThat(shown.has("secret")).isFalse();
        // A delivery used to embed its webhook, which embedded its deliveries, without end.
        assertThat(shown.has("deliveries")).isFalse();
        String log = api(get("/escalated/api/admin/webhooks/" + webhook.getId() + "/deliveries"));
        assertThat(log).doesNotContain(webhookSecret);
        assertThat(JSON.readTree(log).get(0).get("webhookId").asLong()).isEqualTo(webhook.getId());
        assertThat(JSON.readTree(log).get(0).has("webhook")).isFalse();

        String created = mvc.perform(post("/escalated/api/admin/webhooks").with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + HOOK_HOST + "/new-" + unique + "\",\"secret\":\"whsec-new-" + unique
                                + "\",\"events\":\"ticket.created\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain("whsec-new-" + unique);
        assertThat(JSON.readTree(created).get("has_secret").asBoolean()).isTrue();
    }

    @Test
    void savingTheWebhookFormWithTheSecretLeftBlankKeepsTheStoredOne() throws Exception {
        Webhook webhook = webhookWithSecret();
        form(put("/escalated/admin/webhooks/" + webhook.getId()),
                "{\"url\":\"" + webhook.getUrl() + "\",\"events\":[\"ticket.created\",\"ticket.updated\"],"
                        + "\"secret\":\"\",\"clear_secret\":false,\"active\":true}");

        Webhook saved = webhooks.findById(webhook.getId()).orElseThrow();
        assertThat(saved.getSecret()).isEqualTo(webhookSecret);
        assertThat(saved.getEvents()).isEqualTo("ticket.created,ticket.updated");
    }

    @Test
    void theWebhookFormReplacesTheSecretWhenANewOneIsEnteredAndClearsItOnRequest() throws Exception {
        Webhook webhook = webhookWithSecret();
        String base = "{\"url\":\"" + webhook.getUrl() + "\",\"events\":[\"ticket.created\"],\"active\":true,";

        form(put("/escalated/admin/webhooks/" + webhook.getId()), base + "\"secret\":\"whsec-rotated\"}");
        assertThat(webhooks.findById(webhook.getId()).orElseThrow().getSecret()).isEqualTo("whsec-rotated");

        form(put("/escalated/admin/webhooks/" + webhook.getId()), base + "\"secret\":\"\",\"clear_secret\":true}");
        assertThat(webhooks.findById(webhook.getId()).orElseThrow().getSecret()).isNull();
        JsonNode edit = JSON.readTree(inertiaBody(get("/escalated/admin/webhooks/" + webhook.getId() + "/edit")));
        assertThat(edit.get("props").get("webhook").get("has_secret").asBoolean()).isFalse();
    }

    @Test
    void theWebhookJsonApiKeepsReplacesAndClearsTheSecretTheSameWay() throws Exception {
        Webhook webhook = webhookWithSecret();
        String base = "{\"url\":\"" + webhook.getUrl() + "\",\"events\":\"ticket.created\",\"active\":true";

        String kept = api(put("/escalated/api/admin/webhooks/" + webhook.getId())
                .contentType(MediaType.APPLICATION_JSON).content(base + ",\"secret\":\"\"}"));
        assertThat(kept).doesNotContain(webhookSecret);
        assertThat(JSON.readTree(kept).get("has_secret").asBoolean()).isTrue();
        assertThat(webhooks.findById(webhook.getId()).orElseThrow().getSecret()).isEqualTo(webhookSecret);

        api(put("/escalated/api/admin/webhooks/" + webhook.getId())
                .contentType(MediaType.APPLICATION_JSON).content(base + ",\"secret\":\"whsec-rotated\"}"));
        assertThat(webhooks.findById(webhook.getId()).orElseThrow().getSecret()).isEqualTo("whsec-rotated");

        String cleared = api(put("/escalated/api/admin/webhooks/" + webhook.getId())
                .contentType(MediaType.APPLICATION_JSON).content(base + ",\"clear_secret\":true}"));
        assertThat(JSON.readTree(cleared).get("has_secret").asBoolean()).isFalse();
        assertThat(webhooks.findById(webhook.getId()).orElseThrow().getSecret()).isNull();
    }

    // --- API tokens and two-factor seeds -----------------------------------------

    @Test
    void theApiTokenPageAndJsonNeverCarryTheTokenHashOrATwoFactorSeed() throws Exception {
        Map<String, Object> issued = tokens.createToken("CI " + unique, admin.getId(), null, null);
        ApiToken token = (ApiToken) issued.get("token");
        String plain = (String) issued.get("plainTextToken");

        String page = inertiaBody(get("/escalated/admin/api-tokens"));
        String json = api(get("/escalated/api/admin/tokens"));
        String agentsJson = api(get("/escalated/api/admin/agents"));
        for (String body : List.of(page, json, agentsJson)) {
            assertThat(body).doesNotContain(token.getTokenHash())
                    .doesNotContain(plain)
                    .doesNotContain(admin.getTwoFactorSecret())
                    .doesNotContainIgnoringCase("token_hash")
                    .doesNotContainIgnoringCase("tokenHash")
                    .doesNotContainIgnoringCase("twoFactorSecret")
                    .doesNotContainIgnoringCase("two_factor_secret");
        }
    }

    // --- Entities never serialize their secrets ----------------------------------

    @Test
    void everyEntityThatStoresASecretHidesItFromTheSerializer() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Webhook webhook = webhookWithSecret();
        ImportJob job = importJobWithCredentials(0);
        ApiToken token = (ApiToken) tokens.createToken("CI", admin.getId(), null, null).get("token");

        JsonNode webhookJson = mapper.valueToTree(webhook);
        assertThat(webhookJson.has("secret")).isFalse();
        assertThat(webhookJson.get("has_secret").asBoolean()).isTrue();
        assertThat(mapper.writeValueAsString(webhook)).doesNotContain(webhookSecret);
        assertThat(mapper.valueToTree(job).has("credentials")).isFalse();
        assertThat(mapper.writeValueAsString(token)).doesNotContain(token.getTokenHash());
        assertThat(mapper.valueToTree(token).has("tokenHash")).isFalse();
        AgentProfile detached = new AgentProfile();
        detached.setName("Detached");
        detached.setEmail("detached-" + unique + "@example.com");
        detached.setTwoFactorSecret("TOTPDETACHED");
        assertThat(mapper.writeValueAsString(detached)).doesNotContain("TOTPDETACHED")
                .doesNotContainIgnoringCase("twoFactorSecret");
    }

    // --- helpers ------------------------------------------------------------------

    private String inertiaBody(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(user(admin.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String api(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(user(admin.getEmail())).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void form(MockHttpServletRequestBuilder request, String json) throws Exception {
        mvc.perform(request.with(user(admin.getEmail())).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isSeeOther());
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
