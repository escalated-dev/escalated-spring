package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.events.ChatEvent;
import dev.escalated.events.TicketEvent;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.ChatSession;
import dev.escalated.models.KnowledgeBaseArticle;
import dev.escalated.models.KnowledgeBaseCategory;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketFollower;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentNotificationRepository;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.CustomerPresenceRepository;
import dev.escalated.repositories.KnowledgeBaseArticleRepository;
import dev.escalated.repositories.KnowledgeBaseCategoryRepository;
import dev.escalated.repositories.TicketFollowerRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.AgentPanelService;
import dev.escalated.services.ImportContext;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The agent panel's data (escalated-developer-context ADR 2026-11-05-agent-panel-data, after
 * escalated-laravel #292-#296): agent status, knowledge-base search, unread markers, customer
 * presence and the agents' in-app notifications.
 *
 * <p>The suite shares one database, so each test works on tickets and people of its own. The
 * configuration is AgentInboxPropsTest's, so the cached context is reused; the knowledge base,
 * ticket visibility and notification switches are flipped on the properties bean and restored.
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
class AgentPanelDataTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketService tickets;
    @Autowired private TicketFollowerRepository followers;
    @Autowired private AgentNotificationRepository notifications;
    @Autowired private CustomerPresenceRepository presence;
    @Autowired private KnowledgeBaseArticleRepository articles;
    @Autowired private KnowledgeBaseCategoryRepository categories;
    @Autowired private AgentPanelService panel;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private DataSource dataSource;

    @Autowired private EscalatedProperties properties;
    @Autowired private dev.escalated.repositories.TicketReadRepository reads;
    @Autowired private dev.escalated.repositories.DepartmentRepository departments;
    @Autowired private org.springframework.context.ApplicationContext context;

    private String unique;
    private AgentProfile agent;
    private AgentProfile other;
    private String customer;

    @BeforeEach
    void seed() {
        unique = "panel" + UUID.randomUUID().toString().substring(0, 8);
        agent = agent("agent");
        other = agent("other");
        customer = "customer-" + unique + "@example.test";
    }

    // ---- Agent status --------------------------------------------------------------------------

    @Test
    void agentsSetTheirStatusAndEveryStaffPageSharesIt() throws Exception {
        JsonNode shared = page("/escalated/agent/tickets", agent).get("props").get("escalated");
        assertThat(shared.get("agent_status").asText()).isEqualTo("offline");
        assertThat(shared.get("agent_statuses").toString()).isEqualTo("[\"online\",\"away\",\"offline\"]");
        assertThat(shared.get("notifications").get("unread_count").asLong()).isZero();

        JsonNode body = json(mvc.perform(put("/escalated/agent/status").with(user(agent.getEmail()))
                .with(UiTestSupport.xsrf()).contentType("application/json").content("{\"status\":\"away\"}"))
                .andExpect(status().isOk()));
        assertThat(body.get("agent_status").asText()).isEqualTo("away");
        AgentProfile stored = agents.findById(agent.getId()).orElseThrow();
        assertThat(stored.getChatStatus()).isEqualTo("away");
        assertThat(stored.isAvailable()).isFalse();
        assertThat(page("/escalated/agent/tickets", agent).get("props").get("escalated").get("agent_status").asText())
                .isEqualTo("away");

        mvc.perform(post("/escalated/agent/status").with(user(agent.getEmail())).with(UiTestSupport.xsrf())
                        .contentType("application/json").content("{\"status\":\"online\"}"))
                .andExpect(status().isOk());
        assertThat(agents.findById(agent.getId()).orElseThrow().isAvailable()).isTrue();

        JsonNode invalid = json(mvc.perform(put("/escalated/agent/status").with(user(agent.getEmail()))
                .with(UiTestSupport.xsrf()).contentType("application/json").content("{\"status\":\"busy\"}"))
                .andExpect(status().isUnprocessableEntity()));
        assertThat(invalid.get("errors").has("status")).isTrue();

        // An Inertia visit goes back.
        mvc.perform(put("/escalated/agent/status").with(user(agent.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost/escalated/agent/tickets")
                        .contentType("application/json").content("{\"status\":\"away\"}"))
                .andExpect(status().isSeeOther());
    }

    @Test
    void customersHaveNoStatusNorPanelProps() throws Exception {
        mvc.perform(put("/escalated/agent/status").with(user(customer)).with(UiTestSupport.xsrf())
                        .contentType("application/json").content("{\"status\":\"away\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
        JsonNode shared = page("/escalated", customer).get("props").get("escalated");
        assertThat(shared.has("agent_status")).isFalse();
        assertThat(shared.has("agent_statuses")).isFalse();
        assertThat(shared.has("notifications")).isFalse();
    }

    // ---- Knowledge base search ----------------------------------------------------------------------

    @Test
    void knowledgeBaseSearchAnswersPublishedArticlesTitleMatchesFirst() throws Exception {
        boolean wasEnabled = properties.getKnowledgeBase().isEnabled();
        properties.getKnowledgeBase().setEnabled(true);
        try {
            searchesTheKnowledgeBase();
        } finally {
            properties.getKnowledgeBase().setEnabled(wasEnabled);
        }
        properties.getKnowledgeBase().setEnabled(false);
        try {
            kb("q=password", 404);
        } finally {
            properties.getKnowledgeBase().setEnabled(wasEnabled);
        }
    }

    private void searchesTheKnowledgeBase() throws Exception {
        KnowledgeBaseCategory category = new KnowledgeBaseCategory();
        category.setName("Accounts " + unique);
        category.setSlug("accounts-" + unique);
        category = categories.save(category);
        String word = "zq" + unique;
        KnowledgeBaseArticle bodyMatch = article("Basics " + unique, "<p>To reset your " + word + " open   Settings.</p>",
                true, category, Instant.now().minusSeconds(10));
        KnowledgeBaseArticle oldTitle = article(word + " rules", "x", true, null, Instant.now().minusSeconds(500));
        KnowledgeBaseArticle newTitle = article(word + " reset", "x", true, null, Instant.now().minusSeconds(100));
        article(word + " draft", "x", false, null, Instant.now());

        JsonNode data = kb("q=" + word, 200).get("data");
        assertThat(ids(data)).containsExactly(newTitle.getId(), oldTitle.getId(), bodyMatch.getId());
        JsonNode item = data.get(2);
        assertThat(AgentInertiaPagesTest.fieldNames(item))
                .containsExactly("id", "title", "excerpt", "url", "category", "updated_at");
        assertThat(item.get("excerpt").asText()).isEqualTo("To reset your " + word + " open Settings.");
        assertThat(item.get("url").asText()).isEqualTo("http://localhost/escalated/kb/" + bodyMatch.getSlug());
        assertThat(item.get("category").get("name").asText()).isEqualTo("Accounts " + unique);
        assertThat(data.get(0).get("category").isNull()).isTrue();

        // Case-insensitive, with % and _ taken literally.
        KnowledgeBaseArticle percent = article("Save 50% " + unique, "x", true, null, Instant.now());
        article("Save 50 dollars " + unique, "x", true, null, Instant.now());
        KnowledgeBaseArticle underscore = article("snake_case " + unique, "x", true, null, Instant.now());
        article("snakeXcase " + unique, "x", true, null, Instant.now());
        assertThat(ids(kbQ("50% " + unique.toUpperCase()).get("data"))).containsExactly(percent.getId());
        assertThat(ids(kbQ("SNAKE_CASE " + unique).get("data"))).containsExactly(underscore.getId());

        for (String[] bad : new String[][] {
            {"q=a", "q"}, {"q=" + "a".repeat(201), "q"}, {"q=abc&limit=0", "limit"}, {"q=abc&limit=21", "limit"}}) {
            assertThat(kb(bad[0], 422).get("errors").has(bad[1])).as(bad[0]).isTrue();
        }
        assertThat(kb("q=" + word + "&limit=2", 200).get("data")).hasSize(2);
    }

    // ---- Unread markers ------------------------------------------------------------------------------

    @Test
    void aNewTicketIsUnreadUntilTheAgentOpensIt() throws Exception {
        Ticket ticket = created();
        assertThat(row(ticket, agent).get("is_unread").asBoolean()).isTrue();

        JsonNode show = page("/escalated/agent/tickets/" + ticket.getTicketNumber(), agent).get("props").get("ticket");
        assertThat(show.get("is_unread").asBoolean()).isFalse();
        assertThat(row(ticket, agent).get("is_unread").asBoolean()).isFalse();
        // Per agent.
        assertThat(row(ticket, other).get("is_unread").asBoolean()).isTrue();
    }

    @Test
    void customerRepliesMakeItUnreadAgainAgentsRepliesDoNot() throws Exception {
        Ticket ticket = created();
        page("/escalated/agent/tickets/" + ticket.getTicketNumber(), agent);
        backdate(ticket);

        tickets.addReply(ticket.getId(), "On it", other.getName(), other.getEmail(), "agent", false);
        tickets.addReply(ticket.getId(), "Note", other.getName(), other.getEmail(), "agent", true);
        tickets.addReply(ticket.getId(), "Automated", "System", "system", "system", false);
        assertThat(row(ticket, agent).get("is_unread").asBoolean()).isFalse();

        tickets.addReply(ticket.getId(), "Still broken", "Customer", customer, "customer", false);
        assertThat(row(ticket, agent).get("is_unread").asBoolean()).isTrue();
    }

    @Test
    void importedTicketsAreNotCustomerActivity() throws Exception {
        Ticket ticket = ImportContext.suppress(this::created);
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getLastCustomerActivityAt()).isNull();
        assertThat(row(ticket, agent).get("is_unread").asBoolean()).isFalse();
    }

    @Test
    void theReadAndUnreadRoutesAnswerIsUnread() throws Exception {
        Ticket ticket = created();
        String path = "/escalated/agent/tickets/" + ticket.getTicketNumber();
        assertThat(json(postAs(path + "/read", agent).andExpect(status().isOk())).toString())
                .isEqualTo("{\"is_unread\":false}");
        assertThat(row(ticket, agent).get("is_unread").asBoolean()).isFalse();
        assertThat(json(postAs(path + "/unread", agent).andExpect(status().isOk())).toString())
                .isEqualTo("{\"is_unread\":true}");
        assertThat(row(ticket, agent).get("is_unread").asBoolean()).isTrue();
        postAs("/escalated/agent/tickets/NOPE-1/read", agent).andExpect(status().isNotFound());
    }

    @Test
    void inboxUnreadCountsUnreadOpenTicketsPerQueue() throws Exception {
        JsonNode before = inbox();
        Ticket mine = created();
        Ticket minePending = created();
        tickets.assign(mine.getId(), agent.getId(), other.getEmail());
        tickets.assign(minePending.getId(), agent.getId(), other.getEmail());
        Ticket unassigned = created();
        Ticket resolved = created();
        tickets.changeStatusByName(resolved.getId(), "resolved", other.getEmail());
        page("/escalated/agent/tickets/" + mine.getTicketNumber(), agent);
        page("/escalated/agent/tickets/" + unassigned.getTicketNumber(), agent);

        JsonNode after = inbox();
        assertThat(AgentInertiaPagesTest.fieldNames(after)).containsExactly("counts", "unread", "views", "stats");
        assertThat(after.get("unread").get("mine").asLong()).isEqualTo(1);
        assertThat(delta(before, after, "unassigned")).isZero();
        assertThat(delta(before, after, "all")).isEqualTo(1);
    }

    // ---- Customer presence --------------------------------------------------------------------------

    @Test
    void aCustomerOnThePortalShowsOnlineOnAgentPagesOnly() throws Exception {
        Ticket ticket = created();
        JsonNode row = row(ticket, agent);
        assertThat(row.get("requester_online").asBoolean()).isFalse();
        assertThat(row.get("requester_last_seen_at").isNull()).isTrue();

        MvcResult customerPage = mvc.perform(get("/escalated/" + ticket.getTicketNumber()).with(user(customer))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk()).andReturn();
        String body = customerPage.getResponse().getContentAsString();
        assertThat(body).doesNotContain("requester_online").doesNotContain("requester_last_seen_at")
                .doesNotContain("is_unread");

        row = row(ticket, agent);
        assertThat(row.get("requester_online").asBoolean()).isTrue();
        assertThat(row.get("requester_last_seen_at").isNull()).isFalse();
        assertThat(page("/escalated/agent/tickets/" + ticket.getTicketNumber(), agent)
                .get("props").get("ticket").get("requester_online").asBoolean()).isTrue();

        // Throttled: a visit within 30 seconds writes nothing.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Timestamp recent = Timestamp.from(Instant.now().minusSeconds(10).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        jdbc.update("UPDATE escalated_customer_presence SET last_seen_at = ? WHERE email = ?", recent, customer);
        page("/escalated", customer);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM escalated_customer_presence WHERE email = ? AND last_seen_at = ?",
                Long.class, customer, recent)).isEqualTo(1);
        jdbc.update("UPDATE escalated_customer_presence SET last_seen_at = ? WHERE email = ?",
                Timestamp.from(Instant.now().minusSeconds(40)), customer);
        page("/escalated", customer);
        assertThat(presence.findByEmailIn(List.of(customer)).get(0).getLastSeenAt())
                .isAfter(Instant.now().minusSeconds(10));
        assertThat(presence.findByEmailIn(List.of(customer))).hasSize(1);

        // Outside the window: offline, with the time.
        jdbc.update("UPDATE escalated_customer_presence SET last_seen_at = ? WHERE email = ?",
                Timestamp.from(Instant.now().minusSeconds(600)), customer);
        row = row(ticket, agent);
        assertThat(row.get("requester_online").asBoolean()).isFalse();
        assertThat(row.get("requester_last_seen_at").isNull()).isFalse();
    }

    @Test
    void staffBrowsingThePortalAreNotRecorded() throws Exception {
        page("/escalated", agent);
        assertThat(presence.findByEmailIn(List.of(agent.getEmail()))).isEmpty();
    }

    // ---- Notifications --------------------------------------------------------------------------------

    @Test
    void assignmentBySomeoneElseNotifiesAssigningYourselfDoesNot() throws Exception {
        Ticket ticket = created();
        tickets.assign(ticket.getId(), agent.getId(), agent.getEmail());
        assertThat(notifications.countByAgentId(agent.getId())).isZero();

        tickets.assign(ticket.getId(), agent.getId(), other.getEmail());
        JsonNode body = feed(agent, "");
        assertThat(body.get("meta").toString())
                .isEqualTo("{\"current_page\":1,\"last_page\":1,\"per_page\":20,\"total\":1}");
        JsonNode item = body.get("data").get(0);
        assertThat(AgentInertiaPagesTest.fieldNames(item))
                .containsExactly("id", "type", "title", "body", "ticket", "url", "created_at", "read_at");
        assertThat(item.get("type").asText()).isEqualTo("ticket_assigned");
        assertThat(item.get("title").asText()).isEqualTo(ticket.getTicketNumber() + " was assigned to you");
        assertThat(item.get("body").asText()).isEqualTo(ticket.getSubject());
        assertThat(item.get("ticket").get("reference").asText()).isEqualTo(ticket.getTicketNumber());
        assertThat(item.get("url").asText()).isEqualTo("/escalated/agent/tickets/" + ticket.getTicketNumber());
        assertThat(item.get("read_at").isNull()).isTrue();
        assertThat(page("/escalated/agent/tickets", agent).get("props").get("escalated")
                .get("notifications").get("unread_count").asLong()).isEqualTo(1);

    }

    @Test
    void eachNotificationIsSentToTheAgentsPrivateQueue() {
        SimpMessagingTemplate messaging = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        org.springframework.beans.factory.support.StaticListableBeanFactory beans =
                new org.springframework.beans.factory.support.StaticListableBeanFactory(Map.of("messaging", messaging));
        AgentPanelService broadcasting = new AgentPanelService(ticketRepository,
                context.getBean(dev.escalated.repositories.TicketReadRepository.class), presence, notifications, agents,
                followers, articles, properties, context.getBeanProvider(dev.escalated.security.TicketAccess.class),
                beans.getBeanProvider(SimpMessagingTemplate.class),
                context.getBeanProvider(org.springframework.transaction.PlatformTransactionManager.class));
        Ticket ticket = created();
        broadcasting.notify(agent.getId(), "ticket_assigned", ticket, Map.of(), other.getEmail());

        verify(messaging).convertAndSendToUser(eq(agent.getEmail()), eq(AgentPanelService.NOTIFICATION_DESTINATION),
                argThat((Object payload) -> payload instanceof Map<?, ?> map
                        && "notification.created".equals(map.get("event"))
                        && map.get("data") instanceof Map<?, ?> item
                        && "ticket_assigned".equals(item.get("type"))));
    }

    @Test
    void underDepartmentVisibilityOnlyVisibleTicketsAreCountedAndMarkedAndOthersAreRedacted() throws Exception {
        dev.escalated.models.Department own = new dev.escalated.models.Department();
        own.setName("Own " + unique);
        departments.save(own);
        dev.escalated.models.Department hiddenDepartment = new dev.escalated.models.Department();
        hiddenDepartment.setName("Other " + unique);
        departments.save(hiddenDepartment);
        agent.setDepartment(own);
        agent = agents.save(agent);
        Ticket visible = tickets.create("Visible " + unique, "Body", "Customer", customer, TicketPriority.LOW, own.getId());
        Ticket hidden = tickets.create("Hidden " + unique, "Body", "Customer", customer, TicketPriority.LOW,
                hiddenDepartment.getId());
        panel.notify(agent.getId(), "ticket_assigned", hidden, Map.of(), null);

        String previous = properties.getVisibility();
        properties.setVisibility("department");
        try {
            JsonNode rows = page("/escalated/agent/tickets?search=" + unique, agent).get("props").get("tickets").get("data");
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("id").asLong()).isEqualTo(visible.getId());

            long before = inbox().get("unread").get("all").asLong();
            page("/escalated/agent/tickets/" + visible.getTicketNumber(), agent);
            assertThat(before - inbox().get("unread").get("all").asLong()).isEqualTo(1);

            postAs("/escalated/agent/tickets/" + hidden.getTicketNumber() + "/read", agent)
                    .andExpect(status().isNotFound());
            assertThat(reads.findByAgentIdAndTicketIdIn(agent.getId(), List.of(hidden.getId()))).isEmpty();

            String body = mvc.perform(get("/escalated/agent/notifications").with(user(agent.getEmail()))
                    .header("Accept", "application/json")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode item = JSON.readTree(body).get("data").get(0);
            assertThat(item.get("title").asText()).isEqualTo("A ticket you can no longer view");
            assertThat(item.get("body").isNull()).isTrue();
            assertThat(item.get("ticket").isNull()).isTrue();
            assertThat(item.get("url").isNull()).isTrue();
            assertThat(body).doesNotContain(hidden.getSubject()).doesNotContain(hidden.getTicketNumber());
        } finally {
            properties.setVisibility(previous);
        }
    }

    @Test
    void switchedOffNothingIsStoredTheRoutesAre404AndTheCountIsNotShared() throws Exception {
        properties.getUi().setAgentNotificationsEnabled(false);
        try {
            Ticket ticket = created();
            tickets.assign(ticket.getId(), agent.getId(), other.getEmail());
            assertThat(notifications.countByAgentId(agent.getId())).isZero();
            mvc.perform(get("/escalated/agent/notifications").with(user(agent.getEmail()))
                    .header("Accept", "application/json")).andExpect(status().isNotFound());
            postAs("/escalated/agent/notifications/read-all", agent).andExpect(status().isNotFound());
            JsonNode shared = page("/escalated/agent/tickets", agent).get("props").get("escalated");
            assertThat(shared.has("notifications")).isFalse();
            assertThat(shared.has("agent_status")).isTrue();
        } finally {
            properties.getUi().setAgentNotificationsEnabled(true);
        }
    }

    @Test
    void onlyCustomerSideRepliesNotifyTheAssigneeAndFollowers() throws Exception {
        Ticket ticket = created();
        tickets.assign(ticket.getId(), agent.getId(), other.getEmail());
        followers.save(new TicketFollower(ticket.getId(), String.valueOf(other.getId())));

        tickets.addReply(ticket.getId(), "Agent reply", other.getName(), other.getEmail(), "agent", false);
        tickets.addReply(ticket.getId(), "Note", other.getName(), other.getEmail(), "agent", true);
        assertThat(notifications.countByAgentIdAndType(agent.getId(), "ticket_reply")).isZero();

        tickets.addReply(ticket.getId(), "Customer reply", "Customer", customer, "customer", false);
        assertThat(notifications.countByAgentIdAndType(agent.getId(), "ticket_reply")).isEqualTo(1);
        assertThat(notifications.countByAgentIdAndType(other.getId(), "ticket_reply")).isEqualTo(1);
    }

    @Test
    void slaEventsAndChatTransfersNotify() throws Exception {
        Ticket ticket = created();
        tickets.assign(ticket.getId(), agent.getId(), other.getEmail());
        Ticket assigned = tickets.findById(ticket.getId());
        events.publishEvent(new TicketEvent(this, assigned, TicketEvent.Type.SLA_WARNING, null));
        events.publishEvent(new TicketEvent(this, assigned, TicketEvent.Type.SLA_BREACHED, null));
        ChatSession session = new ChatSession();
        session.setTicketId(ticket.getId());
        events.publishEvent(new ChatEvent(this, ChatEvent.Type.TRANSFERRED, session,
                new ChatEvent.Transfer(other.getId(), agent.getId(), null)));

        List<String> types = new ArrayList<>();
        feed(agent, "").get("data").forEach(item -> types.add(item.get("type").asText()));
        assertThat(types).contains("sla_warning", "sla_breached", "chat_transferred");
        assertThat(feed(agent, "").get("data").findValuesAsText("title"))
                .contains("SLA breached on " + ticket.getTicketNumber());
    }

    @Test
    void readReadAllTheUnreadFilterAndPaging() throws Exception {
        Ticket ticket = created();
        for (int i = 0; i < 3; i++) {
            panel.notify(agent.getId(), "ticket_assigned", ticket, Map.of(), null);
        }
        panel.notify(other.getId(), "ticket_assigned", ticket, Map.of(), null);
        String first = feed(agent, "").get("data").get(0).get("id").asText();
        String others = feed(other, "").get("data").get(0).get("id").asText();

        assertThat(json(postAs("/escalated/agent/notifications/" + first + "/read", agent).andExpect(status().isOk()))
                .get("unread_count").asLong()).isEqualTo(2);
        postAs("/escalated/agent/notifications/" + others + "/read", agent).andExpect(status().isNotFound());
        assertThat(feed(agent, "?unread=1").get("meta").get("total").asLong()).isEqualTo(2);

        JsonNode paged = feed(agent, "?per_page=2&page=2");
        assertThat(paged.get("meta").toString())
                .isEqualTo("{\"current_page\":2,\"last_page\":2,\"per_page\":2,\"total\":3}");
        assertThat(paged.get("data")).hasSize(1);
        assertThat(feed(agent, "?per_page=500").get("meta").get("per_page").asInt()).isEqualTo(50);

        assertThat(json(postAs("/escalated/agent/notifications/read-all", agent).andExpect(status().isOk())).toString())
                .isEqualTo("{\"unread_count\":0}");
        assertThat(panel.unreadCount(agent.getId())).isZero();
        assertThat(panel.unreadCount(other.getId())).isEqualTo(1);
    }

    // ---- Helpers ------------------------------------------------------------------------------------

    private AgentProfile agent(String name) {
        AgentProfile profile = new AgentProfile();
        profile.setName(name + " " + unique);
        profile.setEmail(name + "-" + unique + "@example.com");
        profile.setAgent(true);
        return agents.save(profile);
    }

    private Ticket created() {
        return tickets.create("Panel " + unique, "Body", "Customer", customer, TicketPriority.LOW, null);
    }

    private void backdate(Ticket ticket) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("UPDATE escalated_tickets SET last_customer_activity_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(120)), ticket.getId());
        jdbc.update("UPDATE escalated_ticket_reads SET last_read_at = ? WHERE ticket_id = ? AND last_read_at IS NOT NULL",
                Timestamp.from(Instant.now().minusSeconds(60)), ticket.getId());
    }

    private KnowledgeBaseArticle article(String title, String content, boolean published, KnowledgeBaseCategory category,
                                         Instant updated) {
        KnowledgeBaseArticle article = new KnowledgeBaseArticle();
        article.setTitle(title);
        article.setSlug("a-" + UUID.randomUUID());
        article.setContent(content);
        article.setPublished(published);
        article.setCategory(category);
        article = articles.save(article);
        new JdbcTemplate(dataSource).update("UPDATE escalated_kb_articles SET updated_at = ? WHERE id = ?",
                Timestamp.from(updated), article.getId());
        return article;
    }

    private JsonNode kb(String query, int expected) throws Exception {
        return json(mvc.perform(get("/escalated/agent/kb/search?" + query).with(user(agent.getEmail()))
                .header("Accept", "application/json")).andExpect(status().is(expected)));
    }

    private JsonNode kbQ(String q) throws Exception {
        return json(mvc.perform(get("/escalated/agent/kb/search").param("q", q).with(user(agent.getEmail()))
                .header("Accept", "application/json")).andExpect(status().isOk()));
    }

    private static List<Long> ids(JsonNode data) {
        List<Long> ids = new ArrayList<>();
        data.forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    private JsonNode feed(AgentProfile as, String query) throws Exception {
        return json(mvc.perform(get("/escalated/agent/notifications" + query).with(user(as.getEmail()))
                .header("Accept", "application/json")).andExpect(status().isOk()));
    }

    private ResultActions postAs(String path, AgentProfile as) throws Exception {
        return mvc.perform(post(path).with(user(as.getEmail())).with(UiTestSupport.xsrf())
                .header("Accept", "application/json"));
    }

    private JsonNode row(Ticket ticket, AgentProfile as) throws Exception {
        JsonNode data = page("/escalated/agent/tickets?search=" + unique + "&per_page=100", as)
                .get("props").get("tickets").get("data");
        for (JsonNode row : data) {
            if (row.get("id").asLong() == ticket.getId()) {
                return row;
            }
        }
        throw new AssertionError("ticket " + ticket.getTicketNumber() + " is not on the list: " + data);
    }

    private JsonNode inbox() throws Exception {
        return page("/escalated/agent/tickets", agent).get("props").get("inbox");
    }

    private static long delta(JsonNode before, JsonNode after, String key) {
        return after.get("unread").get(key).asLong() - before.get("unread").get(key).asLong();
    }

    private JsonNode page(String url, AgentProfile as) throws Exception {
        return page(url, as.getEmail());
    }

    private JsonNode page(String url, String email) throws Exception {
        MvcResult result = mvc.perform(get(url).with(user(email))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode json(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
