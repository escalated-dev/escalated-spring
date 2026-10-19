package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.Department;
import dev.escalated.models.KnowledgeBaseArticle;
import dev.escalated.models.KnowledgeBaseCategory;
import dev.escalated.services.ChatSessionService;
import dev.escalated.services.DepartmentService;
import dev.escalated.services.KnowledgeBaseService;
import hostapp.HostApplication;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The requests the shared widget ({@code @escalated-dev/escalated} 0.12.x,
 * {@code EscalatedWidget.vue}) sends, exactly as it sends them, and the
 * response fields it reads. The whole filter chain runs.
 *
 * <p>Same properties as {@link WidgetEmbeddingTest}, so both share one context.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.widget.allowed-origins=https://shop.example.com, https://*.brand.test",
})
class WidgetApiContractTest {

    private static final String WIDGET = "/escalated/api/widget";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DepartmentService departments;
    @Autowired private KnowledgeBaseService knowledgeBase;
    @Autowired private ChatSessionService chats;
    @Autowired private dev.escalated.repositories.KnowledgeBaseCategoryRepository categories;

    private final ObjectMapper json = new ObjectMapper();

    // --- /config ---

    @Test
    void configHidesTheStatusTabAndListsDepartments() throws Exception {
        Department sales = departments.create("Sales " + unique(), null);

        mockMvc.perform(get(WIDGET + "/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status_lookup_enabled").value(false))
                .andExpect(jsonPath("$.guest_verification_required").doesNotExist())
                .andExpect(jsonPath("$.guest_tickets_enabled").value(true))
                .andExpect(jsonPath("$.departments[*].id", Matchers.hasItem(sales.getId().intValue())));
    }

    // --- Articles ---

    @Test
    void articleSearchAnswersWithTheArticlesKey() throws Exception {
        String word = "zq" + unique();
        KnowledgeBaseArticle published = knowledgeBase.createArticle(
                "Reset your " + word, "reset-" + word, "<p>Steps</p>", "How to reset", null, "Ada");
        knowledgeBase.publishArticle(published.getId());
        knowledgeBase.createArticle("Draft " + word, "draft-" + word, "secret", "draft", null, "Ada");

        mockMvc.perform(get(WIDGET + "/articles?q=" + word))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles.length()").value(1))
                .andExpect(jsonPath("$.articles[0].title").value("Reset your " + word))
                .andExpect(jsonPath("$.articles[0].slug").value("reset-" + word))
                .andExpect(jsonPath("$.articles[0].excerpt").value("How to reset"));

        mockMvc.perform(get(WIDGET + "/articles?q=z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles.length()").value(0));
    }

    @Test
    void anArticleIsReadBySlug() throws Exception {
        String word = unique();
        KnowledgeBaseCategory category = new KnowledgeBaseCategory();
        category.setName("Accounts " + word);
        category.setSlug("accounts-" + word);
        category = categories.save(category);
        KnowledgeBaseArticle article = knowledgeBase.createArticle(
                "Billing " + word, "billing-" + word, "<p>Pay</p>", "Pay", category.getId(), "Ada");
        knowledgeBase.publishArticle(article.getId());
        knowledgeBase.createArticle("Hidden " + word, "hidden-" + word, "x", "x", null, "Ada");

        mockMvc.perform(get(WIDGET + "/articles/billing-" + word))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Billing " + word))
                .andExpect(jsonPath("$.slug").value("billing-" + word))
                .andExpect(jsonPath("$.body").value("<p>Pay</p>"))
                .andExpect(jsonPath("$.category").value("Accounts " + word));

        mockMvc.perform(get(WIDGET + "/articles/hidden-" + word)).andExpect(status().isNotFound());
        mockMvc.perform(get(WIDGET + "/articles/no-such-" + word)).andExpect(status().isNotFound());
    }

    // --- Tickets ---

    @Test
    void aWidgetTicketKeepsItsDescriptionAndAnswersWithAReference() throws Exception {
        Department support = departments.create("Support " + unique(), null);
        String body = json.writeValueAsString(java.util.Map.of(
                "name", "Grace",
                "email", "grace-" + unique() + "@example.com",
                "subject", "Order missing",
                "description", "It never arrived.",
                "department_id", support.getId()));

        JsonNode answer = json.readTree(mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").isString())
                .andReturn().getResponse().getContentAsString());

        String reference = answer.get("reference").asText();
        String token = answer.get("guest_access_token").asText();
        assertThat(reference).isNotBlank();
        assertThat(token).isNotBlank();
        // Existing clients read the entity fields; they stay.
        assertThat(answer.get("guestAccessToken").asText()).isEqualTo(token);
        assertThat(answer.get("ticketNumber").asText()).isEqualTo(reference);

        var row = jdbc.queryForMap(
                "SELECT body, department_id FROM escalated_tickets WHERE ticket_number = ?", reference);
        assertThat(row.get("body")).isEqualTo("It never arrived.");
        assertThat(((Number) row.get("department_id")).longValue()).isEqualTo(support.getId());
    }

    @Test
    void anUnknownDepartmentIsIgnored() throws Exception {
        JsonNode answer = json.readTree(mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"G\",\"email\":\"g-" + unique() + "@example.com\","
                                + "\"subject\":\"S\",\"description\":\"D\",\"department_id\":987654321}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        Object department = jdbc.queryForObject(
                "SELECT department_id FROM escalated_tickets WHERE ticket_number = ?",
                Object.class, answer.get("reference").asText());
        assertThat(department).isNull();
    }

