package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.models.SavedView;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.SavedViewRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The agent panel's optional {@code inbox} and {@code queue} props, and the list filters its
 * rail, views and stat strip link to (shared frontend docs/agent-panel.md, after
 * escalated-laravel's AgentInboxPropsTest). Every number must be what the agent gets by opening the
 * list it labels.
 *
 * <p>The suite shares one database, so counts over every ticket are checked as the change the
 * test's own tickets make, and lists are narrowed with a search for this test's tickets.
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
class AgentInboxPropsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> BUILT_IN =
            List.of("urgent", "sla_at_risk", "waiting_on_me", "waiting_on_customer", "recently_updated");

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketService tickets;
    @Autowired private SavedViewRepository savedViews;
    @Autowired private AgentInbox agentInbox;
    @Autowired private EscalatedProperties properties;
    @Autowired private DataSource dataSource;
    @Autowired private DepartmentRepository departments;

    private String unique;
    private AgentProfile agent;
    private AgentProfile other;

    @BeforeEach
    void seed() {
        unique = "inbox" + UUID.randomUUID().toString().substring(0, 8);
        agent = agent("agent");
        other = agent("other");
    }

    @Test
    void sendsCountsBuiltInViewsStatsAndTheQueueOnTheList() throws Exception {
        JsonNode before = inbox("");
        Instant now = Instant.now();
        ticket(t -> {
            t.setAssignedAgent(agent);
            t.setPriority(TicketPriority.URGENT);
        });
        ticket(t -> {
            t.setAssignedAgent(agent);
            t.setStatus(TicketStatus.WAITING_ON_CUSTOMER);
        });
        ticket(t -> {
            t.setAssignedAgent(agent);
            t.setStatus(TicketStatus.WAITING_ON_AGENT);
        });
        ticket(t -> resolved(t, now, agent));
        ticket(t -> resolved(t, now.minus(Duration.ofDays(2)), agent));
        ticket(t -> {
            t.setAssignedAgent(agent);
            t.setStatus(TicketStatus.SNOOZED);
            t.setSnoozedUntil(now.plus(Duration.ofDays(1)));
        });
        ticket(t -> { });
        ticket(t -> {
            t.setStatus(TicketStatus.CLOSED);
            t.setClosedAt(now);
        });
        ticket(t -> t.setAssignedAgent(other));

        JsonNode page = page("/escalated/agent/tickets");
        JsonNode inbox = page.get("props").get("inbox");
        assertThat(AgentInertiaPagesTest.fieldNames(inbox)).containsExactly("counts", "views", "stats");
        assertThat(AgentInertiaPagesTest.fieldNames(inbox.get("counts"))).containsExactly("mine", "unassigned", "all");
        // Unresolved, unsnoozed tickets in each queue.
        assertThat(inbox.get("counts").get("mine").asLong()).isEqualTo(3);
        assertThat(delta(before, inbox, "counts", "unassigned")).isEqualTo(1);
        assertThat(delta(before, inbox, "counts", "all")).isEqualTo(5);

        assertThat(viewIds(inbox)).containsExactlyElementsOf(BUILT_IN);
        JsonNode urgent = inbox.get("views").get(0);
        assertThat(AgentInertiaPagesTest.fieldNames(urgent)).containsExactly("id", "name", "count", "filters");
        assertThat(urgent.get("name").asText()).isEqualTo("Urgent");
        assertThat(urgent.get("filters")).isEqualTo(JSON.readTree("{\"priority\":\"urgent\",\"open\":1}"));
        assertThat(view(inbox, "urgent").get("count").asLong() - view(before, "urgent").get("count").asLong())
                .isEqualTo(1);
        // The two waiting views are different tickets (they were one status, pending, before).
        assertThat(view(inbox, "waiting_on_customer").get("count").asLong()).isEqualTo(1);
        assertThat(view(inbox, "waiting_on_me").get("count").asLong()).isEqualTo(1);
        assertThat(page("/escalated/agent/tickets?assigned_to=" + agent.getId() + "&status=waiting_on_agent")
                .get("props").get("tickets").get("total").asLong()).isEqualTo(1);
        assertThat(view(inbox, "waiting_on_customer").get("filters")).isEqualTo(JSON.readTree(
                "{\"assigned_to\":" + agent.getId() + ",\"status\":\"waiting_on_customer\"}"));
        assertThat(view(inbox, "waiting_on_me").get("filters")).isEqualTo(JSON.readTree(
                "{\"assigned_to\":" + agent.getId() + ",\"status\":\"waiting_on_agent\"}"));
        assertThat(view(inbox, "sla_at_risk").get("filters")).isEqualTo(JSON.readTree("{\"sla_at_risk\":1}"));
        assertThat(view(inbox, "recently_updated").get("filters")).isEqualTo(JSON.readTree(
                "{\"open\":1,\"updated_within_hours\":24,\"sort_by\":\"updated_at\"}"));
        assertThat(view(inbox, "recently_updated").get("count").asLong()
                - view(before, "recently_updated").get("count").asLong()).isEqualTo(5);

        assertThat(AgentInertiaPagesTest.fieldNames(inbox.get("stats")))
                .containsExactly("open", "urgent", "sla_at_risk", "resolved_today");
        // The "all" queue: three tickets with status open, one urgent, one resolved today.
        assertThat(delta(before, inbox, "stats", "open")).isEqualTo(3);
        assertThat(delta(before, inbox, "stats", "urgent")).isEqualTo(1);
        assertThat(delta(before, inbox, "stats", "resolved_today")).isEqualTo(1);
        assertThat(page.get("props").get("queue")).isEqualTo(JSON.readTree("{\"key\":\"all\",\"title\":\"All tickets\"}"));
    }

    @Test
    void namesAndScopesTheMineQueue() throws Exception {
        seedQueue();
        for (String query : List.of("?assigned_to=" + agent.getId(), "?assigned=me", "?assigned_to=me",
                "?assigned_to=" + agent.getId() + "&priority=urgent")) {
            JsonNode props = page("/escalated/agent/tickets" + query).get("props");
            assertThat(props.get("queue")).as(query).isEqualTo(JSON.readTree("{\"key\":\"mine\",\"title\":\"My inbox\"}"));
            assertThat(props.get("inbox").get("stats")).as(query).isEqualTo(JSON.readTree(
                    "{\"open\":1,\"urgent\":1,\"sla_at_risk\":0,\"resolved_today\":1}"));
        }
    }

    @Test
    void namesTheOtherQueues() throws Exception {
        assertThat(page("/escalated/agent/tickets?unassigned=1").get("props").get("queue"))
                .isEqualTo(JSON.readTree("{\"key\":\"unassigned\",\"title\":\"Unassigned\"}"));
        assertThat(page("/escalated/agent/tickets?priority=urgent&open=1").get("props").get("queue"))
                .isEqualTo(JSON.readTree("{\"key\":\"view:urgent\",\"title\":\"Urgent\"}"));
        assertThat(page("/escalated/agent/tickets?view=urgent").get("props").get("queue"))
                .isEqualTo(JSON.readTree("{\"key\":\"view:urgent\",\"title\":\"Urgent\"}"));
        assertThat(page("/escalated/agent/tickets?view=nope").get("props").get("queue"))
                .isEqualTo(JSON.readTree("{\"key\":\"all\",\"title\":\"All tickets\"}"));
    }

    @Test
    void theUnassignedQueuesStatsAreItsOwn() throws Exception {
        JsonNode before = inbox("?unassigned=1");
        seedQueue();
        JsonNode after = inbox("?unassigned=1");
        assertThat(delta(before, after, "stats", "open")).isEqualTo(2);
        assertThat(delta(before, after, "stats", "urgent")).isEqualTo(1);
        assertThat(delta(before, after, "stats", "resolved_today")).isEqualTo(1);
    }

    @Test
    void aViewPickedByIdFiltersTheList() throws Exception {
        Ticket urgent = ticket(t -> t.setPriority(TicketPriority.URGENT));
        ticket(t -> {
            t.setPriority(TicketPriority.URGENT);
            t.setStatus(TicketStatus.CLOSED);
        });
        ticket(t -> { });

        JsonNode rows = page("/escalated/agent/tickets?view=urgent&search=" + unique)
                .get("props").get("tickets").get("data");
        assertThat(ids(rows)).containsExactly(urgent.getId());
    }

    @Test
    void listsOwnAndSharedSavedViewsButNotOtherAgentsPrivateOnes() throws Exception {
        SavedView mine = savedView(agent, "High priority " + unique, "{\"priority\":\"high\",\"search\":\"" + unique + "\"}", false);
        SavedView shared = savedView(other, "Pending " + unique, "{\"status\":\"pending\",\"search\":\"" + unique + "\"}", true);
        SavedView hidden = savedView(other, "Private " + unique, "{\"priority\":\"low\"}", false);
        ticket(t -> t.setPriority(TicketPriority.HIGH));
        ticket(t -> t.setPriority(TicketPriority.HIGH));
        ticket(t -> {
            t.setPriority(TicketPriority.HIGH);
            t.setStatus(TicketStatus.CLOSED);
        });
        ticket(t -> t.setStatus(TicketStatus.WAITING_ON_CUSTOMER));

        JsonNode views = page("/escalated/agent/tickets").get("props").get("inbox").get("views");
        List<Long> savedIds = new ArrayList<>();
        views.forEach(view -> {
            if (view.get("id").isNumber()) {
                savedIds.add(view.get("id").asLong());
            }
        });
        assertThat(savedIds).contains(mine.getId(), shared.getId()).doesNotContain(hidden.getId());
        JsonNode high = find(views, mine.getId());
        assertThat(high.get("name").asText()).isEqualTo("High priority " + unique);
        assertThat(high.get("count").asLong()).isEqualTo(2);
        assertThat(high.get("filters")).isEqualTo(JSON.readTree("{\"priority\":\"high\",\"search\":\"" + unique + "\"}"));
        assertThat(find(views, shared.getId()).get("count").asLong()).isEqualTo(1);

        JsonNode byId = page("/escalated/agent/tickets?view=" + mine.getId()).get("props");
        assertThat(byId.get("queue").get("key").asText()).isEqualTo("view:" + mine.getId());
        assertThat(byId.get("tickets").get("data")).hasSize(3);
        JsonNode byFilters = page("/escalated/agent/tickets?priority=high&search=" + unique).get("props");
        assertThat(byFilters.get("queue").get("key").asText()).isEqualTo("view:" + mine.getId());
        assertThat(page("/escalated/agent/tickets?view=" + hidden.getId()).get("props").get("queue").get("key").asText())
                .isEqualTo("all");
    }

    @Test
    void everyViewCountsWhatItsOwnListShows() throws Exception {
        Instant now = Instant.now();
        ticket(t -> {
            t.setAssignedAgent(agent);
            t.setPriority(TicketPriority.URGENT);
        });
        ticket(t -> t.setSlaDueAt(now.plus(Duration.ofMinutes(30))));
        Ticket stale = ticket(t -> { });
        new JdbcTemplate(dataSource).update("UPDATE escalated_tickets SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(now.minus(Duration.ofDays(3))), stale.getId());

        for (JsonNode view : page("/escalated/agent/tickets").get("props").get("inbox").get("views")) {
            UriComponentsBuilder url = UriComponentsBuilder.fromPath("/escalated/agent/tickets");
            view.get("filters").fields().forEachRemaining(f -> url.queryParam(f.getKey(), f.getValue().asText()));
            long shown = page(url.toUriString()).get("props").get("tickets").get("total").asLong();
            assertThat(view.get("count").asLong()).as(view.get("id").asText()).isEqualTo(shown);
        }
    }

    @Test
    void filtersTheListToTicketsWhoseSlaIsBreachedOrDueWithinTheHour() throws Exception {
        long before = inbox("").get("stats").get("sla_at_risk").asLong();
        Instant now = Instant.now();
        Ticket overdue = ticket(t -> t.setSlaDueAt(now.minus(Duration.ofMinutes(5))));
        Ticket responseDue = ticket(t -> t.setSlaFirstResponseDueAt(now.plus(Duration.ofMinutes(20))));
        Ticket resolutionDue = ticket(t -> t.setSlaDueAt(now.plus(Duration.ofMinutes(50))));
        ticket(t -> {
            t.setSlaFirstResponseDueAt(now.plus(Duration.ofMinutes(20)));
            t.setFirstRespondedAt(now.minus(Duration.ofMinutes(1)));
        });
        ticket(t -> t.setSlaDueAt(now.plus(Duration.ofHours(3))));
        ticket(t -> {
            t.setSlaDueAt(now.minus(Duration.ofMinutes(5)));
            resolved(t, now, null);
        });
        ticket(t -> { });

        JsonNode props = page("/escalated/agent/tickets?sla_at_risk=1&search=" + unique).get("props");
        assertThat(ids(props.get("tickets").get("data")))
                .containsExactlyInAnyOrder(overdue.getId(), responseDue.getId(), resolutionDue.getId());
        assertThat(inbox("").get("stats").get("sla_at_risk").asLong() - before).isEqualTo(3);
    }

    @Test
    void filtersTheListToResolvedTodayOpenAndRecentlyUpdated() throws Exception {
        ZoneId zone = ZoneId.systemDefault();
        Instant startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Ticket today = ticket(t -> resolved(t, startOfDay.plusSeconds(60), null));
        ticket(t -> resolved(t, startOfDay.minus(Duration.ofDays(1)), null));
        Ticket fresh = ticket(t -> { });
        Ticket stale = ticket(t -> { });
        ticket(t -> t.setStatus(TicketStatus.CLOSED));
        ticket(t -> t.setStatus(TicketStatus.SNOOZED));
        new JdbcTemplate(dataSource).update("UPDATE escalated_tickets SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(3))), stale.getId());

        String search = "&search=" + unique;
        assertThat(ids(page("/escalated/agent/tickets?status=resolved&resolved_today=1" + search)
                .get("props").get("tickets").get("data"))).containsExactly(today.getId());
        assertThat(ids(page("/escalated/agent/tickets?open=1" + search).get("props").get("tickets").get("data")))
                .containsExactlyInAnyOrder(fresh.getId(), stale.getId());
        assertThat(ids(page("/escalated/agent/tickets?open=1&updated_within_hours=24" + search)
                .get("props").get("tickets").get("data"))).containsExactly(fresh.getId());
    }

    @Test
    void rejectsMalformedValuesForTheNewFilters() throws Exception {
        for (String[] bad : new String[][] {
            {"sla_at_risk", "soon"}, {"resolved_today", "maybe"}, {"open", "perhaps"},
            {"updated_within_hours", "0"}, {"updated_within_hours", "a day"}, {"updated_within_hours", "8761"},
        }) {
            MvcResult result = mvc.perform(get("/escalated/agent/tickets").param(bad[0], bad[1])
                            .with(user(agent.getEmail())).header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            @SuppressWarnings("unchecked")
            Map<String, Object> errors = (Map<String, Object>) result.getRequest().getSession()
                    .getAttribute(Inertia.ERRORS_SESSION_KEY);
            assertThat(errors).as(bad[0] + "=" + bad[1]).containsKey(bad[0]);
        }
    }

    @Test
    void previewsTheLatestPublicReplyNeverAnInternalNote() throws Exception {
        Ticket ticket = ticket(t -> { });
        tickets.addReply(ticket.getId(), "Customer-visible answer", agent.getName(), agent.getEmail(), "agent", false);
        tickets.addReply(ticket.getId(), "Secret internal note", agent.getName(), agent.getEmail(), "agent", true);

        JsonNode row = page("/escalated/agent/tickets?search=" + unique).get("props").get("tickets").get("data").get(0);
        assertThat(row.get("latest_reply").get("body").asText()).isEqualTo("Customer-visible answer");
        assertThat(row.get("latest_reply").get("is_internal_note").asBoolean()).isFalse();
        // Escalated tracks no per-agent read state or customer presence.
        assertThat(row.has("is_unread")).isFalse();
        assertThat(row.has("requester_online")).isFalse();
    }

    @Test
    void theTicketPageGetsTheInboxAndTheQueueLastOpenedInTheList() throws Exception {
        Ticket ticket = ticket(t -> t.setAssignedAgent(agent));
        MockHttpSession session = new MockHttpSession();
        String show = "/escalated/agent/tickets/" + ticket.getTicketNumber();

        JsonNode props = page(show, session).get("props");
        assertThat(props.get("queue")).isEqualTo(JSON.readTree("{\"key\":\"all\",\"title\":\"All tickets\"}"));
        assertThat(AgentInertiaPagesTest.fieldNames(props.get("inbox"))).containsExactly("counts", "views", "stats");
        assertThat(props.get("inbox").get("counts").get("mine").asLong()).isEqualTo(1);

        page("/escalated/agent/tickets?assigned_to=" + agent.getId(), session);
        props = page(show, session).get("props");
        assertThat(props.get("queue")).isEqualTo(JSON.readTree("{\"key\":\"mine\",\"title\":\"My inbox\"}"));
        assertThat(props.get("inbox").get("stats")).isEqualTo(JSON.readTree(
                "{\"open\":1,\"urgent\":0,\"sla_at_risk\":0,\"resolved_today\":0}"));
    }

    @Test
    void sendsTheAgentTicketPagesEveryPropTheyDeclareAndNothingElse() throws Exception {
        JsonNode manifest = JSON.readTree(getClass().getResourceAsStream("/escalated-pages.json"));
        Ticket ticket = ticket(t -> { });
        // TicketShow also carries the ticket's custom field values, which this package has sent
        // there since custom fields shipped.
        Map<String, List<String>> extra = Map.of("Escalated/Agent/TicketShow", List.of("custom_fields"));
        List<String> shared = List.of("errors", "auth", "flash", "escalated", "ziggy");
        for (Map.Entry<String, String> page : Map.of(
                "Escalated/Agent/TicketIndex", "/escalated/agent/tickets",
                "Escalated/Agent/TicketShow", "/escalated/agent/tickets/" + ticket.getTicketNumber()).entrySet()) {
            List<String> declared = new ArrayList<>();
            manifest.get("props").get(page.getKey()).get("props").forEach(p -> declared.add(p.asText()));
            declared.addAll(extra.getOrDefault(page.getKey(), List.of()));
            List<String> sent = new ArrayList<>(AgentInertiaPagesTest.fieldNames(page(page.getValue()).get("props")));
            sent.removeAll(shared);
            assertThat(sent).as(page.getKey()).containsExactlyInAnyOrderElementsOf(declared);
        }
    }

    @Test
    void keepsTheInboxPerAgentForTheConfiguredSeconds() throws Exception {
        EscalatedProperties.UiProperties ui = properties.getUi();
        Instant start = Instant.parse("2030-01-01T00:00:00Z");
        try {
            ui.setAgentInboxCacheSeconds(30);
            agentInbox.setClock(Clock.fixed(start, ZoneOffset.UTC));
            ticket(t -> t.setAssignedAgent(agent));
            assertThat(inbox("").get("counts").get("mine").asLong()).isEqualTo(1);

            ticket(t -> t.setAssignedAgent(agent));
            ticket(t -> t.setAssignedAgent(other));
            assertThat(inbox("").get("counts").get("mine").asLong()).isEqualTo(1);
            assertThat(inbox("", other).get("counts").get("mine").asLong()).isEqualTo(1);

            agentInbox.setClock(Clock.fixed(start.plusSeconds(31), ZoneOffset.UTC));
            assertThat(inbox("").get("counts").get("mine").asLong()).isEqualTo(2);
        } finally {
            ui.setAgentInboxCacheSeconds(0);
            agentInbox.setClock(Clock.systemUTC());
        }
    }

    @Test
    void countsOnlyTicketsTheAgentMaySee() throws Exception {
        Department support = department("Support " + unique);
        Department billing = department("Billing " + unique);
        agent.setDepartment(support);
        agent = agents.save(agent);
        ticket(t -> {
            t.setDepartment(support);
            t.setAssignedAgent(agent);
            t.setPriority(TicketPriority.URGENT);
        });
        ticket(t -> t.setDepartment(support));
        ticket(t -> t.setDepartment(billing));
        ticket(t -> {
            t.setDepartment(billing);
            t.setPriority(TicketPriority.URGENT);
        });
        ticket(t -> {
            t.setDepartment(billing);
            resolved(t, Instant.now(), null);
        });

        String visibility = properties.getVisibility();
        try {
            properties.setVisibility("department");
            JsonNode inbox = inbox("");
            assertThat(inbox.get("counts")).isEqualTo(JSON.readTree("{\"mine\":1,\"unassigned\":1,\"all\":2}"));
            assertThat(inbox.get("stats")).isEqualTo(JSON.readTree(
                    "{\"open\":2,\"urgent\":1,\"sla_at_risk\":0,\"resolved_today\":0}"));
            assertThat(view(inbox, "urgent").get("count").asLong()).isEqualTo(1);
        } finally {
            properties.setVisibility(visibility);
        }
    }

    private Department department(String name) {
        Department department = new Department();
        department.setName(name);
        return departments.save(department);
    }

    private void seedQueue() {
        Instant now = Instant.now();
        ticket(t -> {
            t.setAssignedAgent(agent);
            t.setPriority(TicketPriority.URGENT);
        });
        ticket(t -> resolved(t, now, agent));
        ticket(t -> { });
        ticket(t -> resolved(t, now, null));
        ticket(t -> t.setPriority(TicketPriority.URGENT));
    }

    private static void resolved(Ticket ticket, Instant at, AgentProfile assignee) {
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(at);
        if (assignee != null) {
            ticket.setAssignedAgent(assignee);
        }
    }

    private AgentProfile agent(String name) {
        AgentProfile profile = new AgentProfile();
        profile.setName(name + " " + unique);
        profile.setEmail(name + "-" + unique + "@example.com");
        profile.setAgent(true);
        return agents.save(profile);
    }

    private Ticket ticket(Consumer<Ticket> customise) {
        Ticket ticket = new Ticket();
        ticket.setSubject("Ticket " + unique);
        ticket.setBody("Body");
        ticket.setTicketNumber("T-" + UUID.randomUUID());
        ticket.setRequesterName("Customer");
        ticket.setRequesterEmail("customer@example.test");
        ticket.setGuestAccessToken(UUID.randomUUID().toString());
        ticket.setPriority(TicketPriority.LOW);
        customise.accept(ticket);
        return ticketRepository.save(ticket);
    }

    private SavedView savedView(AgentProfile owner, String name, String filters, boolean shared) {
        SavedView view = new SavedView();
        view.setName(name);
        view.setFilters(filters);
        view.setShared(shared);
        view.setAgent(owner);
        return savedViews.save(view);
    }

    private JsonNode inbox(String query) throws Exception {
        return page("/escalated/agent/tickets" + query).get("props").get("inbox");
    }

    private JsonNode inbox(String query, AgentProfile as) throws Exception {
        MvcResult result = mvc.perform(get("/escalated/agent/tickets" + query).with(user(as.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("props").get("inbox");
    }

    private JsonNode page(String url) throws Exception {
        return page(url, new MockHttpSession());
    }

    private JsonNode page(String url, MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get(url).session(session).with(user(agent.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static long delta(JsonNode before, JsonNode after, String group, String key) {
        return after.get(group).get(key).asLong() - before.get(group).get(key).asLong();
    }

    private static List<String> viewIds(JsonNode inbox) {
        List<String> ids = new ArrayList<>();
        inbox.get("views").forEach(view -> {
            if (view.get("id").isTextual()) {
                ids.add(view.get("id").asText());
            }
        });
        return ids;
    }

    private static JsonNode view(JsonNode inbox, String id) {
        for (JsonNode view : inbox.get("views")) {
            if (id.equals(view.get("id").asText())) {
                return view;
            }
        }
        throw new AssertionError(id + " not in " + inbox.get("views"));
    }

    private static JsonNode find(JsonNode views, Long id) {
        for (JsonNode view : views) {
            if (view.get("id").isNumber() && view.get("id").asLong() == id) {
                return view;
            }
        }
        throw new AssertionError(id + " not in " + views);
    }

    private static List<Long> ids(JsonNode rows) {
        List<Long> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }
}
