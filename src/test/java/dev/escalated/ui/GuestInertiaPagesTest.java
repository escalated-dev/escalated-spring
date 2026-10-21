package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.Department;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.GuestAccessGrants;
import dev.escalated.services.GuestVerificationService;
import dev.escalated.services.SettingsService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The guest pages over the Inertia protocol, signed out: the submit form with a verified email,
 * the ticket opened by its grant, and the guest's reply and rating.
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
class GuestInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private GuestVerificationService verification;
    @Autowired private GuestAccessGrants grants;
    @Autowired private TicketService tickets;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private DepartmentRepository departments;
    @Autowired private SettingsService settings;

    private String unique;
    private String email;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        email = "guest-" + unique + "@example.com";
    }

    @AfterEach
    void restore() {
        settings.set("guest_tickets_enabled", "1", "general");
    }

    @Test
    void theFormCarriesWhatTheCreatePageReadsWithoutSigningIn() throws Exception {
        Department department = new Department();
        department.setName("Sales " + unique);
        department = departments.save(department);

        MvcResult result = mvc.perform(get("/escalated/guest/create").header("X-Inertia", "true")
                        .header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();
        JsonNode page = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Guest/Create");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("departments", "priorities", "verification_url", "lookup_url");
        assertThat(props.get("verification_url").asText()).isEqualTo("/escalated/api/guest/verification");
        assertThat(props.get("lookup_url").asText()).isEqualTo("/escalated/api/guest/lookup");
        assertThat(props.get("priorities").toString()).contains("low", "medium", "high", "urgent");
        boolean listed = false;
        for (JsonNode row : props.get("departments")) {
            listed |= row.get("id").asLong() == department.getId();
        }
        assertThat(listed).isTrue();
        assertThat(props.get("auth").get("user").isNull()).isTrue();
    }

    @Test
    void aVerifiedGuestFilesATicketAndLandsOnItsPrivateLink() throws Exception {
        GuestVerificationService.Challenge challenge = verification.request(email, "ticket", "127.0.0.1");
        MockHttpSession session = new MockHttpSession();
        MvcResult created = mvc.perform(post("/escalated/guest").session(session).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guest_name":"Gail %s","guest_email":"%s","subject":"Locked out %s",
                                 "description":"Cannot sign in.","priority":"high","department_id":"",
                                 "attachments":[],"verification_id":"%s","verification_code":"%s"}
                                """.formatted(unique, email, unique, challenge.verificationId(), challenge.code())))
                .andExpect(status().isSeeOther())
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        assertThat(location).startsWith("/escalated/guest/" + GuestAccessGrants.PREFIX);

        Ticket ticket = ticketRepository.findAll().stream().filter(t -> t.getSubject().equals("Locked out " + unique))
                .findFirst().orElseThrow();
        assertThat(ticket.getRequesterEmail()).isEqualTo(email);
        assertThat(ticket.getRequesterName()).isEqualTo("Gail " + unique);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);

        JsonNode page = guest(get(location).session(session));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Guest/Show");
        JsonNode props = page.get("props");
        assertThat(props.get("token").asText()).isEqualTo(location.substring("/escalated/guest/".length()));
        assertThat(props.get("flash").get("success").asText())
                .isEqualTo("Ticket created. Save this link to check your ticket status.");
        JsonNode shown = props.get("ticket");
        assertThat(fieldNames(shown)).contains("reference", "subject", "description", "status", "priority",
                "department", "attachments", "replies", "satisfaction_rating", "guest_access_expires_at", "created_at");
        assertThat(shown.get("reference").asText()).isEqualTo(ticket.getTicketNumber());
        assertThat(shown.get("guest_access_expires_at").isTextual()).isTrue();
        assertThat(page.toString()).doesNotContain(ticket.getGuestAccessHash() == null ? "#none#" : ticket.getGuestAccessHash());
    }

    @Test
    void aWrongCodeFilesNothingAndGoesBackWithAnError() throws Exception {
        GuestVerificationService.Challenge challenge = verification.request(email, "ticket", "127.0.0.1");
        MockHttpSession session = new MockHttpSession();
        String wrong = challenge.code().equals("00000000") ? "11111111" : "00000000";
        mvc.perform(post("/escalated/guest").session(session).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/guest/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guest_name":"Gail","guest_email":"%s","subject":"Wrong %s","description":"x",
                                 "verification_id":"%s","verification_code":"%s"}
                                """.formatted(email, unique, challenge.verificationId(), wrong)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/guest/create"));
        JsonNode errors = guest(get("/escalated/guest/create").session(session)).get("props").get("errors");
        assertThat(errors.has("verification_code")).isTrue();
        assertThat(ticketRepository.findAll().stream().noneMatch(t -> t.getSubject().equals("Wrong " + unique))).isTrue();

        mvc.perform(post("/escalated/guest").session(session).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/guest/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guest_name\":\"Gail\",\"guest_email\":\"" + email + "\",\"subject\":\"No code\","
                                + "\"description\":\"x\"}"))
                .andExpect(status().isSeeOther());
        assertThat(guest(get("/escalated/guest/create").session(session)).get("props").get("errors")
                .has("verification_id")).isTrue();
    }

    @Test
    void theGuestRepliesAndRatesThroughTheirLink() throws Exception {
        Ticket ticket = tickets.create("Help " + unique, "Body", "Gail", email, TicketPriority.LOW, null);
        tickets.addReply(ticket.getId(), "Internal " + unique, "Agent", "agent@example.com", "agent", true);
        tickets.addReply(ticket.getId(), "Public " + unique, "Agent Smith", "agent@example.com", "agent", false);
        String token = grants.issue(ticket.getId()).token();
        String link = "/escalated/guest/" + token;

        JsonNode shown = guest(get(link)).get("props").get("ticket");
        assertThat(shown.toString()).contains("Public " + unique).doesNotContain("Internal " + unique)
                .doesNotContain("agent@example.com");
        assertThat(fieldNames(shown.get("replies").get(0))).contains("id", "body", "author", "attachments", "created_at");

        write(post(link + "/reply"), "{\"body\":\"Thanks " + unique + "\"}")
                .andExpect(status().isSeeOther()).andExpect(header().string("Location", link));
        assertThat(guest(get(link)).get("props").get("ticket").get("replies").get(0).get("body").asText())
                .isEqualTo("Thanks " + unique);

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post(link + "/rate").session(session).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isSeeOther());
        assertThat(guest(get(link).session(session)).get("props").get("flash").get("error").asText())
                .isEqualTo("Only resolved or closed tickets can be rated.");

        tickets.changeStatus(ticket.getId(), TicketStatus.RESOLVED, "agent@example.com");
        write(post(link + "/rate"), "{\"rating\":4,\"comment\":\"Quick\"}").andExpect(status().isSeeOther());
        JsonNode rating = guest(get(link)).get("props").get("ticket").get("satisfaction_rating");
        assertThat(rating.get("rating").asInt()).isEqualTo(4);
        assertThat(rating.get("comment").asText()).isEqualTo("Quick");

        tickets.changeStatus(ticket.getId(), TicketStatus.CLOSED, "agent@example.com");
        MockHttpSession closed = new MockHttpSession();
        mvc.perform(post(link + "/reply").session(closed).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Late\"}"))
                .andExpect(status().isSeeOther());
        assertThat(guest(get(link).session(closed)).get("props").get("flash").get("error").asText())
                .isEqualTo("This ticket is closed.");
    }

    @Test
    void anUnknownRevokedOrForeignLinkIsANotFound() throws Exception {
        Ticket ticket = tickets.create("Mine " + unique, "Body", "Gail", email, TicketPriority.LOW, null);
        String token = grants.issue(ticket.getId()).token();
        mvc.perform(get("/escalated/guest/eg1.not-a-real-grant").header("X-Inertia", "true")
                        .header("X-Inertia-Version", "build-1"))
                .andExpect(status().isNotFound());

        grants.revoke(ticket.getId());
        mvc.perform(get("/escalated/guest/" + token).header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isNotFound());
        write(post("/escalated/guest/" + token + "/reply"), "{\"body\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test
    void theFormIsANotFoundWhileGuestTicketsAreOff() throws Exception {
        settings.set("guest_tickets_enabled", "0", "general");
        mvc.perform(get("/escalated/guest/create").header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isNotFound());
        write(post("/escalated/guest"), "{\"guest_name\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test
    void aGuestWriteWithoutTheCsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/escalated/guest").header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode guest(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
