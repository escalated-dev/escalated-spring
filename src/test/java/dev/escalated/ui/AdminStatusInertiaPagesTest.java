package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.CustomTicketStatus;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.CustomTicketStatusRepository;
import dev.escalated.repositories.TicketRepository;
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

/** The custom ticket status pages over the Inertia protocol, and the ticket pages that use them. */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class AdminStatusInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/escalated/admin/statuses";

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private CustomTicketStatusRepository statuses;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketService tickets;

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
    }

    @Test
    void anAgentWhoIsNotAnAdminIsRefused() throws Exception {
        for (String path : new String[] {BASE, BASE + "/create"}) {
            mvc.perform(get(path).with(user(agent.getEmail()))).andExpect(status().isForbidden());
        }
        mvc.perform(post(BASE).with(user(agent.getEmail())).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Sneaky " + unique + "\",\"category\":\"open\",\"color\":\"#000\"}"))
                .andExpect(status().isForbidden());
        assertThat(statuses.findAll()).noneMatch(s -> s.getLabel().equals("Sneaky " + unique));
    }

    @Test
    void theIndexListsEveryStatusWithTheSystemRowsAndTheCategories() throws Exception {
        JsonNode page = inertia(get(BASE));

        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Statuses/Index");
        JsonNode props = page.get("props");
        assertThat(props.get("categories").toString()).isEqualTo("[\"new\",\"open\",\"pending\",\"on_hold\",\"solved\"]");
        JsonNode open = bySlug(props.get("statuses"), "open");
        assertThat(fieldNames(open)).containsExactly("id", "label", "slug", "category", "color", "description",
                "position", "is_default", "is_system", "created_at", "updated_at");
        assertThat(open.get("is_system").asBoolean()).isTrue();
        assertThat(open.get("category").asText()).isEqualTo("open");
        assertThat(bySlug(props.get("statuses"), "pending").get("category").asText()).isEqualTo("pending");
        assertThat(bySlug(props.get("statuses"), "snoozed").get("category").asText()).isEqualTo("on_hold");
        assertThat(bySlug(props.get("statuses"), "in_progress").get("category").asText()).isEqualTo("open");
        for (String solved : new String[] {"resolved", "closed", "merged"}) {
            assertThat(bySlug(props.get("statuses"), solved).get("category").asText()).isEqualTo("solved");
        }

        // Ordered by category, then position.
        String previous = "";
        for (JsonNode row : props.get("statuses")) {
            assertThat(row.get("category").asText()).isGreaterThanOrEqualTo(previous);
            previous = row.get("category").asText();
        }
    }

    @Test
    void aCustomStatusIsCreatedEditedAndDeleted() throws Exception {
        JsonNode create = inertia(get(BASE + "/create"));
        assertThat(create.get("component").asText()).isEqualTo("Escalated/Admin/Statuses/Form");
        assertThat(fieldNames(create.get("props"))).contains("categories").doesNotContain("status");
        assertThat(create.get("props").get("categories").size()).isEqualTo(5);

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post(BASE).session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Waiting on Parts %s","category":"on_hold","color":"#f97316",
                                 "description":"Hardware on order","position":3,"is_default":false}
                                """.formatted(unique)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", BASE));
        assertThat(inertia(get(BASE).session(session)).get("props").get("flash").get("success").asText())
                .isEqualTo("Status created.");
        CustomTicketStatus created = labelled("Waiting on Parts " + unique);
        assertThat(created.getSlug()).isEqualTo("waiting_on_parts_" + unique);
        assertThat(created.getCategory()).isEqualTo("on_hold");
        assertThat(created.getColor()).isEqualTo("#f97316");
        assertThat(created.getDescription()).isEqualTo("Hardware on order");
        assertThat(created.getPosition()).isEqualTo(3);
        assertThat(created.isSystem()).isFalse();

        // The same label again gets the next free slug.
        write(post(BASE), "{\"label\":\"waiting on parts " + unique + "\",\"category\":\"open\",\"color\":\"#000\"}")
                .andExpect(status().isSeeOther());
        assertThat(labelled("waiting on parts " + unique).getSlug()).isEqualTo("waiting_on_parts_" + unique + "_2");

        JsonNode edit = inertia(get(BASE + "/" + created.getId() + "/edit"));
        assertThat(edit.get("component").asText()).isEqualTo("Escalated/Admin/Statuses/Form");
        assertThat(fieldNames(edit.get("props"))).contains("status", "categories");
        assertThat(fieldNames(edit.get("props").get("status"))).containsExactly("id", "label", "slug", "category",
                "color", "description", "position", "is_default", "is_system", "created_at", "updated_at");
        assertThat(edit.get("props").get("status").get("slug").asText()).isEqualTo(created.getSlug());

        // Moving a ticket's custom status to another category leaves the ticket as it is.
        Ticket ticket = newTicket();
        tickets.changeStatusByName(ticket.getId(), created.getSlug(), admin.getEmail());
        write(put(BASE + "/" + created.getId()), """
                {"label":"Parts ordered %s","category":"pending","color":"#123456","description":"",
                 "position":1,"is_default":true}
                """.formatted(unique))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", BASE));
        CustomTicketStatus updated = statuses.findById(created.getId()).orElseThrow();
        assertThat(updated.getLabel()).isEqualTo("Parts ordered " + unique);
        assertThat(updated.getSlug()).isEqualTo(created.getSlug());
        assertThat(updated.getCategory()).isEqualTo("pending");
        assertThat(updated.getDescription()).isNull();
        assertThat(updated.isDefaultStatus()).isTrue();
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.IN_PROGRESS);

        write(patch(BASE + "/" + created.getId()), """
                {"label":"Parts ordered %s","category":"pending","color":"#654321"}
                """.formatted(unique))
                .andExpect(status().isSeeOther());
        assertThat(statuses.findById(created.getId()).orElseThrow().getColor()).isEqualTo("#654321");

        write(delete(BASE + "/" + created.getId()), "{}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", BASE));
        assertThat(statuses.existsById(created.getId())).isFalse();
        Ticket kept = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(kept.getCustomStatus()).isNull();
        assertThat(kept.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void oneDefaultPerCategory() throws Exception {
        write(post(BASE), "{\"label\":\"First " + unique + "\",\"category\":\"new\",\"color\":\"#000\",\"is_default\":true}")
                .andExpect(status().isSeeOther());
        write(post(BASE), "{\"label\":\"Second " + unique + "\",\"category\":\"new\",\"color\":\"#000\",\"is_default\":true}")
                .andExpect(status().isSeeOther());

        assertThat(labelled("First " + unique).isDefaultStatus()).isFalse();
        assertThat(labelled("Second " + unique).isDefaultStatus()).isTrue();
        assertThat(statuses.findByCategoryAndDefaultStatusTrue("new")).hasSize(1);
    }

    @Test
    void anInvalidStatusGoesBackWithErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post(BASE).session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost" + BASE + "/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Bad " + unique + "\",\"category\":\"limbo\",\"color\":\"#000\"}"))
                .andExpect(status().isSeeOther());
        JsonNode errors = inertia(get(BASE + "/create").session(session)).get("props").get("errors");
        assertThat(errors.get("category").asText()).isEqualTo("The selected category is invalid.");

        for (String body : new String[] {
            "{\"category\":\"open\",\"color\":\"#000\"}",
            "{\"label\":\"Bad " + unique + "\",\"category\":\"open\"}",
            "{\"label\":\"Bad " + unique + "\",\"category\":\"open\",\"color\":\"#000000000000000000000\"}",
            "{\"label\":\"Bad " + unique + "\",\"category\":\"open\",\"color\":\"#000\",\"position\":\"top\"}",
            "{\"label\":\"Bad " + unique + "\",\"category\":\"open\",\"color\":\"#000\",\"is_default\":\"maybe\"}",
        }) {
            write(post(BASE), body).andExpect(status().isSeeOther());
        }
        assertThat(statuses.findAll()).noneMatch(s -> s.getLabel().equals("Bad " + unique));
    }

    @Test
    void aSystemRowKeepsItsSlugAndCategoryAndCannotBeDeleted() throws Exception {
        inertia(get(BASE));
        CustomTicketStatus resolved = statuses.findBySlug("resolved").orElseThrow();
        String label = resolved.getLabel();

        MockHttpSession session = new MockHttpSession();
        mvc.perform(put(BASE + "/" + resolved.getId()).session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost" + BASE + "/" + resolved.getId() + "/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Done\",\"category\":\"open\",\"color\":\"#000\"}"))
                .andExpect(status().isSeeOther());
        JsonNode errors = inertia(get(BASE + "/" + resolved.getId() + "/edit").session(session))
                .get("props").get("errors");
        assertThat(errors.has("category")).isTrue();
        assertThat(statuses.findById(resolved.getId()).orElseThrow().getCategory()).isEqualTo("solved");

        try {
            write(put(BASE + "/" + resolved.getId()), "{\"label\":\"Done " + unique
                    + "\",\"category\":\"solved\",\"color\":\"#00ff00\",\"description\":\"All good\"}")
                    .andExpect(status().isSeeOther());
            CustomTicketStatus relabelled = statuses.findById(resolved.getId()).orElseThrow();
            assertThat(relabelled.getLabel()).isEqualTo("Done " + unique);
            assertThat(relabelled.getSlug()).isEqualTo("resolved");
            assertThat(relabelled.isSystem()).isTrue();

            MockHttpSession deleting = new MockHttpSession();
            mvc.perform(delete(BASE + "/" + resolved.getId()).session(deleting).with(user(admin.getEmail()))
                            .with(UiTestSupport.xsrf()).header("X-Inertia", "true"))
                    .andExpect(status().isSeeOther())
                    .andExpect(header().string("Location", BASE));
            assertThat(statuses.existsById(resolved.getId())).isTrue();
            assertThat(inertia(get(BASE).session(deleting)).get("props").get("flash").get("error").asText())
                    .isEqualTo("Built-in statuses cannot be deleted.");
        } finally {
            CustomTicketStatus restore = statuses.findById(resolved.getId()).orElseThrow();
            restore.setLabel(label);
            restore.setDescription(null);
            statuses.save(restore);
        }
    }

    @Test
    void theTicketPagesSetAndShowACustomStatusAndTheCustomerNeverSeesIt() throws Exception {
        write(post(BASE), "{\"label\":\"Vendor " + unique + "\",\"category\":\"on_hold\",\"color\":\"#abcdef\"}")
                .andExpect(status().isSeeOther());
        CustomTicketStatus vendor = labelled("Vendor " + unique);
        Ticket ticket = newTicket();
        String show = "/escalated/admin/tickets/" + ticket.getTicketNumber();

        write(post(show + "/status"), "{\"status\":\"" + vendor.getSlug() + "\"}").andExpect(status().isSeeOther());

        JsonNode shown = inertia(get(show)).get("props").get("ticket");
        assertThat(shown.get("status").asText()).isEqualTo("in_progress");
        assertThat(shown.get("status_category").asText()).isEqualTo("on_hold");
        assertThat(fieldNames(shown.get("custom_status"))).containsExactly("id", "label", "slug", "color", "category");
        assertThat(shown.get("custom_status").get("label").asText()).isEqualTo("Vendor " + unique);
        assertThat(shown.get("custom_status").get("color").asText()).isEqualTo("#abcdef");

        JsonNode row = inertia(get("/escalated/admin/tickets").param("search", unique)).get("props")
                .get("tickets").get("data").get(0);
        assertThat(row.get("custom_status").get("slug").asText()).isEqualTo(vendor.getSlug());

        // The customer's page shows the built-in status only.
        MvcResult customer = mvc.perform(get("/escalated/" + ticket.getTicketNumber())
                        .with(user(ticket.getRequesterEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode customerTicket = JSON.readTree(customer.getResponse().getContentAsString()).get("props").get("ticket");
        assertThat(customerTicket.get("status").asText()).isEqualTo("in_progress");
        assertThat(customerTicket.toString()).doesNotContain("Vendor " + unique).doesNotContain(vendor.getSlug())
                .doesNotContain("custom_status");

        // An unknown status is still refused, and a built-in one clears the custom status.
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post(show + "/status").session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost" + show)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"limbo_" + unique + "\"}"))
                .andExpect(status().isSeeOther());
        assertThat(inertia(get(show).session(session)).get("props").get("errors").get("status").asText())
                .isEqualTo("The selected status is invalid.");
        write(post(show + "/status"), "{\"status\":\"open\"}").andExpect(status().isSeeOther());
        Ticket reopened = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(reopened.getCustomStatus()).isNull();

        // The bulk action takes a custom slug too.
        write(post("/escalated/admin/tickets/bulk"), "{\"ticket_ids\":[" + ticket.getId() + "],\"action\":\"status\","
                + "\"value\":\"" + vendor.getSlug() + "\"}").andExpect(status().isSeeOther());
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getCustomStatus().getId())
                .isEqualTo(vendor.getId());
    }

    private Ticket newTicket() {
        return tickets.create("Status " + unique, "b", "Casey", "casey-" + unique + "@example.com",
                TicketPriority.LOW, null);
    }

    private CustomTicketStatus labelled(String label) {
        return statuses.findAll().stream().filter(s -> s.getLabel().equals(label)).findFirst().orElseThrow();
    }

    private static JsonNode bySlug(JsonNode rows, String slug) {
        for (JsonNode row : rows) {
            if (row.get("slug").asText().equals(slug)) {
                return row;
            }
        }
        throw new AssertionError("no status " + slug);
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
}
