package dev.escalated.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.TicketRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@SpringBootTest(classes = hostapp.HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"escalated.visibility=department", "spring.mail.host=localhost"})
@Transactional
class TicketVisibilityHttpTest {
    @Autowired MockMvc mvc;
    @Autowired TicketRepository tickets;
    @Autowired DepartmentRepository departments;
    @Autowired AgentProfileRepository agents;
    @Autowired EntityManager em;
    @Autowired dev.escalated.services.ApiTokenService tokens;
    String email;
    Long hiddenId;
    String hiddenToken;

    @BeforeEach
    void seed() {
        Department own = new Department();
        own.setName("Support");
        departments.save(own);
        Department other = new Department();
        other.setName("Delivery");
        departments.save(other);
        email = UUID.randomUUID() + "@example.test";
        AgentProfile agent = new AgentProfile();
        agent.setName("Agent");
        agent.setEmail(email);
        agent.setDepartment(own);
        agents.save(agent);
        ticket(own, "Visible");
        Ticket hidden = ticket(other, "Hidden");
        hiddenId = hidden.getId();
        hiddenToken = hidden.getGuestAccessToken();
        ticket(null, "No department");
        em.flush();
        em.clear();
    }

    private Ticket ticket(Department department, String subject) {
        Ticket ticket = new Ticket();
        ticket.setSubject(subject);
        ticket.setBody("Body");
        ticket.setDepartment(department);
        ticket.setTicketNumber(UUID.randomUUID().toString());
        ticket.setRequesterName("Customer");
        ticket.setRequesterEmail("customer@example.test");
        ticket.setGuestAccessToken(UUID.randomUUID().toString());
        return tickets.save(ticket);
    }

    @Test
    void listIsScopedBeforePagination() throws Exception {
        mvc.perform(get("/escalated/api/agent/tickets").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("Visible"));
    }

    @Test
    void detailIsHidden() throws Exception {
        mvc.perform(get("/escalated/api/agent/tickets/{id}", hiddenId).with(user(email)))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchCannotFindOtherDepartments() throws Exception {
        mvc.perform(get("/escalated/api/agent/tickets").param("query", "Hidden").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void bearerOwnerOverridesAnAdminSession() throws Exception {
        String token =
                (String)
                        tokens.createToken(
                                        "Access regression",
                                        agents.findByEmail(email).orElseThrow().getId(),
                                        "*",
                                        null)
                                .get("plainTextToken");
        mvc.perform(
                        get("/escalated/api/agent/tickets")
                                .with(user("admin@example.test").roles("ESCALATED_ADMIN"))
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void replyCannotCrossDepartments() throws Exception {
        mvc.perform(
                        post("/escalated/api/agent/tickets/{id}/replies", hiddenId)
                                .with(user(email))
                                .contentType("application/json")
                                .content(
                                        "{\"body\":\"Reply\",\"author_name\":\"Agent\",\"author_email\":\"agent@example.test\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void widgetDoesNotBypassAnAgentsScope() throws Exception {
        mvc.perform(get("/escalated/api/widget/tickets/{token}", hiddenToken).with(user(email)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousWidgetGrantStillWorks() throws Exception {
        mvc.perform(get("/escalated/api/widget/tickets/{token}", hiddenToken))
                .andExpect(status().isOk());
    }
}
