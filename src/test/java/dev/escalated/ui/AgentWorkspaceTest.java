package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.security.RoutePermissions;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

/**
 * What the agent ticket page needs beyond the ticket itself, as escalated-laravel's
 * {@code AgentWorkspaceTest}: the agents it can be reassigned to, the requester's other tickets,
 * and snoozing from the agent panel (until now only the admin panel could snooze). Through the real
 * security chain and router.
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
class AgentWorkspaceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private DepartmentRepository departments;
    @Autowired private TicketService tickets;
    @Autowired private EscalatedProperties properties;

    private String unique;
    private AgentProfile agent;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        agent = staff("Ada Agent " + unique, false);
    }

    @AfterEach
    void restoreVisibility() {
        properties.setVisibility("all");
    }

    // --- Reassigning --------------------------------------------------------

    @Test
    void sendsTheAgentsAndAdminsATicketCanBeAssignedToByIdAndName() throws Exception {
        AgentProfile admin = staff("Al Admin " + unique, true);
        AgentProfile inactive = staff("Ina Inactive " + unique, false);
        inactive.setActive(false);
        agents.save(inactive);
        AgentProfile neither = staff("Nell Neither " + unique, false);
        neither.setAgent(false);
        agents.save(neither);
        Ticket ticket = ticket("Reassign me " + unique, "cass-" + unique + "@example.com", null);

        JsonNode list = showProps(ticket).get("agents");

        assertThat(list.isArray()).isTrue();
        JsonNode mine = byId(list, agent.getId());
        assertThat(fieldNames(mine)).containsExactly("id", "name");
        assertThat(mine.get("name").asText()).isEqualTo(agent.getName());
        assertThat(byId(list, admin.getId()).get("name").asText()).isEqualTo(admin.getName());
        assertThat(ids(list)).doesNotContain(inactive.getId(), neither.getId());
    }

    @Test
    void leavesOutAgentsWhoCannotSeeTheTicketUnderDepartmentVisibility() throws Exception {
        Department department = department();
        agent.setDepartment(department);
        agents.save(agent);
        AgentProfile other = staff("Otto Other " + unique, false);
        other.setDepartment(department());
        agents.save(other);
        AgentProfile admin = staff("Al Admin " + unique, true);
        Ticket ticket = ticket("Billing question " + unique, "cass-" + unique + "@example.com", department.getId());
        properties.setVisibility("department");

        List<Long> ids = ids(showProps(ticket).get("agents"));

        assertThat(ids).contains(agent.getId(), admin.getId()).doesNotContain(other.getId());
    }

    // --- The requester's other tickets --------------------------------------

    @Test
    void sendsTheRequestersOtherTicketsNewestFirstAtMostFive() throws Exception {
        String email = "cass-" + unique + "@example.com";
        List<Ticket> older = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            older.add(ticket("Earlier " + i + " " + unique, email, null));
        }
        ticket("Someone else " + unique, "else-" + unique + "@example.com", null);
        Ticket current = ticket("Current " + unique, email, null);

        JsonNode recent = showProps(current).get("ticket").get("requester_tickets");

        List<String> subjects = new ArrayList<>();
        recent.forEach(row -> subjects.add(row.get("subject").asText()));
        assertThat(subjects).containsExactly("Earlier 6 " + unique, "Earlier 5 " + unique, "Earlier 4 " + unique,
                "Earlier 3 " + unique, "Earlier 2 " + unique);
        JsonNode first = recent.get(0);
        assertThat(fieldNames(first)).containsExactly("id", "reference", "subject", "status", "created_at", "updated_at");
        assertThat(first.get("id").asLong()).isEqualTo(older.get(5).getId());
        assertThat(first.get("reference").asText()).isEqualTo(older.get(5).getTicketNumber());
        assertThat(first.get("status").asText()).isEqualTo("open");
        assertThat(Instant.parse(first.get("created_at").asText())).isNotNull();
        assertThat(Instant.parse(first.get("updated_at").asText())).isNotNull();
    }

    @Test
    void matchesTheRequestersOtherTicketsByEmail() throws Exception {
        String email = "guest-" + unique + "@example.com";
        ticket("Earlier guest ticket " + unique, email, null);
        ticket("Another guest " + unique, "other-" + unique + "@example.com", null);
        Ticket current = ticket("Guest again " + unique, email, null);

        JsonNode recent = showProps(current).get("ticket").get("requester_tickets");

        assertThat(recent.size()).isEqualTo(1);
        assertThat(recent.get(0).get("subject").asText()).isEqualTo("Earlier guest ticket " + unique);
    }

    @Test
    void onlyListsRequesterTicketsTheAgentMaySee() throws Exception {
        Department department = department();
        agent.setDepartment(department);
        agents.save(agent);
        String email = "cass-" + unique + "@example.com";
        ticket("Visible " + unique, email, department.getId());
        ticket("Hidden " + unique, email, department().getId());
        Ticket current = ticket("Current " + unique, email, department.getId());
        properties.setVisibility("department");

        JsonNode recent = showProps(current).get("ticket").get("requester_tickets");

        assertThat(recent.size()).isEqualTo(1);
        assertThat(recent.get(0).get("subject").asText()).isEqualTo("Visible " + unique);
    }

    @Test
    void sendsAnEmptyListForATicketWithNoKnownRequester() throws Exception {
        ticket("No requester " + unique, null, null);
        Ticket current = ticket("Also no requester " + unique, null, null);

        JsonNode recent = showProps(current).get("ticket").get("requester_tickets");

        assertThat(recent.isArray()).isTrue();
        assertThat(recent.size()).isZero();
    }

    // --- Snoozing from the agent panel --------------------------------------

    @Test
    void letsAnAgentSnoozeATicket() throws Exception {
        Ticket ticket = ticket("Snooze me " + unique, "cass-" + unique + "@example.com", null);
        Instant until = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        MockHttpSession session = new MockHttpSession();
        String show = "/escalated/agent/tickets/" + ticket.getTicketNumber();

        mvc.perform(post(show + "/snooze").session(session).with(user(agent.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost" + show)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"snoozed_until\":\"" + until + "\"}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", show));

        Ticket snoozed = tickets.findById(ticket.getId());
        assertThat(snoozed.getSnoozedUntil()).isEqualTo(until);
        assertThat(snoozed.getStatus()).isEqualTo(TicketStatus.SNOOZED);
        assertThat(inertia(get(show).session(session)).get("props").get("flash").get("success").asText())
                .startsWith("Ticket snoozed until");
    }

    @Test
    void rejectsASnoozeTimeInThePast() throws Exception {
        Ticket ticket = ticket("Too late " + unique, "cass-" + unique + "@example.com", null);
        MockHttpSession session = new MockHttpSession();
        String show = "/escalated/agent/tickets/" + ticket.getTicketNumber();

        mvc.perform(post(show + "/snooze").session(session).with(user(agent.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost" + show)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"snoozed_until\":\"2001-01-01T00:00:00Z\"}"))
                .andExpect(status().isSeeOther());

        assertThat(inertia(get(show).session(session)).get("props").get("errors").get("snoozed_until").asText())
                .isEqualTo("The snoozed until field must be a date after now.");
        assertThat(tickets.findById(ticket.getId()).getSnoozedUntil()).isNull();
    }

    @Test
    void letsAnAgentUnsnoozeATicket() throws Exception {
        Ticket ticket = ticket("Wake me " + unique, "cass-" + unique + "@example.com", null);
        String show = "/escalated/agent/tickets/" + ticket.getTicketNumber();
        write(show + "/snooze", "{\"snoozed_until\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}")
                .andExpect(status().isSeeOther());

        write(show + "/unsnooze", "{}").andExpect(status().isSeeOther());

        Ticket woken = tickets.findById(ticket.getId());
        assertThat(woken.getSnoozedUntil()).isNull();
        assertThat(woken.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void doesNotLetACustomerSnoozeThroughTheAgentPanel() throws Exception {
        Ticket ticket = ticket("Not yours " + unique, "cass-" + unique + "@example.com", null);

        mvc.perform(post("/escalated/agent/tickets/" + ticket.getTicketNumber() + "/snooze")
                        .with(user("customer-" + unique + "@example.com")).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"snoozed_until\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}"))
                .andExpect(status().isForbidden());

        assertThat(tickets.findById(ticket.getId()).getSnoozedUntil()).isNull();
    }

    @Test
    void doesNotLetAnAgentSnoozeATicketOutsideTheirDepartments() throws Exception {
        agent.setDepartment(department());
        agents.save(agent);
        Ticket ticket = ticket("Elsewhere " + unique, "cass-" + unique + "@example.com", department().getId());
        properties.setVisibility("department");

        write("/escalated/agent/tickets/" + ticket.getTicketNumber() + "/snooze",
                "{\"snoozed_until\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}")
                .andExpect(status().isNotFound());

        properties.setVisibility("all");
        assertThat(tickets.findById(ticket.getId()).getSnoozedUntil()).isNull();
    }

    @Test
    void namesTheAgentSnoozeRoutesAndMapsThemToTheTicketEditPermission() {
        UiRoutes routes = new UiRoutes();
        assertThat(routes.url("escalated.agent.tickets.snooze", "ESC-1")).isEqualTo("/escalated/agent/tickets/ESC-1/snooze");
        assertThat(routes.url("escalated.agent.tickets.unsnooze", "ESC-1"))
                .isEqualTo("/escalated/agent/tickets/ESC-1/unsnooze");
        assertThat(RoutePermissions.mapped("POST", "/escalated/agent/tickets/ESC-1/snooze")).contains("ticket.edit");
        assertThat(RoutePermissions.mapped("POST", "/escalated/agent/tickets/ESC-1/unsnooze")).contains("ticket.edit");
    }

    // --- Helpers ------------------------------------------------------------

    private AgentProfile staff(String name, boolean admin) {
        AgentProfile profile = new AgentProfile();
        profile.setName(name);
        profile.setEmail(name.toLowerCase().replace(' ', '-') + "@example.com");
        profile.setAgent(!admin);
        profile.setAdmin(admin);
        return agents.save(profile);
    }

    private Department department() {
        Department department = new Department();
        department.setName("Dept " + UUID.randomUUID().toString().substring(0, 8));
        return departments.save(department);
    }

    private Ticket ticket(String subject, String email, Long departmentId) {
        return tickets.create(subject, "Body.", "Cass", email == null ? "" : email, TicketPriority.MEDIUM,
                departmentId);
    }

    private JsonNode showProps(Ticket ticket) throws Exception {
        return inertia(get("/escalated/agent/tickets/" + ticket.getTicketNumber())).get("props");
    }

    private ResultActions write(String path, String json) throws Exception {
        return mvc.perform(post(path).with(user(agent.getEmail())).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode inertia(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mvc.perform(request.with(user(agent.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    private static JsonNode byId(JsonNode array, Long id) {
        for (JsonNode item : array) {
            if (item.get("id").asLong() == id) {
                return item;
            }
        }
        throw new AssertionError(id + " not in " + array);
    }
}
