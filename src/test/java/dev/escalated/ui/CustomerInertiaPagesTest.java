package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.SatisfactionRatingService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The customer pages over the Inertia protocol: what each renders, what each write does, and whose tickets. */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class CustomerInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private TicketService tickets;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private AgentProfileRepository agents;
    @Autowired private DepartmentRepository departments;
    @Autowired private SatisfactionRatingService ratings;

    private String unique;
    private String customer;
    private Ticket ticket;
    private Ticket someoneElses;
    private AgentProfile agent;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        customer = "Customer-" + unique + "@Example.com";

        agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent.setTwoFactorSecret("TOTP" + unique);
        agent = agents.save(agent);

        ticket = tickets.create("My printer " + unique, "It is broken.", "Casey", customer.toLowerCase(),
                TicketPriority.HIGH, null);
        tickets.assign(ticket.getId(), agent.getId(), agent.getEmail());
        tickets.addReply(ticket.getId(), "We are on it.", agent.getName(), agent.getEmail(), "agent", false);
        tickets.addReply(ticket.getId(), "Secret staff note " + unique, agent.getName(), agent.getEmail(), "agent", true);
        ticket = tickets.findById(ticket.getId());

        someoneElses = tickets.create("Not yours " + unique, "Private.", "Other", "other-" + unique + "@example.com",
                TicketPriority.LOW, null);
    }

    @Test
    void theIndexListsOnlyTheCustomersOwnTickets() throws Exception {
        JsonNode page = inertia(get("/escalated").param("search", unique).param("assigned_to", "1"));

        assertThat(page.get("component").asText()).isEqualTo("Escalated/Customer/Index");
        JsonNode props = page.get("props");
        assertThat(props.get("tickets").get("total").asLong()).isEqualTo(1);
        JsonNode row = props.get("tickets").get("data").get(0);
        assertThat(row.get("reference").asText()).isEqualTo(ticket.getTicketNumber());
        assertThat(fieldNames(row)).contains("id", "reference", "subject", "status", "priority", "requester",
                "created_at", "updated_at");
        assertThat(row.get("assignee").get("email").asText()).isEmpty();
        assertThat(fieldNames(props.get("filters"))).containsExactly("search");
        assertThat(page.toString()).doesNotContain(agent.getEmail()).doesNotContain("TOTP" + unique);
    }

    @Test
    void theCreatePageOffersDepartmentsAndPriorities() throws Exception {
        Department department = new Department();
        department.setName("Sales " + unique);
        department = departments.save(department);

        JsonNode page = inertia(get("/escalated/create"));

        assertThat(page.get("component").asText()).isEqualTo("Escalated/Customer/Create");
        assertThat(page.get("props").get("priorities").toString()).contains("\"medium\"");
        assertThat(page.get("props").get("departments").toString()).contains("Sales " + unique);
    }

    @Test
    void storingATicketFilesItAsTheCustomerAndGoesToIt() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult result = mvc.perform(post("/escalated").session(session).with(user(customer)).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"New " + unique + "\",\"description\":\"Help\",\"priority\":\"low\","
                                + "\"department_id\":\"\",\"ticket_type\":\"question\",\"attachments\":[]}"))
                .andExpect(status().isSeeOther())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).matches("/escalated/[A-Z]+-\\d+");
        Ticket created = ticketRepository.findByTicketNumber(location.substring("/escalated/".length())).orElseThrow();
        assertThat(created.getRequesterEmail()).isEqualTo(customer);
        assertThat(created.getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(created.getTicketType()).isEqualTo("question");

        JsonNode page = inertia(get(location).session(session));
        assertThat(page.get("props").get("flash").get("success").asText()).isEqualTo("Ticket created.");
    }

    @Test
    void aMissingSubjectGoesBackWithErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated").session(session).with(user(customer)).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost/escalated/create")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Help\"}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/create"));

        JsonNode errors = inertia(get("/escalated/create").session(session)).get("props").get("errors");
        assertThat(errors.get("subject").asText()).isEqualTo("The subject field is required.");
    }

    @Test
    void theTicketShowsPublicRepliesOnlyAndNoStaffDetail() throws Exception {
        JsonNode page = inertia(get("/escalated/" + ticket.getTicketNumber()));

        assertThat(page.get("component").asText()).isEqualTo("Escalated/Customer/Show");
        JsonNode shown = page.get("props").get("ticket");
        assertThat(fieldNames(shown)).containsExactly("reference", "subject", "description", "status", "priority",
                "channel", "department", "attachments", "replies", "satisfaction_rating", "resolved_at",
                "closed_at", "created_at", "updated_at");
        assertThat(shown.get("replies").size()).isEqualTo(1);
        assertThat(fieldNames(shown.get("replies").get(0).get("author"))).containsExactly("name");
        assertThat(page.toString()).doesNotContain("Secret staff note").doesNotContain(agent.getEmail())
                .doesNotContain("guest_access").doesNotContain("TOTP" + unique);
    }

    @Test
    void someoneElsesTicketIsANotFoundForEveryRoute() throws Exception {
        String path = "/escalated/" + someoneElses.getTicketNumber();
        mvc.perform(get(path).with(user(customer)).header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isNotFound());
        for (String write : new String[] {"/reply", "/close", "/reopen", "/rate"}) {
            write(path + write, "{\"body\":\"x\",\"rating\":5}").andExpect(status().isNotFound());
        }
        assertThat(tickets.findById(someoneElses.getId()).getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void aReplyIsFiledAsTheRequester() throws Exception {
        String path = "/escalated/" + ticket.getTicketNumber();
        write(path + "/reply", "{\"body\":\"Still broken " + unique + "\",\"author_email\":\"x@evil.test\"}")
                .andExpect(status().isSeeOther());

        JsonNode latest = inertia(get(path)).get("props").get("ticket").get("replies").get(0);
        assertThat(latest.get("body").asText()).isEqualTo("Still broken " + unique);
        assertThat(latest.get("author").get("name").asText()).isEqualTo("Casey");
    }

    @Test
    void closeReopenAndRate() throws Exception {
        String path = "/escalated/" + ticket.getTicketNumber();

        write(path + "/rate", "{\"rating\":5}").andExpect(status().isSeeOther());
        assertThat(inertia(get(path)).get("props").get("ticket").get("satisfaction_rating").isNull()).isTrue();

        write(path + "/close", "{}").andExpect(status().isSeeOther());
        assertThat(tickets.findById(ticket.getId()).getStatus()).isEqualTo(TicketStatus.CLOSED);

        write(path + "/rate", "{\"rating\":4,\"comment\":\"Good\"}").andExpect(status().isSeeOther());
        JsonNode rating = inertia(get(path)).get("props").get("ticket").get("satisfaction_rating");
        assertThat(rating.get("rating").asInt()).isEqualTo(4);
        assertThat(rating.get("comment").asText()).isEqualTo("Good");
        assertThat(ratings.findByTicket(ticket.getId())).hasSize(1);

        write(path + "/reopen", "{}").andExpect(status().isSeeOther());
        assertThat(tickets.findById(ticket.getId()).getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void aRatingOutOfRangeIsAnError() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String path = "/escalated/" + ticket.getTicketNumber();
        mvc.perform(post(path + "/rate").session(session).with(user(customer)).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":9}"))
                .andExpect(status().isSeeOther());
        assertThat(inertia(get(path).session(session)).get("props").get("errors").get("rating").asText())
                .contains("between 1 and 5");
    }

    @Test
    void aCustomerIsNeitherAgentNorAdmin() throws Exception {
        JsonNode escalated = inertia(get("/escalated")).get("props").get("escalated");
        assertThat(escalated.get("is_agent").asBoolean()).isFalse();
        assertThat(escalated.get("is_admin").asBoolean()).isFalse();
        JsonNode user = inertia(get("/escalated")).get("props").get("auth").get("user");
        assertThat(user.get("id").isNull()).isTrue();
        assertThat(user.get("email").asText()).isEqualTo(customer);
    }

    private ResultActions write(String path, String json) throws Exception {
        return mvc.perform(post(path).with(user(customer)).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.with(user(customer))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
