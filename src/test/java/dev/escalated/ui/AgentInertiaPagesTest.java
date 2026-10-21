package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.CannedResponse;
import dev.escalated.models.Department;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.CannedResponseRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketFollowerRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The agent pages over the Inertia protocol, through the real security chain and router: the
 * component each route renders, the props the Vue pages read, and what each write does.
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
class AgentInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCRIPT_OPEN = "<script data-page=\"app\" type=\"application/json\">";

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private TicketService tickets;
    @Autowired private DepartmentRepository departments;
    @Autowired private TagRepository tags;
    @Autowired private CannedResponseRepository cannedResponses;
    @Autowired private TicketFollowerRepository followers;
    @Autowired private dev.escalated.services.SatisfactionRatingService ratings;

    private String unique;
    private AgentProfile agent;
    private Ticket ticket;
    private Department department;
    private Tag tag;
    private String totpSeed;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        totpSeed = "TOTP" + unique.toUpperCase();

        agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent.setTwoFactorSecret(totpSeed);
        agent = agents.save(agent);

        department = new Department();
        department.setName("Billing " + unique);
        department = departments.save(department);

        tag = new Tag();
        tag.setName("vip-" + unique);
        tag.setColor("#ff0000");
        tag = tags.save(tag);

        CannedResponse canned = new CannedResponse();
        canned.setTitle("Thanks " + unique);
        canned.setContent("Thanks for writing in.");
        canned.setShared(true);
        cannedResponses.save(canned);

        ticket = tickets.create("Printer on fire " + unique, "It is on fire.", "Requester",
                "requester-" + unique + "@example.com", TicketPriority.URGENT, null);
        tickets.assign(ticket.getId(), agent.getId(), agent.getEmail());
        tickets.addReply(ticket.getId(), "Have you tried water?", agent.getName(), agent.getEmail(), "agent", false);
        ticket = tickets.findById(ticket.getId());
    }

    @Test
    void theFirstVisitIsAnHtmlDocumentCarryingThePageObject() throws Exception {
        MvcResult result = mvc.perform(get("/escalated/agent").with(user(agent.getEmail())))
                .andExpect(status().isOk())
                .andExpect(header().string("Vary", "X-Inertia"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType()).startsWith("text/html");
        assertThat(html).contains("<div id=\"app\" data-page=\"").contains("src=\"/build/app.js\"")
                .contains("window.route=route");

        JsonNode page = pageFromHtml(html);
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Agent/Dashboard");
        assertThat(page.get("url").asText()).isEqualTo("/escalated/agent");
        assertThat(page.get("version").asText()).isEqualTo("build-1");

        JsonNode props = page.get("props");
        assertThat(fieldNames(props.get("stats")))
                .containsExactly("open", "my_assigned", "unassigned", "sla_breached", "resolved_today");
        assertThat(props.get("stats").get("my_assigned").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(props.has("recentTickets")).isTrue();
        assertThat(props.get("needsAttention").has("sla_breaching")).isTrue();
        assertThat(props.get("needsAttention").has("unassigned_urgent")).isTrue();
        assertThat(props.get("myPerformance").has("resolved_this_week")).isTrue();

        JsonNode recent = find(props.get("recentTickets"), ticket.getTicketNumber());
        assertThat(recent.get("requester").get("name").asText()).isEqualTo("Requester");
        assertThat(recent.get("assignee").get("name").asText()).isEqualTo(agent.getName());
        assertThat(recent.get("latest_reply").get("author").get("name").asText()).isEqualTo(agent.getName());
        assertThat(recent.get("priority").asText()).isEqualTo("urgent");
        assertThat(recent.get("status").asText()).isEqualTo("open");

        assertSharedProps(props);
        assertThat(html).doesNotContain(totpSeed).doesNotContainIgnoringCase("twoFactor");
    }

    @Test
    void anInertiaVisitGetsJsonAndTheQueueProps() throws Exception {
        JsonNode page = inertia(get("/escalated/agent/tickets").param("search", unique));

        assertThat(page.get("component").asText()).isEqualTo("Escalated/Agent/TicketIndex");
        JsonNode props = page.get("props");
        JsonNode paginator = props.get("tickets");
        assertThat(paginator.get("total").asLong()).isEqualTo(1);
        assertThat(fieldNames(paginator)).contains("data", "current_page", "last_page", "per_page", "links");
        JsonNode row = paginator.get("data").get(0);
        assertThat(fieldNames(row)).contains("id", "reference", "subject", "status", "priority", "requester",
                "assignee", "department", "tags", "ticket_type", "channel", "created_at", "latest_reply",
                "first_response_due_at", "resolution_due_at", "sla_first_response_breached",
                "sla_resolution_breached", "snoozed_until");
        assertThat(row.get("reference").asText()).isEqualTo(ticket.getTicketNumber());
        assertThat(props.get("filters").get("search").asText()).isEqualTo(unique);
        assertThat(fieldNames(find(props.get("departments"), department.getId()))).containsExactly("id", "name");
        assertThat(fieldNames(find(props.get("tags"), tag.getId()))).containsExactly("id", "name", "color");
    }

    @Test
    void theQueueFiltersByStatusAssigneeAndTag() throws Exception {
        assertThat(inertia(get("/escalated/agent/tickets").param("search", unique).param("status", "closed"))
                .get("props").get("tickets").get("total").asLong()).isZero();
        assertThat(inertia(get("/escalated/agent/tickets").param("search", unique)
                        .param("assigned_to", agent.getId().toString()))
                .get("props").get("tickets").get("total").asLong()).isEqualTo(1);
        assertThat(inertia(get("/escalated/agent/tickets").param("search", unique).param("unassigned", "1"))
                .get("props").get("tickets").get("total").asLong()).isZero();
        assertThat(inertia(get("/escalated/agent/tickets").param("search", unique).param("tag", tag.getId().toString()))
                .get("props").get("tickets").get("total").asLong()).isZero();
    }

    @Test
    void theTicketPageCarriesWhatTicketShowReads() throws Exception {
        JsonNode page = inertia(get("/escalated/agent/tickets/" + ticket.getTicketNumber()));

        assertThat(page.get("component").asText()).isEqualTo("Escalated/Agent/TicketShow");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("ticket", "departments", "tags", "cannedResponses", "macros",
                "is_following", "followers_count", "customActions");

        JsonNode shown = props.get("ticket");
        assertThat(fieldNames(shown)).contains("id", "reference", "subject", "description", "status", "priority",
                "requester", "assigned_to", "department", "tags", "replies", "attachments", "activities",
                "pinned_notes", "satisfaction_rating", "chat_session_id", "chat_messages", "requester_ticket_count",
                "related_tickets", "created_at");
        assertThat(shown.get("description").asText()).isEqualTo("It is on fire.");
        assertThat(shown.get("assigned_to").asLong()).isEqualTo(agent.getId());
        JsonNode reply = shown.get("replies").get(0);
        assertThat(fieldNames(reply)).contains("id", "body", "is_internal_note", "is_pinned", "author",
                "attachments", "created_at");
        assertThat(reply.get("author").get("name").asText()).isEqualTo(agent.getName());
        assertThat(shown.get("activities").size()).isGreaterThan(0);
        assertThat(fieldNames(shown.get("activities").get(0))).contains("id", "type", "description", "causer",
                "created_at");

        JsonNode canned = props.get("cannedResponses");
        assertThat(canned.size()).isGreaterThan(0);
        assertThat(fieldNames(canned.get(0))).contains("id", "title", "body");
        assertThat(props.get("is_following").asBoolean()).isFalse();

        String raw = page.toString();
        assertThat(raw).doesNotContain(totpSeed).doesNotContainIgnoringCase("twoFactor")
                .doesNotContain("guest_access").doesNotContain("guestAccess")
                .doesNotContain("hibernateLazyInitializer");
    }

    @Test
    void anUnansweredSurveyIsNotARating() throws Exception {
        String show = "/escalated/agent/tickets/" + ticket.getTicketNumber();
        ratings.createRatingRequest(ticket.getId());
        assertThat(inertia(get(show)).get("props").get("ticket").get("satisfaction_rating").isNull()).isTrue();

        ratings.rateTicket(ticket.getId(), 4, "Fine");
        JsonNode rating = inertia(get(show)).get("props").get("ticket").get("satisfaction_rating");
        assertThat(rating.get("rating").asInt()).isEqualTo(4);
        assertThat(rating.get("comment").asText()).isEqualTo("Fine");
    }

    @Test
    void anUnknownTicketIsANotFound() throws Exception {
        mvc.perform(get("/escalated/agent/tickets/ESC-999999999").header("X-Inertia", "true")
                        .header("X-Inertia-Version", "build-1").with(user(agent.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStaleAssetVersionIsAConflictNamingWhereToReload() throws Exception {
        mvc.perform(get("/escalated/agent/tickets").header("X-Inertia", "true")
                        .header("X-Inertia-Version", "build-0").with(user(agent.getEmail())))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Inertia-Location", "http://localhost/escalated/agent/tickets"));
    }

    @Test
    void aPartialReloadSendsOnlyTheNamedPropsAndTheErrors() throws Exception {
        JsonNode page = inertia(get("/escalated/agent/tickets")
                .header("X-Inertia-Partial-Component", "Escalated/Agent/TicketIndex")
                .header("X-Inertia-Partial-Data", "tickets"));

        assertThat(fieldNames(page.get("props"))).containsExactlyInAnyOrder("tickets", "errors");
    }

    @Test
    void aReplyIsSavedAndTheClientIsSentBackWithAFlash() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String show = "/escalated/agent/tickets/" + ticket.getTicketNumber();

        mvc.perform(post(show + "/reply").session(session).with(user(agent.getEmail())).with(csrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost" + show)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"On my way " + unique + "\",\"is_internal_note\":false,\"attachments\":[]}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", show));

        JsonNode page = inertia(get(show).session(session));
        assertThat(page.get("props").get("flash").get("success").asText()).isEqualTo("Reply sent.");
        JsonNode latest = page.get("props").get("ticket").get("replies").get(0);
        assertThat(latest.get("body").asText()).isEqualTo("On my way " + unique);
        assertThat(latest.get("is_internal_note").asBoolean()).isFalse();

        JsonNode again = inertia(get(show).session(session));
        assertThat(again.get("props").get("flash").get("success").isNull()).isTrue();
    }

    @Test
    void aNoteIsInternal() throws Exception {
        String show = "/escalated/agent/tickets/" + ticket.getTicketNumber();
        write(show + "/note", "{\"body\":\"Internal " + unique + "\"}").andExpect(status().isSeeOther());

        JsonNode latest = inertia(get(show)).get("props").get("ticket").get("replies").get(0);
        assertThat(latest.get("body").asText()).isEqualTo("Internal " + unique);
        assertThat(latest.get("is_internal_note").asBoolean()).isTrue();
    }

    @Test
    void anInvalidWriteGoesBackWithErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String show = "/escalated/agent/tickets/" + ticket.getTicketNumber();

        mvc.perform(post(show + "/status").session(session).with(user(agent.getEmail())).with(csrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost" + show)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"on_fire\"}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", show));

        JsonNode errors = inertia(get(show).session(session)).get("props").get("errors");
        assertThat(errors.get("status").asText()).isEqualTo("The selected status is invalid.");
        assertThat(tickets.findById(ticket.getId()).getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void statusPriorityAssignDepartmentAndTagsChangeTheTicket() throws Exception {
        String base = "/escalated/agent/tickets/" + ticket.getTicketNumber();
        AgentProfile other = new AgentProfile();
        other.setName("Other " + unique);
        other.setEmail("other-" + unique + "@example.com");
        other.setAgent(true);
        other = agents.save(other);

        write(base + "/status", "{\"status\":\"in_progress\"}").andExpect(status().isSeeOther());
        write(base + "/priority", "{\"priority\":\"low\"}").andExpect(status().isSeeOther());
        write(base + "/assign", "{\"agent_id\":" + other.getId() + "}").andExpect(status().isSeeOther());
        write(base + "/department", "{\"department_id\":" + department.getId() + "}").andExpect(status().isSeeOther());
        write(base + "/tags", "{\"tag_ids\":[" + tag.getId() + "]}").andExpect(status().isSeeOther());

        JsonNode shown = inertia(get(base)).get("props").get("ticket");
        assertThat(shown.get("status").asText()).isEqualTo("in_progress");
        assertThat(shown.get("priority").asText()).isEqualTo("low");
        assertThat(shown.get("assigned_to").asLong()).isEqualTo(other.getId());
        assertThat(shown.get("department").get("id").asLong()).isEqualTo(department.getId());
        assertThat(shown.get("tags").get(0).get("id").asLong()).isEqualTo(tag.getId());

        write(base + "/tags", "{\"tag_ids\":[]}").andExpect(status().isSeeOther());
        assertThat(inertia(get(base)).get("props").get("ticket").get("tags").size()).isZero();
    }

    @Test
    void formEncodedWritesReadTheSameFields() throws Exception {
        String base = "/escalated/agent/tickets/" + ticket.getTicketNumber();
        mvc.perform(post(base + "/tags").with(user(agent.getEmail())).with(csrf()).header("X-Inertia", "true")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("tag_ids[0]", tag.getId().toString()))
                .andExpect(status().isSeeOther());

        assertThat(inertia(get(base)).get("props").get("ticket").get("tags").get(0).get("id").asLong())
                .isEqualTo(tag.getId());
    }

    @Test
    void followingToggles() throws Exception {
        String base = "/escalated/agent/tickets/" + ticket.getTicketNumber();
        write(base + "/follow", "{}").andExpect(status().isSeeOther());
        JsonNode props = inertia(get(base)).get("props");
        assertThat(props.get("is_following").asBoolean()).isTrue();
        assertThat(props.get("followers_count").asInt()).isEqualTo(1);

        write(base + "/follow", "{}").andExpect(status().isSeeOther());
        assertThat(followers.existsByTicketIdAndUserId(ticket.getId(), agent.getId().toString())).isFalse();
    }

    @Test
    void aWriteWithoutTheCsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/escalated/agent/tickets/" + ticket.getTicketNumber() + "/reply")
                        .with(user(agent.getEmail())).header("X-Inertia", "true")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void someoneWhoIsNotAnAgentCannotOpenTheAgentPages() throws Exception {
        mvc.perform(get("/escalated/agent").with(user("customer-" + unique + "@example.com")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/escalated/agent/tickets/" + ticket.getTicketNumber())
                        .with(user("customer-" + unique + "@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theInertiaCsrfCookieIsIssued() throws Exception {
        MvcResult result = mvc.perform(get("/escalated/agent").with(user(agent.getEmail())))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").isHttpOnly()).isFalse();
    }

    private void assertSharedProps(JsonNode props) {
        JsonNode user = props.get("auth").get("user");
        assertThat(fieldNames(user)).containsExactly("id", "name", "email");
        assertThat(user.get("id").asLong()).isEqualTo(agent.getId());
        assertThat(fieldNames(props.get("flash"))).contains("success", "error");
        assertThat(props.get("errors").isObject()).isTrue();
        JsonNode escalated = props.get("escalated");
        assertThat(escalated.get("prefix").asText()).isEqualTo("escalated");
        assertThat(escalated.get("is_agent").asBoolean()).isTrue();
        assertThat(escalated.get("is_admin").asBoolean()).isFalse();
        assertThat(escalated.get("permissions").isArray()).isTrue();
        assertThat(escalated.get("features").has("newsletters")).isTrue();
        assertThat(escalated.get("broadcasting").has("channel_prefix")).isTrue();
        assertThat(escalated.get("agent_type").asText()).isEqualTo("full");
    }

    private org.springframework.test.web.servlet.ResultActions write(String path, String json) throws Exception {
        return mvc.perform(post(path).with(user(agent.getEmail())).with(csrf()).header("X-Inertia", "true")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.with(user(agent.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    static JsonNode pageFromHtml(String html) throws Exception {
        int start = html.indexOf(SCRIPT_OPEN) + SCRIPT_OPEN.length();
        int end = html.indexOf("</script>", start);
        return JSON.readTree(html.substring(start, end));
    }

    static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode find(JsonNode array, Object idOrReference) {
        for (JsonNode item : array) {
            if (idOrReference instanceof Long id && item.get("id").asLong() == id) {
                return item;
            }
            if (idOrReference instanceof String reference && reference.equals(item.path("reference").asText())) {
                return item;
            }
        }
        throw new AssertionError(idOrReference + " not in " + array);
    }
}
