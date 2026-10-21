package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static dev.escalated.ui.AgentInertiaPagesTest.pageFromHtml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.models.Macro;
import dev.escalated.models.SavedView;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.MacroRepository;
import dev.escalated.repositories.SavedViewRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The admin ticket pages over the Inertia protocol: Index, Show, their writes, bulk actions and saved views. */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class AdminTicketInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private TicketService tickets;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private DepartmentRepository departments;
    @Autowired private TagRepository tags;
    @Autowired private MacroRepository macros;
    @Autowired private SavedViewRepository savedViews;
    @Autowired private UiRoutes routes;

    private String unique;
    private AgentProfile admin;
    private AgentProfile agent;
    private Ticket ticket;
    private Department department;
    private Tag tag;
    private String totpSeed;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        totpSeed = "TOTP" + unique.toUpperCase();
        admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin.setTwoFactorSecret(totpSeed);
        admin = agents.save(admin);

        agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent = agents.save(agent);

        department = new Department();
        department.setName("Billing " + unique);
        department = departments.save(department);

        tag = new Tag();
        tag.setName("vip-" + unique);
        tag = tags.save(tag);

        ticket = tickets.create("Printer on fire " + unique, "It is on fire.", "Requester",
                "requester-" + unique + "@example.com", TicketPriority.HIGH, null);
        tickets.addReply(ticket.getId(), "Have you tried water?", agent.getName(), agent.getEmail(), "agent", false);
        ticket = tickets.findById(ticket.getId());
    }

    @Test
    void anAgentWhoIsNotAnAdminIsRefused() throws Exception {
        mvc.perform(get("/escalated/admin/tickets").with(user(agent.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(get("/escalated/admin/tickets/" + ticket.getTicketNumber()).with(user(agent.getEmail())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/escalated/admin/tickets/bulk").with(user(agent.getEmail())).with(UiTestSupport.xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket_ids\":[" + ticket.getId() + "],\"action\":\"delete\"}"))
                .andExpect(status().isForbidden());
        assertThat(ticketRepository.existsById(ticket.getId())).isTrue();
    }

    @Test
    void theQueueCarriesWhatTheIndexPageReads() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/tickets").param("search", unique));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Tickets/Index");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("tickets", "filters", "departments", "tags", "agents", "savedViews");

        JsonNode paginator = props.get("tickets");
        assertThat(fieldNames(paginator)).contains("data", "current_page", "last_page", "per_page", "total", "links",
                "path");
        assertThat(paginator.get("path").asText()).isEqualTo("/escalated/admin/tickets");
        assertThat(paginator.get("total").asLong()).isEqualTo(1);
        JsonNode row = paginator.get("data").get(0);
        assertThat(fieldNames(row)).contains("id", "reference", "subject", "status", "priority", "requester",
                "assignee", "updated_at", "created_at");
        assertThat(row.get("reference").asText()).isEqualTo(ticket.getTicketNumber());
        assertThat(props.get("filters").get("search").asText()).isEqualTo(unique);

        JsonNode staff = find(props.get("agents"), agent.getId());
        assertThat(fieldNames(staff)).containsExactly("id", "name", "email");
        find(props.get("agents"), admin.getId());
        assertThat(fieldNames(find(props.get("departments"), department.getId()))).containsExactly("id", "name");
        assertThat(fieldNames(find(props.get("tags"), tag.getId()))).containsExactly("id", "name", "color");
        assertThat(props.get("savedViews").isArray()).isTrue();
        assertThat(page.toString()).doesNotContain(totpSeed);
    }

    @Test
    void theTicketPageCarriesWhatTheShowPageReads() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/tickets/" + ticket.getTicketNumber()));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Tickets/Show");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("ticket", "departments", "tags", "cannedResponses", "agents", "macros",
                "is_following", "followers_count", "is_snoozed");
        assertThat(props.get("is_snoozed").asBoolean()).isFalse();

        JsonNode shown = props.get("ticket");
        assertThat(fieldNames(shown)).contains("id", "reference", "subject", "description", "status", "priority",
                "requester", "assigned_to", "department", "tags", "replies", "attachments", "activities",
                "pinned_notes", "snoozed_until", "is_snoozed", "sla_policy", "satisfaction_rating",
                "requester_ticket_count", "related_tickets", "chat_messages", "created_at");
        assertThat(shown.get("requester").get("name").asText()).isEqualTo("Requester");
        assertThat(shown.get("replies").get(0).get("body").asText()).isEqualTo("Have you tried water?");
        assertThat(fieldNames(find(props.get("agents"), agent.getId()))).containsExactly("id", "name", "email");

        String raw = page.toString();
        assertThat(raw).doesNotContain(totpSeed).doesNotContainIgnoringCase("twoFactor")
                .doesNotContain("guest_access").doesNotContain("hibernateLazyInitializer");
    }

    @Test
    void theTicketWritesGoBackToTheAdminTicket() throws Exception {
        String base = "/escalated/admin/tickets/" + ticket.getTicketNumber();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post(base + "/reply").session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"On my way " + unique + "\"}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", base));
        assertThat(inertia(get(base).session(session)).get("props").get("flash").get("success").asText())
                .isEqualTo("Reply sent.");

        write(post(base + "/note"), "{\"body\":\"Internal " + unique + "\"}").andExpect(status().isSeeOther());
        write(post(base + "/status"), "{\"status\":\"waiting_on_customer\"}").andExpect(status().isSeeOther());
        write(post(base + "/priority"), "{\"priority\":\"low\"}").andExpect(status().isSeeOther());
        write(post(base + "/assign"), "{\"agent_id\":" + agent.getId() + "}").andExpect(status().isSeeOther());
        write(post(base + "/department"), "{\"department_id\":" + department.getId() + "}")
                .andExpect(status().isSeeOther());
        write(post(base + "/tags"), "{\"tag_ids\":[" + tag.getId() + "]}").andExpect(status().isSeeOther());
        write(post(base + "/follow"), "{}").andExpect(status().isSeeOther());

        JsonNode props = inertia(get(base)).get("props");
        JsonNode shown = props.get("ticket");
        assertThat(shown.get("replies").get(0).get("body").asText()).isEqualTo("Internal " + unique);
        assertThat(shown.get("replies").get(0).get("is_internal_note").asBoolean()).isTrue();
        assertThat(shown.get("status").asText()).isEqualTo("pending");
        assertThat(shown.get("priority").asText()).isEqualTo("low");
        assertThat(shown.get("assigned_to").asLong()).isEqualTo(agent.getId());
        assertThat(shown.get("department").get("id").asLong()).isEqualTo(department.getId());
        assertThat(shown.get("tags").get(0).get("id").asLong()).isEqualTo(tag.getId());
        assertThat(props.get("is_following").asBoolean()).isTrue();
    }

    @Test
    void aMacroIsApplied() throws Exception {
        Macro macro = new Macro();
        macro.setName("Escalate " + unique);
        macro.setActions("[{\"type\":\"set_priority\",\"value\":\"URGENT\"}]");
        macro = macros.save(macro);
        String base = "/escalated/admin/tickets/" + ticket.getTicketNumber();

        JsonNode listed = inertia(get(base)).get("props").get("macros");
        assertThat(fieldNames(find(listed, macro.getId()))).contains("id", "name", "description");

        write(post(base + "/macro"), "{\"macro_id\":" + macro.getId() + "}").andExpect(status().isSeeOther());
        assertThat(tickets.findById(ticket.getId()).getPriority()).isEqualTo(TicketPriority.URGENT);
    }

    @Test
    void aTicketIsSnoozedAndWokenUp() throws Exception {
        String base = "/escalated/admin/tickets/" + ticket.getTicketNumber();
        Instant until = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        write(post(base + "/snooze"), "{\"snoozed_until\":\"" + until + "\"}")
                .andExpect(status().isSeeOther()).andExpect(header().string("Location", base));

        Ticket snoozed = tickets.findById(ticket.getId());
        assertThat(snoozed.getStatus()).isEqualTo(TicketStatus.SNOOZED);
        assertThat(snoozed.getSnoozedUntil()).isEqualTo(until);
        JsonNode props = inertia(get(base)).get("props");
        assertThat(props.get("is_snoozed").asBoolean()).isTrue();
        assertThat(props.get("ticket").get("is_snoozed").asBoolean()).isTrue();
        assertThat(props.get("ticket").get("snoozed_until").asText()).isEqualTo(until.toString());

        write(post(base + "/unsnooze"), "{}").andExpect(status().isSeeOther());
        Ticket woken = tickets.findById(ticket.getId());
        assertThat(woken.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(woken.getSnoozedUntil()).isNull();
    }

    @Test
    void aSnoozeInThePastGoesBackWithAnError() throws Exception {
        String base = "/escalated/admin/tickets/" + ticket.getTicketNumber();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post(base + "/snooze").session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost" + base)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"snoozed_until\":\"2001-01-01T00:00:00Z\"}"))
                .andExpect(status().isSeeOther());
        assertThat(inertia(get(base).session(session)).get("props").get("errors").get("snoozed_until").asText())
                .isEqualTo("The snoozed until field must be a date after now.");
        assertThat(tickets.findById(ticket.getId()).getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void bulkActionsApplyToEachTicket() throws Exception {
        Ticket second = tickets.create("Second " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.LOW, null);
        String ids = ticket.getId() + "," + second.getId();
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/escalated/admin/tickets/bulk").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket_ids\":[" + ids + "],\"action\":\"status\",\"value\":\"in_progress\"}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/tickets"));
        assertThat(inertia(get("/escalated/admin/tickets").session(session)).get("props").get("flash")
                .get("success").asText()).isEqualTo("2 ticket(s) updated.");
        assertThat(tickets.findById(second.getId()).getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        bulk("{\"ticket_ids\":[" + ids + "],\"action\":\"priority\",\"value\":\"urgent\"}");
        bulk("{\"ticket_ids\":[" + ids + "],\"action\":\"assign\",\"value\":" + agent.getId() + "}");
        bulk("{\"ticket_ids\":[" + ids + "],\"action\":\"department\",\"value\":" + department.getId() + "}");
        bulk("{\"ticket_ids\":[" + ids + "],\"action\":\"tags\",\"value\":[" + tag.getId() + "]}");
        Ticket changed = tickets.findById(second.getId());
        assertThat(changed.getPriority()).isEqualTo(TicketPriority.URGENT);
        assertThat(changed.getAssignedAgent().getId()).isEqualTo(agent.getId());
        assertThat(changed.getDepartment().getId()).isEqualTo(department.getId());
        assertThat(inertia(get("/escalated/admin/tickets/" + second.getTicketNumber())).get("props").get("ticket")
                .get("tags").get(0).get("id").asLong()).isEqualTo(tag.getId());

        bulk("{\"ticket_ids\":[" + second.getId() + "],\"action\":\"delete\"}");
        assertThat(ticketRepository.existsById(second.getId())).isFalse();
        assertThat(ticketRepository.existsById(ticket.getId())).isTrue();
    }

    @Test
    void anUnknownBulkActionGoesBackWithAnError() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/tickets/bulk").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket_ids\":[" + ticket.getId() + "],\"action\":\"explode\",\"value\":1}"))
                .andExpect(status().isSeeOther());
        assertThat(inertia(get("/escalated/admin/tickets").session(session)).get("props").get("errors")
                .get("action").asText()).isEqualTo("The selected action is invalid.");
    }

    @Test
    void savedViewsAreStoredUpdatedAndDeletedWithTheMetaTagToken() throws Exception {
        // As SaveViewDialog does it: fetch() with the csrf-token meta tag's value in X-CSRF-TOKEN.
        MvcResult first = mvc.perform(get("/escalated/admin/tickets").with(user(admin.getEmail())))
                .andExpect(status().isOk()).andReturn();
        Matcher meta = Pattern.compile("<meta name=\"csrf-token\" content=\"([^\"]+)\">")
                .matcher(first.getResponse().getContentAsString());
        assertThat(meta.find()).isTrue();
        Cookie cookie = first.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(meta.group(1)).isEqualTo(cookie.getValue());
        assertThat(pageFromHtml(first.getResponse().getContentAsString()).get("component").asText())
                .isEqualTo("Escalated/Admin/Tickets/Index");

        MvcResult created = mvc.perform(post("/escalated/admin/saved-views").with(user(admin.getEmail()))
                        .cookie(cookie).header("X-CSRF-TOKEN", meta.group(1))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Urgent " + unique + "\",\"filters\":{\"priority\":\"urgent\"},"
                                + "\"is_shared\":true}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode view = JSON.readTree(created.getResponse().getContentAsString());
        assertThat(fieldNames(view)).contains("id", "name", "filters", "is_shared", "user_id");
        assertThat(view.get("filters").get("priority").asText()).isEqualTo("urgent");
        long id = view.get("id").asLong();

        JsonNode listed = find(inertia(get("/escalated/admin/tickets")).get("props").get("savedViews"), id);
        assertThat(listed.get("name").asText()).isEqualTo("Urgent " + unique);
        assertThat(listed.get("is_shared").asBoolean()).isTrue();

        json(put("/escalated/admin/saved-views/" + id), admin, "{\"name\":\"Hot " + unique + "\",\"filters\":{\"priority\":\"high\"}}")
                .andExpect(status().isOk());
        SavedView updated = savedViews.findById(id).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Hot " + unique);
        assertThat(updated.getFilters()).contains("high");

        // Shared with everyone, but only its owner changes it.
        json(delete("/escalated/admin/saved-views/" + id), otherAdmin(), "{}").andExpect(status().isForbidden());
        json(delete("/escalated/admin/saved-views/" + id), admin, "{}").andExpect(status().isOk());
        assertThat(savedViews.existsById(id)).isFalse();
    }

    @Test
    void anInvalidSavedViewIsA422WithAMessage() throws Exception {
        MvcResult result = json(post("/escalated/admin/saved-views"), admin, "{\"name\":\"\",\"filters\":{}}")
                .andExpect(status().isUnprocessableEntity()).andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("message").asText()).isEqualTo("The name field is required.");
        assertThat(body.get("errors").has("name")).isTrue();
    }

    @Test
    void aFetchWithoutAnyTokenIsRefused() throws Exception {
        mvc.perform(post("/escalated/admin/saved-views").with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"filters\":{\"a\":1}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theRouteTableNamesEveryAdminTicketRouteThePagesCall() {
        for (String name : new String[] {"index", "show", "bulk", "reply", "note", "assign", "status", "priority",
                "tags", "department", "macro", "follow", "snooze", "unsnooze"}) {
            assertThat(routes.all()).containsKey("escalated.admin.tickets." + name);
        }
        assertThat(routes.url("escalated.admin.tickets.snooze", "ESC-1")).isEqualTo("/escalated/admin/tickets/ESC-1/snooze");
        assertThat(routes.url("escalated.admin.saved-views.update", 7)).isEqualTo("/escalated/admin/saved-views/7");
    }

    private AgentProfile otherAdmin() {
        AgentProfile other = new AgentProfile();
        other.setName("Other " + unique);
        other.setEmail("other-admin-" + unique + "@example.com");
        other.setAgent(true);
        other.setAdmin(true);
        return agents.save(other);
    }

    private void bulk(String json) throws Exception {
        write(post("/escalated/admin/tickets/bulk"), json).andExpect(status().isSeeOther());
    }

    private ResultActions json(MockHttpServletRequestBuilder request, AgentProfile as, String json) throws Exception {
        return mvc.perform(request.with(user(as.getEmail())).with(UiTestSupport.xsrf())
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.with(user(admin.getEmail())).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.with(user(admin.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode find(JsonNode array, Long id) {
        for (JsonNode item : array) {
            if (item.get("id").asLong() == id) {
                return item;
            }
        }
        throw new AssertionError(id + " not in " + array);
    }
}
