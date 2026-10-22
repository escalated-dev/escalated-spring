package dev.escalated.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Department;
import dev.escalated.models.EscalationRule;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.models.Workflow;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.repositories.WorkflowRepository;
import dev.escalated.scheduling.AutomationScheduler;
import dev.escalated.scheduling.SlaScheduler;
import dev.escalated.services.EscalationConditions;
import dev.escalated.services.SlaService;
import dev.escalated.services.TicketRoutingConditions;
import dev.escalated.services.TicketService;
import dev.escalated.services.TicketSubjectService;
import dev.escalated.services.WorkflowRunnerService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@SpringBootTest(classes = hostapp.HostApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
@Import(TicketVisibilityTest.Accounts.class)
@Transactional
class TicketRoutingTest {
    @Autowired EscalatedProperties properties;
    @Autowired TicketService service;
    @Autowired TicketSubjectService subjects;
    @Autowired TicketRepository tickets;
    @Autowired DepartmentRepository departments;
    @Autowired SlaPolicyRepository policies;
    @Autowired SlaService slas;
    @Autowired WorkflowRepository workflows;
    @Autowired WorkflowRunnerService workflowRunner;
    @Autowired AutomationScheduler automations;
    @Autowired SlaScheduler escalations;
    @Autowired EntityManager em;
    Department department;

    @BeforeEach
    void configure() {
        properties.getAccounts().setEnabled(true);
        department = new Department();
        department.setName("Delivery");
        departments.save(department);
    }

    @AfterEach
    void reset() {
        properties.getAccounts().setEnabled(false);
        properties.setTargetedSlas(false);
        properties.getAutomationConditions().clear();
    }

    Ticket create(String email, String type) {
        return service.create(
                "Request",
                "body",
                "Customer",
                email,
                TicketPriority.MEDIUM,
                department.getId(),
                type);
    }

    SlaPolicy policy(String name, Long departmentId, String type, int minutes) {
        SlaPolicy policy = new SlaPolicy();
        policy.setName(name);
        policy.setDepartmentId(departmentId);
        policy.setTicketType(type);
        policy.setDefaultPolicy(departmentId == null && type == null);
        policy.setFirstResponseHours(java.util.Map.of("medium", minutes / 60.0));
        policy.setResolutionHours(java.util.Map.of("medium", minutes * 2 / 60.0));
        return policies.saveAndFlush(policy);
    }

    String conditions(String id) {
        return "{\"all\":[{\"field\":\"ticket_type\",\"value\":\"delivery\"},"
                + "{\"field\":\"account\",\"value\":{\"type\":\"Company\",\"id\":\""
                + id
                + "\"}}]}";
    }

    @Test
    void requesterAccountIsAvailableBeforeCreatedWorkflows() {
        Ticket ticket = create("account-customer@example.test", "delivery");
        assertThat(ticket.getAccountType()).isEqualTo("Company");
        assertThat(ticket.getAccountId()).isEqualTo("007");
        assertThat(WorkflowRunnerService.ticketToConditionMap(ticket))
                .containsEntry("account_id", "007")
                .containsEntry("ticket_type", "delivery");
    }

    @Test
    void firstSubjectSetsAnAccountWithoutOverwritingAnExistingOne() {
        Ticket ticket = create("customer@example.test", "delivery");
        subjects.attach(ticket, "Company", "001", null);
        subjects.attach(ticket, "Company", "002", null);
        em.flush();
        em.clear();
        assertThat(tickets.findById(ticket.getId()).orElseThrow().getAccountId()).isEqualTo("001");
        Ticket requester = create("account-customer@example.test", "delivery");
        subjects.attach(requester, "Company", "002", null);
        assertThat(requester.getAccountId()).isEqualTo("007");
    }

    @Test
    void accountsRemainUnpopulatedWhenDisabledAndSplitsPreserveIdentity() {
        Ticket original = create("account-customer@example.test", "delivery");
        properties.getAccounts().setEnabled(false);
        assertThat(create("account-customer@example.test", "delivery").getAccountId()).isNull();
        Ticket split = service.split(original.getId(), "Split", null, "agent");
        assertThat(split.getAccountId()).isEqualTo("007");
        assertThat(split.getTicketType()).isEqualTo("delivery");
    }

    @ParameterizedTest
    @ValueSource(strings = {"workflow", "escalation", "automation"})
    void eachEngineUsesAccountPairAndType(String engine) {
        Ticket match = create("account-customer@example.test", "delivery");
        Ticket otherType = create("account-customer@example.test", "support");
        Ticket otherAccount = create("customer@example.test", "delivery");
        otherAccount.setAccountType("Company");
        otherAccount.setAccountId("7");
        tickets.saveAndFlush(otherAccount);
        String json = conditions("007");
        if (engine.equals("workflow")) {
            Workflow workflow = new Workflow();
            workflow.setName("Account route");
            workflow.setTriggerEvent("ticket.updated");
            workflow.setConditions(json);
            workflow.setActions("[{\"type\":\"change_priority\",\"value\":\"high\"}]");
            workflows.save(workflow);
            for (Ticket ticket : List.of(match, otherType, otherAccount)) {
                workflowRunner.runForEvent("ticket.updated", ticket);
            }
            assertThat(match.getPriority()).isEqualTo(TicketPriority.HIGH);
        } else if (engine.equals("escalation")) {
            EscalationRule rule = new EscalationRule();
            rule.setName("Account route");
            // Routing conditions saved before V18 are kept whole as workflow_conditions.
            rule.setConditions("[{\"field\":\"workflow_conditions\",\"value\":" + json + "}]");
            rule.setActions("[{\"type\":\"change_priority\",\"value\":\"high\"}]");
            String listed = "[{\"field\":\"ticket_type\",\"value\":\"delivery\"},"
                    + "{\"field\":\"account\",\"value\":{\"type\":\"Company\",\"id\":\"007\"}}]";
            for (Ticket ticket : List.of(match, otherType, otherAccount)) {
                boolean matches = EscalationConditions.matches(rule.getConditions(), ticket, Instant.now());
                assertThat(EscalationConditions.matches(listed, ticket, Instant.now())).isEqualTo(matches);
                if (matches) {
                    ReflectionTestUtils.invokeMethod(escalations, "applyEscalationRule", ticket, rule);
                }
            }
            assertThat(match.getPriority()).isEqualTo(TicketPriority.HIGH);
        } else {
            properties.getAutomationConditions().put("auto-close", json);
            for (Ticket ticket : List.of(match, otherType, otherAccount)) {
                ticket.setStatus(TicketStatus.RESOLVED);
                ticket.setResolvedAt(Instant.now().minusSeconds(864000));
                tickets.save(ticket);
            }
            automations.autoCloseResolvedTickets();
            assertThat(match.getStatus()).isEqualTo(TicketStatus.CLOSED);
            assertThat(otherType.getStatus()).isEqualTo(TicketStatus.RESOLVED);
            assertThat(otherAccount.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        }
        assertThat(otherType.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(otherAccount.getPriority()).isEqualTo(TicketPriority.MEDIUM);
    }

    @Test
    void malformedAccountNegationNeverMatches() {
        Ticket ticket = create("account-customer@example.test", "delivery");
        for (String value :
                List.of("null", "{}", "{\"id\":\"007\"}", "{\"type\":\"Company\"}", "\"007\"")) {
            assertThat(
                            TicketRoutingConditions.matches(
                                    "{\"all\":[{\"field\":\"account\",\"operator\":\"not_equals\",\"value\":"
                                            + value
                                            + "}]}",
                                    ticket))
                    .isFalse();
        }
        assertThat(TicketRoutingConditions.matches(conditions("7"), ticket)).isFalse();
    }

    @Test
    void slaTargetsPreferBothThenDepartmentThenTypeThenUntargeted() {
        SlaPolicy fallback = policy("Default", null, null, 240);
        SlaPolicy stream = policy("Type", null, "delivery", 120);
        SlaPolicy dept = policy("Department", department.getId(), null, 60);
        SlaPolicy both = policy("Both", department.getId(), "delivery", 15);
        policy("Equal specificity later", department.getId(), "delivery", 5);
        properties.setTargetedSlas(true);
        Ticket ticket = create("customer@example.test", "delivery");
        assertThat(ticket.getSlaPolicy().getId()).isEqualTo(both.getId());
        ticket.setTicketType("support");
        slas.applySlaPolicy(ticket);
        assertThat(ticket.getSlaPolicy().getId()).isEqualTo(dept.getId());
        ticket.setDepartment(null);
        ticket.setTicketType("delivery");
        slas.applySlaPolicy(ticket);
        assertThat(ticket.getSlaPolicy().getId()).isEqualTo(stream.getId());
        ticket.setTicketType("support");
        slas.applySlaPolicy(ticket);
        assertThat(ticket.getSlaPolicy().getId()).isEqualTo(fallback.getId());
    }

    @Test
    void disabledTargetsCannotReplaceDefaultAndExplicitReselectionUsesCreationClock() {
        SlaPolicy fallback = policy("Default", null, null, 240);
        policy("Target", department.getId(), "delivery", 15);
        Ticket ticket = create("customer@example.test", "delivery");
        assertThat(ticket.getSlaPolicy().getId()).isEqualTo(fallback.getId());
        Instant created = Instant.parse("2026-10-01T10:00:00Z");
        ticket.setCreatedAt(created);
        properties.setTargetedSlas(true);
        slas.applySlaPolicy(ticket);
        assertThat(ticket.getSlaFirstResponseDueAt()).isEqualTo(created.plusSeconds(900));
    }
}
