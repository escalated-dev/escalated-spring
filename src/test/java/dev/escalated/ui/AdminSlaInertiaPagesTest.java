package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
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
import dev.escalated.models.EscalationRule;
import dev.escalated.models.RuleExecution;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.EscalationRuleRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.scheduling.SlaScheduler;
import dev.escalated.services.RuleFireLog;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.time.Duration;
import java.time.Instant;
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

/** The SLA policy and escalation rule pages over the Inertia protocol, and what they save doing its job. */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class AdminSlaInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private SlaPolicyRepository policies;
    @Autowired private EscalationRuleRepository rules;
    @Autowired private DepartmentRepository departments;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketService tickets;
    @Autowired private SlaScheduler slaScheduler;
    @Autowired private RuleFireLog fireLog;

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
        for (String path : new String[] {"/escalated/admin/sla-policies", "/escalated/admin/sla-policies/create",
                "/escalated/admin/escalation-rules", "/escalated/admin/escalation-rules/create"}) {
            mvc.perform(get(path).with(user(agent.getEmail()))).andExpect(status().isForbidden());
        }
    }

    @Test
    void slaPoliciesAreListedCreatedEditedAndDeleted() throws Exception {
        Department department = new Department();
        department.setName("Billing " + unique);
        department = departments.save(department);

        JsonNode create = inertia(get("/escalated/admin/sla-policies/create"));
        assertThat(create.get("component").asText()).isEqualTo("Escalated/Admin/SlaPolicies/Form");
        JsonNode options = create.get("props");
        assertThat(options.has("policy")).isFalse();
        assertThat(options.get("priorities").toString()).isEqualTo("[\"low\",\"medium\",\"high\",\"urgent\"]");
        assertThat(options.get("targeted").asBoolean()).isFalse();
        assertThat(options.get("ticketTypes").toString()).contains("question", "problem", "incident", "task");
        JsonNode listed = find(options.get("departments"), department.getId());
        assertThat(fieldNames(listed)).containsExactly("id", "name");

        // As the shared form posts it, plus the targeting fields Laravel's request also takes.
        String body = """
                {"name":"Gold %s","description":"Paying customers","is_default":false,
                 "first_response_hours":{"low":8,"medium":4,"high":1,"urgent":0.5},
                 "resolution_hours":{"low":72,"medium":24,"high":8,"urgent":""},
                 "business_hours_only":true,"is_active":true,
                 "department_id":%d,"ticket_type":"incident"}
                """.formatted(unique, department.getId());
        write(post("/escalated/admin/sla-policies"), body)
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/sla-policies"));
        SlaPolicy created = policyNamed("Gold " + unique);
        assertThat(created.getFirstResponseHours()).containsEntry("urgent", 0.5).containsEntry("high", 1.0);
        assertThat(created.getResolutionHours()).doesNotContainKey("urgent").containsEntry("medium", 24.0);
        assertThat(created.isBusinessHoursOnly()).isTrue();
        assertThat(created.getDepartmentId()).isEqualTo(department.getId());
        assertThat(created.getTicketType()).isEqualTo("incident");

        Ticket ticket = tickets.create("Covered " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.LOW, null);
        attach(ticket, created);

        JsonNode index = inertia(get("/escalated/admin/sla-policies"));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/SlaPolicies/Index");
        JsonNode row = find(index.get("props").get("policies"), created.getId());
        assertThat(fieldNames(row)).contains("id", "name", "description", "is_default", "first_response_hours",
                "resolution_hours", "business_hours_only", "is_active", "tickets_count", "department_id",
                "ticket_type");
        assertThat(row.get("tickets_count").asLong()).isEqualTo(1);
        assertThat(row.get("first_response_hours").get("high").isIntegralNumber()).isTrue();
        assertThat(row.get("first_response_hours").get("urgent").asDouble()).isEqualTo(0.5);

        JsonNode edit = inertia(get("/escalated/admin/sla-policies/" + created.getId() + "/edit")).get("props");
        assertThat(fieldNames(edit)).contains("policy", "priorities", "targeted", "departments", "ticketTypes");
        assertThat(edit.get("policy").get("resolution_hours").get("low").asInt()).isEqualTo(72);

        // The shared form does not post the targeting fields: an update keeps them.
        write(put("/escalated/admin/sla-policies/" + created.getId()), """
                {"name":"Platinum %s","description":"","is_default":true,
                 "first_response_hours":{"high":2},"resolution_hours":{"high":6},
                 "business_hours_only":false,"is_active":false}
                """.formatted(unique))
                .andExpect(status().isSeeOther());
        SlaPolicy updated = policies.findById(created.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Platinum " + unique);
        assertThat(updated.getFirstResponseHours()).containsOnlyKeys("high");
        assertThat(updated.isDefaultPolicy()).isTrue();
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.isBusinessHoursOnly()).isFalse();
        assertThat(updated.getDepartmentId()).isEqualTo(department.getId());
        assertThat(updated.getTicketType()).isEqualTo("incident");

        write(delete("/escalated/admin/sla-policies/" + created.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(policies.existsById(created.getId())).isFalse();
        Ticket kept = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(kept.getSlaPolicy()).isNull();
    }

    @Test
    void aDefaultPolicySavedFromThePageSetsTheDeadlinesOfNewTickets() throws Exception {
        write(post("/escalated/admin/sla-policies"), """
                {"name":"Urgent %s","is_default":true,"first_response_hours":{"urgent":2},
                 "resolution_hours":{"urgent":0.25},"business_hours_only":false,"is_active":true}
                """.formatted(unique))
                .andExpect(status().isSeeOther());
        SlaPolicy policy = policyNamed("Urgent " + unique);
        try {
            Instant before = Instant.now();
            Ticket ticket = tickets.create("Fire " + unique, "b", "R", "r-" + unique + "@example.com",
                    TicketPriority.URGENT, null);
            Ticket stored = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertThat(stored.getSlaPolicy().getId()).isEqualTo(policy.getId());
            assertThat(Duration.between(before, stored.getSlaFirstResponseDueAt()).toMinutes()).isBetween(119L, 120L);
            assertThat(Duration.between(before, stored.getSlaDueAt()).toMinutes()).isBetween(14L, 15L);
        } finally {
            write(delete("/escalated/admin/sla-policies/" + policy.getId()), "{}").andExpect(status().isSeeOther());
        }
    }

    @Test
    void anInvalidPolicyGoesBackWithErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/sla-policies").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/sla-policies/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad " + unique + "\",\"first_response_hours\":{},"
                                + "\"resolution_hours\":{\"high\":\"soon\"}}"))
                .andExpect(status().isSeeOther());
        JsonNode errors = inertia(get("/escalated/admin/sla-policies/create").session(session))
                .get("props").get("errors");
        assertThat(errors.has("first_response_hours")).isTrue();
        assertThat(policies.findAll().stream().noneMatch(p -> p.getName().equals("Bad " + unique))).isTrue();
    }

    @Test
    void escalationRulesAreListedCreatedEditedAndDeleted() throws Exception {
        JsonNode create = inertia(get("/escalated/admin/escalation-rules/create"));
        assertThat(create.get("component").asText()).isEqualTo("Escalated/Admin/EscalationRules/Form");
        assertThat(create.get("props").has("rule")).isFalse();

        String body = """
                {"name":"Stale %s","description":"Nobody answered","trigger_type":"time_based",
                 "conditions":[{"field":"no_response_hours","value":"4"},{"field":"priority","value":"high"}],
                 "actions":[{"type":"escalate","value":""},{"type":"assign_to","value":"%d"}],
                 "order":3,"is_active":true}
                """.formatted(unique, agent.getId());
        write(post("/escalated/admin/escalation-rules"), body)
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/escalation-rules"));
        EscalationRule created = ruleNamed("Stale " + unique);
        try {
            assertThat(created.getSortOrder()).isEqualTo(3);
            assertThat(JSON.readTree(created.getConditions()).get(1).get("value").asText()).isEqualTo("high");

            JsonNode index = inertia(get("/escalated/admin/escalation-rules"));
            assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/EscalationRules/Index");
            JsonNode row = find(index.get("props").get("rules"), created.getId());
            assertThat(fieldNames(row)).contains("id", "name", "description", "category", "trigger_type",
                    "conditions", "actions", "order", "is_active", "repeat_after_hours");
            assertThat(row.get("category").asText()).isEqualTo("Uncategorized");
            assertThat(row.get("order").asInt()).isEqualTo(3);
            assertThat(fieldNames(row.get("conditions").get(0))).containsExactly("field", "value");
            assertThat(fieldNames(row.get("actions").get(1))).containsExactly("type", "value");
            assertThat(row.get("actions").get(1).get("value").asText()).isEqualTo(String.valueOf(agent.getId()));

            JsonNode edit = inertia(get("/escalated/admin/escalation-rules/" + created.getId() + "/edit"));
            assertThat(edit.get("component").asText()).isEqualTo("Escalated/Admin/EscalationRules/Form");
            assertThat(edit.get("props").get("rule").get("trigger_type").asText()).isEqualTo("time_based");

            write(put("/escalated/admin/escalation-rules/" + created.getId()), """
                    {"name":"Stale later %s","trigger_type":"sla_breach",
                     "conditions":[{"field":"sla_breached","value":"1"}],
                     "actions":[{"type":"change_priority","value":"urgent"}],"order":0,"is_active":false,
                     "repeat_after_hours":12}
                    """.formatted(unique))
                    .andExpect(status().isSeeOther());
            EscalationRule updated = rules.findById(created.getId()).orElseThrow();
            assertThat(updated.getName()).isEqualTo("Stale later " + unique);
            assertThat(updated.getTriggerType()).isEqualTo("sla_breach");
            assertThat(updated.isActive()).isFalse();
            assertThat(updated.getRepeatAfterHours()).isEqualTo(12);
            assertThat(updated.getDescription()).isEqualTo("Nobody answered");
        } finally {
            rules.findById(created.getId()).ifPresent(rule -> {
                rule.setActive(false);
                rules.save(rule);
            });
        }

        Ticket ticket = tickets.create("Logged " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.LOW, null);
        fireLog.record(RuleExecution.ESCALATION_RULE, created.getId(), ticket.getId());
        write(delete("/escalated/admin/escalation-rules/" + created.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(rules.existsById(created.getId())).isFalse();
        assertThat(fireLog.hasFired(RuleExecution.ESCALATION_RULE, created.getId(), ticket.getId())).isFalse();
    }

    @Test
    void anInvalidRuleGoesBackWithErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/escalation-rules").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/escalation-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad " + unique + "\",\"trigger_type\":\"time_based\","
                                + "\"conditions\":[],\"actions\":[{\"type\":\"escalate\"}],\"repeat_after_hours\":0}"))
                .andExpect(status().isSeeOther());
        JsonNode errors = inertia(get("/escalated/admin/escalation-rules/create").session(session))
                .get("props").get("errors");
        assertThat(errors.has("conditions")).isTrue();
        assertThat(rules.findAll().stream().noneMatch(r -> r.getName().equals("Bad " + unique))).isTrue();
    }

    @Test
    void aRuleSavedFromThePageActsOnTheTicketsItMatches() throws Exception {
        String type = "esc-" + unique;
        write(post("/escalated/admin/escalation-rules"), """
                {"name":"Bump %s","trigger_type":"time_based",
                 "conditions":[{"field":"ticket_type","value":"%s"},{"field":"priority","value":"high"},
                               {"field":"assigned","value":"unassigned"},{"field":"","value":""}],
                 "actions":[{"type":"change_priority","value":"urgent"},{"type":"assign_to","value":"%d"},
                            {"type":"escalate","value":""}],
                 "order":0,"is_active":true}
                """.formatted(unique, type, agent.getId()))
                .andExpect(status().isSeeOther());
        EscalationRule rule = ruleNamed("Bump " + unique);
        try {
            Ticket matching = tickets.create("Match " + unique, "b", "R", "m-" + unique + "@example.com",
                    TicketPriority.HIGH, null, type);
            Ticket otherType = tickets.create("Other " + unique, "b", "R", "o-" + unique + "@example.com",
                    TicketPriority.HIGH, null, "other-" + unique);
            Ticket lowPriority = tickets.create("Low " + unique, "b", "R", "l-" + unique + "@example.com",
                    TicketPriority.LOW, null, type);

            slaScheduler.checkSlaBreaches();

            Ticket acted = ticketRepository.findById(matching.getId()).orElseThrow();
            assertThat(acted.getPriority()).isEqualTo(TicketPriority.URGENT);
            assertThat(acted.getAssignedAgent().getId()).isEqualTo(agent.getId());
            assertThat(fireLog.hasFired(RuleExecution.ESCALATION_RULE, rule.getId(), matching.getId())).isTrue();

            // It no longer matches (urgent, assigned), so the next sweep re-arms the rule for it.
            slaScheduler.checkSlaBreaches();
            assertThat(fireLog.hasFired(RuleExecution.ESCALATION_RULE, rule.getId(), matching.getId())).isFalse();
            assertThat(ticketRepository.findById(otherType.getId()).orElseThrow().getPriority())
                    .isEqualTo(TicketPriority.HIGH);
            assertThat(ticketRepository.findById(lowPriority.getId()).orElseThrow().getAssignedAgent()).isNull();
        } finally {
            write(delete("/escalated/admin/escalation-rules/" + rule.getId()), "{}").andExpect(status().isSeeOther());
        }
    }

    private void attach(Ticket ticket, SlaPolicy policy) {
        Ticket stored = ticketRepository.findById(ticket.getId()).orElseThrow();
        stored.setSlaPolicy(policy);
        ticketRepository.save(stored);
    }

    private SlaPolicy policyNamed(String name) {
        return policies.findAll().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }

    private EscalationRule ruleNamed(String name) {
        return rules.findAll().stream().filter(r -> r.getName().equals(name)).findFirst().orElseThrow();
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
