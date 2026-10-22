package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Attachment;
import dev.escalated.models.AuditLog;
import dev.escalated.models.ChatSession;
import dev.escalated.models.CustomField;
import dev.escalated.models.CustomFieldValue;
import dev.escalated.models.GuestVerification;
import dev.escalated.models.Reply;
import dev.escalated.models.SatisfactionRating;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketFollower;
import dev.escalated.models.TicketLink;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AttachmentRepository;
import dev.escalated.repositories.AuditLogRepository;
import dev.escalated.repositories.ChatSessionRepository;
import dev.escalated.repositories.CustomFieldRepository;
import dev.escalated.repositories.CustomFieldValueRepository;
import dev.escalated.repositories.GuestVerificationRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.SatisfactionRatingRepository;
import dev.escalated.repositories.TicketFollowerRepository;
import dev.escalated.repositories.TicketLinkRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.scheduling.RetentionScheduler;
import dev.escalated.services.DataRetentionService;
import dev.escalated.services.SettingsService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The admin Settings sub-pages (CsatSettings, DataRetention, DatabaseConnection, EmailSettings,
 * PublicTickets) against escalated-laravel's controllers, and the data retention purge they
 * configure. {@code Admin/Settings/Sandbox} stays unrendered, as in Laravel.
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
class AdminSettingsSubPagesInertiaTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> RETENTION_KEYS =
            List.of("retention_closed_tickets", "retention_attachments", "retention_audit_logs");

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private SettingsService settings;
    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository tickets;
    @Autowired private ReplyRepository replies;
    @Autowired private AttachmentRepository attachments;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private SatisfactionRatingRepository ratings;
    @Autowired private TicketFollowerRepository followers;
    @Autowired private TicketLinkRepository links;
    @Autowired private ChatSessionRepository chats;
    @Autowired private CustomFieldRepository fields;
    @Autowired private CustomFieldValueRepository fieldValues;
    @Autowired private GuestVerificationRepository guestCodes;
    @Autowired private DataRetentionService retention;
    @Autowired private RetentionScheduler scheduler;
    @Autowired private JdbcTemplate jdbc;

    private String unique;
    private AgentProfile admin;
    private AgentProfile agent;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
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
        resetRetention();
    }

    /** The context is shared: leave every retention period at never for the next test. */
    @AfterEach
    void resetRetention() {
        RETENTION_KEYS.forEach(key -> settings.set(key, "never", "data_retention"));
    }

    @Test
    void csatRendersDefaultsSavesAndValidates() throws Exception {
        settings.delete("csat_question_text");
        settings.delete("csat_scale");
        settings.delete("csat_delivery_trigger");
        settings.delete("csat_delay_hours");
        JsonNode page = inertia(get("/escalated/admin/settings/csat"), null);
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Settings/CsatSettings");
        JsonNode values = page.get("props").get("settings");
        assertThat(fieldNames(values)).containsExactlyInAnyOrder(
                "csat_question_text", "csat_scale", "csat_delivery_trigger", "csat_delay_hours");
        assertThat(values.get("csat_question_text").asText()).isEqualTo("How would you rate your support experience?");
        assertThat(values.get("csat_scale").asText()).isEqualTo("1-5");
        assertThat(values.get("csat_delivery_trigger").asText()).isEqualTo("on_resolve");
        assertThat(values.get("csat_delay_hours").isInt()).isTrue();

        MockHttpSession session = new MockHttpSession();
        save(post("/escalated/admin/settings/csat"), session,
                "{\"csat_question_text\":\"How did we do?\",\"csat_scale\":\"1-3\","
                        + "\"csat_delivery_trigger\":\"delayed\",\"csat_delay_hours\":24}",
                "/escalated/admin/settings/csat");
        JsonNode after = inertia(get("/escalated/admin/settings/csat"), session).get("props");
        assertThat(after.get("flash").get("success").asText()).isEqualTo("CSAT settings updated.");
        assertThat(after.get("settings").get("csat_delay_hours").asInt()).isEqualTo(24);
        assertThat(settings.get("csat_question_text")).contains("How did we do?");
        assertThat(settings.get("csat_scale")).contains("1-3");
        assertThat(settings.get("csat_delivery_trigger")).contains("delayed");

        save(post("/escalated/admin/settings/csat"), session,
                "{\"csat_question_text\":\"\",\"csat_scale\":\"1-10\",\"csat_delivery_trigger\":\"sometimes\","
                        + "\"csat_delay_hours\":169}",
                "/escalated/admin/settings/csat");
        assertThat(fieldNames(inertia(get("/escalated/admin/settings/csat"), session).get("props").get("errors")))
                .contains("csat_question_text", "csat_scale", "csat_delivery_trigger", "csat_delay_hours");
        assertThat(settings.get("csat_scale")).contains("1-3");
    }

    @Test
    void dataRetentionRendersSettingsAndPreviewSavesAndValidates() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/settings/data-retention"), null);
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Settings/DataRetention");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props.get("settings"))).containsExactlyInAnyOrder("retention_closed_tickets",
                "retention_attachments", "retention_audit_logs", "retention_user_data_gdpr");
        assertThat(props.get("settings").get("retention_closed_tickets").asText()).isEqualTo("never");
        assertThat(props.get("settings").get("retention_user_data_gdpr").isBoolean()).isTrue();
        assertThat(fieldNames(props.get("purgePreview"))).containsExactlyInAnyOrder("tickets", "attachments", "audit_logs");
        assertThat(props.get("purgePreview").get("tickets").asLong()).isZero();

        closedTicket("Preview", Instant.now().minus(Duration.ofDays(6 * 365)));
        MockHttpSession session = new MockHttpSession();
        save(post("/escalated/admin/settings/data-retention"), session,
                "{\"retention_closed_tickets\":\"5_years\",\"retention_attachments\":\"never\","
                        + "\"retention_audit_logs\":\"90_days\",\"retention_user_data_gdpr\":true}",
                "/escalated/admin/settings/data-retention");
        JsonNode after = inertia(get("/escalated/admin/settings/data-retention"), session).get("props");
        assertThat(after.get("flash").get("success").asText()).isEqualTo("Data retention settings updated.");
        assertThat(after.get("settings").get("retention_user_data_gdpr").asBoolean()).isTrue();
        assertThat(after.get("purgePreview").get("tickets").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(settings.get("retention_closed_tickets")).contains("5_years");
        assertThat(settings.get("retention_audit_logs")).contains("90_days");
        assertThat(settings.get("retention_user_data_gdpr")).contains("1");

        save(post("/escalated/admin/settings/data-retention"), session,
                "{\"retention_closed_tickets\":\"90_days\",\"retention_attachments\":\"forever\","
                        + "\"retention_audit_logs\":\"1_year\"}",
                "/escalated/admin/settings/data-retention");
        assertThat(fieldNames(inertia(get("/escalated/admin/settings/data-retention"), session).get("props").get("errors")))
                .contains("retention_closed_tickets", "retention_attachments", "retention_audit_logs",
                        "retention_user_data_gdpr");
        assertThat(settings.get("retention_closed_tickets")).contains("5_years");
    }

    @Test
    void databaseConnectionDescribesTheConnectionAndRefusesASwitch() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/settings/database"), null);
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Settings/DatabaseConnection");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("connections", "current", "hostDefault", "pinnedByConfig", "tablePrefix");
        assertThat(props.get("current").asText()).isEqualTo("dataSource");
        assertThat(props.get("hostDefault").asText()).isEqualTo("dataSource");
        assertThat(props.get("pinnedByConfig").asBoolean()).isTrue();
        assertThat(props.get("tablePrefix").asText()).isEqualTo("escalated_");
        JsonNode connection = props.get("connections").get(0);
        assertThat(fieldNames(connection)).containsExactlyInAnyOrder("name", "driver", "database", "is_current",
                "is_host_default", "reachable", "migrated", "ticket_count", "error");
        assertThat(connection.get("is_current").asBoolean()).isTrue();
        assertThat(connection.get("reachable").asBoolean()).isTrue();
        assertThat(connection.get("migrated").asBoolean()).isTrue();
        assertThat(connection.get("ticket_count").isNumber()).isTrue();
        assertThat(connection.get("error").isNull()).isTrue();
        assertThat(connection.get("database").asText()).doesNotContainIgnoringCase("password");

        MockHttpSession session = new MockHttpSession();
        save(post("/escalated/admin/settings/database"), session, "{\"connection\":\"dataSource\"}",
                "/escalated/admin/settings/database");
        assertThat(inertia(get("/escalated/admin/settings/database"), session).get("props").get("flash").get("error")
                .asText()).startsWith("The database connection is set in configuration");

        // An API client gets the description; an unknown or missing name is a 422.
        MvcResult tested = mvc.perform(post("/escalated/admin/settings/database/test").with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connection\":\"dataSource\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(JSON.readTree(tested.getResponse().getContentAsString()).get("reachable").asBoolean()).isTrue();
        mvc.perform(post("/escalated/admin/settings/database/test").with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connection\":\"reporting\"}"))
                .andExpect(status().isUnprocessableEntity());

        save(post("/escalated/admin/settings/database/test"), session, "{\"connection\":\"dataSource\"}",
                "/escalated/admin/settings/database");
        assertThat(inertia(get("/escalated/admin/settings/database"), session).get("props").get("flash").get("success")
                .asText()).isEqualTo("Connected to [dataSource].");
        save(post("/escalated/admin/settings/database/test"), session, "{}", "/escalated/admin/settings/database");
        assertThat(fieldNames(inertia(get("/escalated/admin/settings/database"), session).get("props").get("errors")))
                .contains("connection");
    }

    @Test
    void emailSettingsStoreNormalisedAddressesAndValidate() throws Exception {
        settings.delete("email_addresses");
        settings.delete("default_reply_address");
        JsonNode page = inertia(get("/escalated/admin/settings/email"), null);
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Settings/EmailSettings");
        assertThat(page.get("props").get("addresses").size()).isZero();
        assertThat(page.get("props").get("defaultReplyAddress").asText()).isEmpty();

        MockHttpSession session = new MockHttpSession();
        save(post("/escalated/admin/settings/email"), session,
                "{\"addresses\":[{\"email\":\"support@example.com\",\"display_name\":\"Support\",\"department_id\":3,"
                        + "\"dkim_status\":\"verified\",\"extra\":\"x\"},{\"email\":\"billing@example.com\"}],"
                        + "\"default_reply_address\":\"support@example.com\"}",
                "/escalated/admin/settings/email");
        JsonNode after = inertia(get("/escalated/admin/settings/email"), session).get("props");
        assertThat(after.get("flash").get("success").asText()).isEqualTo("Email settings updated.");
        assertThat(after.get("defaultReplyAddress").asText()).isEqualTo("support@example.com");
        JsonNode first = after.get("addresses").get(0);
        assertThat(fieldNames(first)).containsExactlyInAnyOrder("email", "display_name", "department_id", "dkim_status");
        assertThat(first.get("department_id").asInt()).isEqualTo(3);
        assertThat(first.get("dkim_status").asText()).isEqualTo("verified");
        JsonNode second = after.get("addresses").get(1);
        assertThat(second.get("dkim_status").asText()).isEqualTo("unknown");
        assertThat(second.get("display_name").isNull()).isTrue();
        assertThat(second.get("department_id").isNull()).isTrue();

        // Leaving the reply address out keeps it.
        save(post("/escalated/admin/settings/email"), session, "{\"addresses\":[{\"email\":\"help@example.com\"}]}",
                "/escalated/admin/settings/email");
        assertThat(settings.get("default_reply_address")).contains("support@example.com");

        save(post("/escalated/admin/settings/email"), session,
                "{\"addresses\":[{\"email\":\"not-an-email\",\"dkim_status\":\"great\"}],"
                        + "\"default_reply_address\":\"nope\"}",
                "/escalated/admin/settings/email");
        assertThat(fieldNames(inertia(get("/escalated/admin/settings/email"), session).get("props").get("errors")))
                .contains("addresses.0.email", "addresses.0.dkim_status", "default_reply_address");
        assertThat(settings.get("email_addresses").orElseThrow()).contains("help@example.com");
    }

    @Test
    void publicTicketsRendersAcceptsPostAndPutAndValidates() throws Exception {
        settings.set("guest_policy_mode", "unassigned", "public_tickets");
        settings.set("guest_policy_user_id", "", "public_tickets");
        JsonNode page = inertia(get("/escalated/admin/settings/public-tickets"), null);
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Settings/PublicTickets");
        JsonNode values = page.get("props").get("settings");
        assertThat(fieldNames(values)).containsExactlyInAnyOrder(
                "guest_policy_mode", "guest_policy_user_id", "guest_policy_signup_url_template");
        assertThat(values.get("guest_policy_user_id").isNull()).isTrue();

        MockHttpSession session = new MockHttpSession();
        save(post("/escalated/admin/settings/public-tickets"), session,
                "{\"guest_policy_mode\":\"guest_user\",\"guest_policy_user_id\":\"42\","
                        + "\"guest_policy_signup_url_template\":\"ignored\"}",
                "/escalated/admin/settings/public-tickets");
        JsonNode after = inertia(get("/escalated/admin/settings/public-tickets"), session).get("props");
        assertThat(after.get("flash").get("success").asText()).isEqualTo("Guest policy updated.");
        assertThat(after.get("settings").get("guest_policy_user_id").asText()).isEqualTo("42");
        assertThat(settings.get("guest_policy_signup_url_template")).contains("");

        save(put("/escalated/admin/settings/public-tickets"), session,
                "{\"guest_policy_mode\":\"prompt_signup\",\"guest_policy_signup_url_template\":\"https://example.com/join\"}",
                "/escalated/admin/settings/public-tickets");
        assertThat(settings.get("guest_policy_mode")).contains("prompt_signup");
        assertThat(settings.get("guest_policy_user_id")).contains("");

        save(post("/escalated/admin/settings/public-tickets"), session,
                "{\"guest_policy_mode\":\"everyone\",\"guest_policy_user_id\":\"0\",\"guest_policy_signup_url_template\":\""
                        + "a".repeat(501) + "\"}",
                "/escalated/admin/settings/public-tickets");
        assertThat(fieldNames(inertia(get("/escalated/admin/settings/public-tickets"), session).get("props").get("errors")))
                .contains("guest_policy_mode", "guest_policy_user_id", "guest_policy_signup_url_template");
        assertThat(settings.get("guest_policy_mode")).contains("prompt_signup");
        settings.set("guest_policy_mode", "unassigned", "public_tickets");
        settings.set("guest_policy_signup_url_template", "", "public_tickets");
    }

    @Test
    void anAgentWhoIsNotAnAdminIsRefused() throws Exception {
        for (String path : new String[] {"csat", "data-retention", "database", "email", "public-tickets"}) {
            mvc.perform(get("/escalated/admin/settings/" + path).with(user(agent.getEmail())))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/escalated/admin/settings/" + path).with(user(agent.getEmail()))
                            .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/escalated/admin/settings/database/test").with(user(agent.getEmail()))
                        .with(UiTestSupport.xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connection\":\"dataSource\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theRouteNamesAreLaravels() {
        UiRoutes routes = new UiRoutes();
        assertThat(routes.url("escalated.admin.settings.csat.update")).isEqualTo("/escalated/admin/settings/csat");
        assertThat(routes.url("escalated.admin.settings.data-retention.update"))
                .isEqualTo("/escalated/admin/settings/data-retention");
        assertThat(routes.url("escalated.admin.settings.database.test")).isEqualTo("/escalated/admin/settings/database/test");
        assertThat(routes.url("escalated.admin.settings.database.update")).isEqualTo("/escalated/admin/settings/database");
        assertThat(routes.url("escalated.admin.settings.email.update")).isEqualTo("/escalated/admin/settings/email");
        assertThat(routes.url("escalated.admin.settings.public-tickets.update"))
                .isEqualTo("/escalated/admin/settings/public-tickets");
    }

    // ── The purge ───────────────────────────────────────────────────────

    @Test
    void neverLeavesAnOldClosedTicketAlone() {
        Ticket old = closedTicket("Never", Instant.now().minus(Duration.ofDays(10 * 365)));
        DataRetentionService.Report report = scheduler.purgeExpired();
        assertThat(report.closedTickets()).isZero();
        assertThat(tickets.existsById(old.getId())).isTrue();
    }

    @Test
    void anOldClosedTicketIsPurgedWithItsRowsAndOthersAreKept() {
        settings.set("retention_closed_tickets", "5_years", "data_retention");
        Instant longAgo = Instant.now().minus(Duration.ofDays(6 * 365));
        Ticket expired = closedTicket("Expired", longAgo);
        Ticket recent = closedTicket("Recent", Instant.now().minus(Duration.ofDays(30)));
        Ticket open = ticketService.create("Open " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.LOW, null);
        open.setClosedAt(longAgo);
        open.setMergedIntoTicketId(expired.getId());
        open = tickets.save(open);
        Ticket resolved = closedTicket("Resolved", longAgo);
        resolved.setStatus(TicketStatus.RESOLVED);
        resolved = tickets.save(resolved);

        Reply reply = new Reply();
        reply.setTicket(expired);
        reply.setBody("hello");
        reply.setAuthorName("R");
        reply.setAuthorEmail("r-" + unique + "@example.com");
        reply = replies.save(reply);
        Attachment onReply = new Attachment();
        onReply.setTicket(expired);
        onReply.setReply(reply);
        onReply.setFileName("a.txt");
        onReply.setFilePath("a");
        attachments.save(onReply);
        SatisfactionRating rating = new SatisfactionRating();
        rating.setTicket(expired);
        rating.setRating(5);
        rating.setRaterEmail("r-" + unique + "@example.com");
        ratings.save(rating);
        followers.save(new TicketFollower(expired.getId(), "7"));
        TicketLink link = new TicketLink();
        link.setSourceTicket(recent);
        link.setTargetTicket(expired);
        links.save(link);
        ChatSession chat = new ChatSession();
        chat.setTicketId(expired.getId());
        chats.save(chat);
        CustomField field = new CustomField();
        field.setName("Order " + unique);
        field.setFieldKey("order_" + unique);
        field = fields.save(field);
        CustomFieldValue value = new CustomFieldValue();
        value.setTicket(expired);
        value.setCustomField(field);
        value.setValue("v");
        fieldValues.save(value);

        DataRetentionService.Report dry = retention.purgeExpired(true, Instant.now());
        assertThat(dry.closedTickets()).isGreaterThanOrEqualTo(1);
        assertThat(tickets.existsById(expired.getId())).isTrue();

        DataRetentionService.Report report = scheduler.purgeExpired();
        assertThat(report.closedTickets()).isGreaterThanOrEqualTo(1);
        assertThat(tickets.existsById(expired.getId())).isFalse();
        assertThat(replies.existsById(reply.getId())).isFalse();
        assertThat(attachments.existsById(onReply.getId())).isFalse();
        assertThat(ratings.existsById(rating.getId())).isFalse();
        assertThat(links.existsById(link.getId())).isFalse();
        assertThat(chats.existsById(chat.getId())).isFalse();
        assertThat(fieldValues.existsById(value.getId())).isFalse();
        assertThat(tickets.existsById(recent.getId())).isTrue();
        assertThat(tickets.existsById(resolved.getId())).isTrue();
        assertThat(tickets.findById(open.getId()).orElseThrow().getMergedIntoTicketId()).isNull();

        // Ticket numbers were the count plus one: with tickets gone that number is still taken.
        Ticket next = ticketService.create("After " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.LOW, null);
        Ticket after = ticketService.create("After 2 " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.LOW, null);
        assertThat(next.getTicketNumber()).isNotEqualTo(after.getTicketNumber());
    }

    @Test
    void attachmentsAuditLogsAndGuestCodesPastTheirCutoffsAreDeleted() {
        settings.set("retention_attachments", "1_year", "data_retention");
        settings.set("retention_audit_logs", "90_days", "data_retention");
        Ticket ticket = ticketService.create("Files " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.LOW, null);
        Attachment old = attachment(ticket, "old");
        Attachment fresh = attachment(ticket, "fresh");
        AuditLog oldLog = auditLog();
        AuditLog freshLog = auditLog();
        // created_at is not updatable through the entities.
        Timestamp twoYearsAgo = Timestamp.from(Instant.now().minus(Duration.ofDays(2 * 365)));
        jdbc.update("UPDATE escalated_attachments SET created_at = ? WHERE id = ?", twoYearsAgo, old.getId());
        jdbc.update("UPDATE escalated_audit_logs SET created_at = ? WHERE id = ?", twoYearsAgo, oldLog.getId());
        GuestVerification expiredCode = guestCode(Instant.now().minus(Duration.ofMinutes(5)));
        GuestVerification liveCode = guestCode(Instant.now().plus(Duration.ofMinutes(10)));

        assertThat(retention.preview(Instant.now()).attachments()).isGreaterThanOrEqualTo(1);
        DataRetentionService.Report dry = retention.purgeExpired(true, Instant.now());
        assertThat(dry.attachments()).isGreaterThanOrEqualTo(1);
        assertThat(dry.auditLogs()).isGreaterThanOrEqualTo(1);
        assertThat(dry.guestVerifications()).isGreaterThanOrEqualTo(1);
        assertThat(attachments.existsById(old.getId())).isTrue();
        assertThat(auditLogs.existsById(oldLog.getId())).isTrue();
        assertThat(guestCodes.existsById(expiredCode.getId())).isTrue();

        scheduler.purgeExpired();
        assertThat(attachments.existsById(old.getId())).isFalse();
        assertThat(auditLogs.existsById(oldLog.getId())).isFalse();
        assertThat(guestCodes.existsById(expiredCode.getId())).isFalse();
        assertThat(attachments.existsById(fresh.getId())).isTrue();
        assertThat(auditLogs.existsById(freshLog.getId())).isTrue();
        assertThat(guestCodes.existsById(liveCode.getId())).isTrue();
        assertThat(tickets.existsById(ticket.getId())).isTrue();
    }

    private Attachment attachment(Ticket ticket, String name) {
        Attachment attachment = new Attachment();
        attachment.setTicket(ticket);
        attachment.setFileName(name + ".txt");
        attachment.setFilePath(name);
        return attachments.save(attachment);
    }

    private AuditLog auditLog() {
        AuditLog log = new AuditLog();
        log.setEntityType("ticket");
        log.setAction("updated");
        return auditLogs.save(log);
    }

    private GuestVerification guestCode(Instant expiresAt) {
        GuestVerification code = new GuestVerification();
        code.setPublicId(UUID.randomUUID().toString());
        code.setPurpose("create");
        code.setEmailDigest("e-" + unique);
        code.setClientDigest("c-" + unique);
        code.setCodeDigest("d-" + unique);
        code.setExpiresAt(expiresAt);
        return guestCodes.save(code);
    }

    private Ticket closedTicket(String subject, Instant closedAt) {
        Ticket ticket = ticketService.create(subject + " " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.LOW, null);
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(closedAt);
        return tickets.save(ticket);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private void save(MockHttpServletRequestBuilder request, MockHttpSession session, String json, String back)
            throws Exception {
        mvc.perform(request.session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost" + back)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", back));
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        if (session != null) {
            request = request.session(session);
        }
        ResultActions result = mvc.perform(request.with(user(admin.getEmail()))
                .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"));
        MvcResult done = result.andExpect(status().isOk()).andReturn();
        return JSON.readTree(done.getResponse().getContentAsString());
    }
}