    @Test
    void aTicketWithoutASubjectIsAValidationError() throws Exception {
        mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"G\",\"email\":\"g@example.com\",\"description\":\"D\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.errors.subject[0]").isString());
    }

    // --- Chat ---

    @Test
    void chatStartAnswersWithAnUnguessableId() throws Exception {
        JsonNode session = startChat("Hello there");

        String id = session.get("id").asText();
        assertThat(session.get("id").isTextual()).isTrue();
        assertThat(id).hasSizeGreaterThanOrEqualTo(32).doesNotMatch("[0-9]+");
        assertThat(session.get("status").asText()).isIn("waiting", "active");
        assertThat(session.get("messages").isArray()).isTrue();

        // Only a hash of the token is stored.
        Integer raw = jdbc.queryForObject(
                "SELECT COUNT(*) FROM escalated_chat_sessions WHERE widget_token_hash = ?", Integer.class, id);
        assertThat(raw).isZero();
    }

    @Test
    void theWidgetFollowsAConversationByItsToken() throws Exception {
        JsonNode session = startChat("My order is late");
        String id = session.get("id").asText();
        long sessionId = sessionIdOf(session);

        mockMvc.perform(post(WIDGET + "/chat/" + id + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Order 42\"}"))
                .andExpect(status().is2xxSuccessful());
        chats.sendMessage(sessionId, "Looking now", "Ada Agent", "ada@example.com", "agent");
        mockMvc.perform(post(WIDGET + "/chat/" + id + "/typing")).andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(WIDGET + "/chat/" + id + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].body").value("My order is late"))
                .andExpect(jsonPath("$.messages[0].is_agent").value(false))
                .andExpect(jsonPath("$.messages[0].author.name").value("Visitor Vi"))
                .andExpect(jsonPath("$.messages[1].body").value("Order 42"))
                .andExpect(jsonPath("$.messages[1].is_agent").value(false))
                .andExpect(jsonPath("$.messages[1].author.name").value("Visitor Vi"))
                .andExpect(jsonPath("$.messages[2].body").value("Looking now"))
                .andExpect(jsonPath("$.messages[2].is_agent").value(true))
                .andExpect(jsonPath("$.messages[2].author.name").value("Ada Agent"))
                .andExpect(jsonPath("$.messages[2].created_at").exists())
                .andExpect(jsonPath("$.messages[2].id").exists())
                .andExpect(jsonPath("$.ended").value(false));

        mockMvc.perform(post(WIDGET + "/chat/" + id + "/end")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(WIDGET + "/chat/" + id + "/messages"))
                .andExpect(jsonPath("$.ended").value(true));

        mockMvc.perform(post(WIDGET + "/chat/" + id + "/rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Quick\"}"))
                .andExpect(status().is2xxSuccessful());
        Integer rating = jdbc.queryForObject(
                "SELECT rating FROM escalated_satisfaction_ratings WHERE ticket_id = ?",
                Integer.class, session.get("ticketId").asLong());
        assertThat(rating).isEqualTo(5);
    }

    @Test
    void aRatingOutsideOneToFiveIsAValidationError() throws Exception {
        String id = startChat(null).get("id").asText();
        mockMvc.perform(post(WIDGET + "/chat/" + id + "/rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":9}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errors.rating[0]").isString());
    }

    // --- Security: chat routes are keyed by the token only ---

    @Test
    void theNumericSessionIdNoLongerReachesAChat() throws Exception {
        JsonNode session = startChat("Private matter");
        long sessionId = sessionIdOf(session);

        mockMvc.perform(post(WIDGET + "/chat/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"injected\",\"name\":\"Mallory\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(WIDGET + "/chat/" + sessionId + "/messages")).andExpect(status().isNotFound());
        mockMvc.perform(post(WIDGET + "/chat/" + sessionId + "/end")).andExpect(status().isNotFound());
        mockMvc.perform(post(WIDGET + "/chat/" + sessionId + "/typing")).andExpect(status().isNotFound());
        mockMvc.perform(post(WIDGET + "/chat/" + sessionId + "/rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":1}"))
                .andExpect(status().isNotFound());

        String status = jdbc.queryForObject(
                "SELECT status FROM escalated_chat_sessions WHERE id = ?", String.class, sessionId);
        assertThat(status).isNotEqualTo("ended");
        Integer injected = jdbc.queryForObject(
                "SELECT COUNT(*) FROM escalated_replies WHERE body = 'injected'", Integer.class);
        assertThat(injected).isZero();
    }

    @Test
    void anUnknownTokenIsNotFound() throws Exception {
        mockMvc.perform(get(WIDGET + "/chat/" + "a".repeat(43) + "/messages")).andExpect(status().isNotFound());
    }

    // --- helpers ---

    private JsonNode startChat(String message) throws Exception {
        String body = "{\"name\":\"Visitor Vi\",\"email\":\"vi-" + unique() + "@example.com\","
                + "\"department_id\":null"
                + (message == null ? "" : ",\"message\":" + json.writeValueAsString(message)) + "}";
        return json.readTree(mockMvc.perform(guest(post(WIDGET + "/chat/start"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long sessionIdOf(JsonNode session) {
        return jdbc.queryForObject("SELECT id FROM escalated_chat_sessions WHERE ticket_id = ?",
                Long.class, session.get("ticketId").asLong());
    }

    /** Each ticket-creating request comes from its own address, clear of the rate limit. */
    private static MockHttpServletRequestBuilder guest(MockHttpServletRequestBuilder builder) {
        int n = NEXT_IP.getAndIncrement();
        String ip = "198.18." + (n / 250) + "." + (n % 250 + 1);
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
