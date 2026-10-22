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
import dev.escalated.models.AgentCapacity;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.AgentSkill;
import dev.escalated.models.BusinessSchedule;
import dev.escalated.models.Department;
import dev.escalated.models.Skill;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Tag;
import dev.escalated.repositories.AgentCapacityRepository;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AgentSkillRepository;
import dev.escalated.repositories.BusinessScheduleRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.SkillRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.services.AuditLogService;
import hostapp.HostApplication;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

/**
 * The routing and audit admin pages over the Inertia protocol: {@code Admin/Skills/Index} and
 * {@code Form}, {@code Admin/Capacity/Index}, {@code Admin/BusinessHours/Index} and {@code Form},
 * and {@code Admin/AuditLog/Index}, with the props escalated-laravel's controllers pass.
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
class AdminRoutingInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private SkillRepository skills;
    @Autowired private AgentSkillRepository agentSkills;
    @Autowired private TagRepository tags;
    @Autowired private DepartmentRepository departments;
    @Autowired private AgentCapacityRepository capacities;
    @Autowired private BusinessScheduleRepository schedules;
    @Autowired private SlaPolicyRepository slaPolicies;
    @Autowired private AuditLogService auditLogs;

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
        for (String path : new String[] {"/escalated/admin/skills", "/escalated/admin/skills/create",
                "/escalated/admin/capacity", "/escalated/admin/business-hours",
                "/escalated/admin/business-hours/create", "/escalated/admin/audit-log"}) {
            mvc.perform(get(path).with(user(agent.getEmail()))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/escalated/admin/skills").with(user(agent.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sneaky " + unique + "\"}"))
                .andExpect(status().isForbidden());
        assertThat(skills.findByName("Sneaky " + unique)).isEmpty();
    }

    // ── Skills ──────────────────────────────────────────────────────────

    @Test
    void aSkillIsCreatedListedEditedAndDeleted() throws Exception {
        Tag tag = new Tag();
        tag.setName("Billing " + unique);
        tag.setColor("#ff0000");
        tag = tags.save(tag);
        Department department = new Department();
        department.setName("Finance " + unique);
        department = departments.save(department);

        JsonNode create = inertia(get("/escalated/admin/skills/create"));
        assertThat(create.get("component").asText()).isEqualTo("Escalated/Admin/Skills/Form");
        JsonNode form = create.get("props");
        assertThat(fieldNames(form)).contains("availableAgents", "availableTags", "availableDepartments")
                .doesNotContain("skill");
        JsonNode listedAgent = byId(form.get("availableAgents"), agent.getId());
        assertThat(fieldNames(listedAgent)).containsExactly("id", "name", "email");
        assertThat(fieldNames(byId(form.get("availableTags"), tag.getId()))).containsExactly("id", "name", "color");
        assertThat(byId(form.get("availableTags"), tag.getId()).get("color").asText()).isEqualTo("#ff0000");
        assertThat(fieldNames(byId(form.get("availableDepartments"), department.getId())))
                .containsExactly("id", "name");

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/skills").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Refunds %s","routing_tag_ids":[%d],"routing_department_ids":[%d],
                                 "agents":[{"user_id":%d,"proficiency":4}]}
                                """.formatted(unique, tag.getId(), department.getId(), agent.getId())))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/skills"));
        Skill skill = skills.findByName("Refunds " + unique).orElseThrow();
        assertThat(skill.getSlug()).isEqualTo("refunds-" + unique);

        JsonNode index = inertia(get("/escalated/admin/skills").session(session));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/Skills/Index");
        assertThat(index.get("props").get("flash").get("success").asText()).isEqualTo("Skill created.");
        JsonNode row = byId(index.get("props").get("skills"), skill.getId());
        assertThat(fieldNames(row)).containsExactly("id", "name", "slug", "agents_count", "routing_tags_count",
                "routing_departments_count", "updated_at");
        assertThat(row.get("agents_count").asInt()).isEqualTo(1);
        assertThat(row.get("routing_tags_count").asInt()).isEqualTo(1);
        assertThat(row.get("routing_departments_count").asInt()).isEqualTo(1);

        JsonNode edit = inertia(get("/escalated/admin/skills/" + skill.getId() + "/edit"));
        assertThat(edit.get("component").asText()).isEqualTo("Escalated/Admin/Skills/Form");
        JsonNode shown = edit.get("props").get("skill");
        assertThat(fieldNames(edit.get("props"))).contains("skill", "availableAgents", "availableTags",
                "availableDepartments");
        assertThat(fieldNames(shown)).containsExactly("id", "name", "slug", "routing_tag_ids",
                "routing_department_ids", "agents");
        assertThat(shown.get("routing_tag_ids").toString()).isEqualTo("[" + tag.getId() + "]");
        assertThat(shown.get("routing_department_ids").toString()).isEqualTo("[" + department.getId() + "]");
        assertThat(fieldNames(shown.get("agents").get(0))).containsExactly("user_id", "proficiency");
        assertThat(shown.get("agents").get(0).get("proficiency").asInt()).isEqualTo(4);

        write(put("/escalated/admin/skills/" + skill.getId()), """
                {"name":"Chargebacks %s","routing_tag_ids":[],"routing_department_ids":[],
                 "agents":[{"user_id":%d,"proficiency":2}]}
                """.formatted(unique, admin.getId()))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/skills"));
        assertThat(skills.findById(skill.getId()).orElseThrow().getName()).isEqualTo("Chargebacks " + unique);
        List<AgentSkill> assigned = agentSkills.findBySkill_Id(skill.getId());
        assertThat(assigned).extracting(AgentSkill::getUserId).containsExactly(admin.getId());
        assertThat(assigned.get(0).getProficiency()).isEqualTo(2);

        write(patch("/escalated/admin/skills/" + skill.getId()), "{\"name\":\"Disputes " + unique + "\"}")
                .andExpect(status().isSeeOther());
        assertThat(skills.findById(skill.getId()).orElseThrow().getName()).isEqualTo("Disputes " + unique);
        assertThat(agentSkills.findBySkill_Id(skill.getId())).isEmpty();

        write(delete("/escalated/admin/skills/" + skill.getId()), "{}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/skills"));
        assertThat(skills.existsById(skill.getId())).isFalse();
    }

    @Test
    void aSkillIsValidatedAsLaravelValidatesIt() throws Exception {
        AgentProfile customer = new AgentProfile();
        customer.setName("Customer " + unique);
        customer.setEmail("customer-" + unique + "@example.com");
        customer.setAgent(false);
        customer = agents.save(customer);
        write(post("/escalated/admin/skills"), "{\"name\":\"Taken " + unique + "\"}").andExpect(status().isSeeOther());

        assertInvalid(post("/escalated/admin/skills"), "{\"name\":\"\"}", "name");
        assertInvalid(post("/escalated/admin/skills"), "{\"name\":\"Taken " + unique + "\"}", "name");
        assertInvalid(post("/escalated/admin/skills"),
                "{\"name\":\"T " + unique + "\",\"routing_tag_ids\":[999999999]}", "routing_tag_ids.0");
        assertInvalid(post("/escalated/admin/skills"),
                "{\"name\":\"D " + unique + "\",\"routing_department_ids\":[999999999]}", "routing_department_ids.0");
        assertInvalid(post("/escalated/admin/skills"), "{\"name\":\"A " + unique
                + "\",\"agents\":[{\"user_id\":" + customer.getId() + ",\"proficiency\":3}]}", "agents.0.user_id");
        assertInvalid(post("/escalated/admin/skills"), "{\"name\":\"P " + unique
                + "\",\"agents\":[{\"user_id\":" + agent.getId() + ",\"proficiency\":6}]}", "agents.0.proficiency");
        assertInvalid(post("/escalated/admin/skills"), "{\"name\":\"Dup " + unique
                + "\",\"agents\":[{\"user_id\":" + agent.getId() + ",\"proficiency\":3},{\"user_id\":"
                + agent.getId() + ",\"proficiency\":2}]}", "agents.1.user_id");
        assertThat(skills.findByName("A " + unique)).isEmpty();
    }

    // ── Capacity ────────────────────────────────────────────────────────

    @Test
    void capacityListsEachAgentsLoadAndTheLimitIsEdited() throws Exception {
        AgentCapacity capacity = new AgentCapacity();
        capacity.setAgent(agent);
        capacity.setMaxTickets(8);
        capacity.setCurrentTickets(2);
        capacity = capacities.save(capacity);

        JsonNode page = inertia(get("/escalated/admin/capacity"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Capacity/Index");
        JsonNode row = byId(page.get("props").get("capacities"), capacity.getId());
        assertThat(fieldNames(row)).containsExactly("id", "user_id", "agent_name", "channel", "max_concurrent",
                "current_count", "load_percentage");
        assertThat(row.get("user_id").asLong()).isEqualTo(agent.getId());
        assertThat(row.get("agent_name").asText()).isEqualTo("Agent " + unique);
        assertThat(row.get("channel").asText()).isEqualTo("default");
        assertThat(row.get("max_concurrent").asInt()).isEqualTo(8);
        assertThat(row.get("current_count").asInt()).isEqualTo(2);
        assertThat(row.get("load_percentage").asDouble()).isEqualTo(25.0);

        write(put("/escalated/admin/capacity/" + capacity.getId())
                        .header("Referer", "http://localhost/escalated/admin/capacity"), "{\"max_concurrent\":12}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/capacity"));
        assertThat(capacities.findById(capacity.getId()).orElseThrow().getMaxTickets()).isEqualTo(12);

        assertInvalid(put("/escalated/admin/capacity/" + capacity.getId()), "{\"max_concurrent\":0}", "max_concurrent");
        assertInvalid(put("/escalated/admin/capacity/" + capacity.getId()), "{\"max_concurrent\":1000}",
                "max_concurrent");
        assertInvalid(put("/escalated/admin/capacity/" + capacity.getId()), "{}", "max_concurrent");
        assertThat(capacities.findById(capacity.getId()).orElseThrow().getMaxTickets()).isEqualTo(12);
    }

    // ── Business hours ──────────────────────────────────────────────────

    @Test
    void aBusinessScheduleIsCreatedEditedAndDeleted() throws Exception {
        JsonNode create = inertia(get("/escalated/admin/business-hours/create"));
        assertThat(create.get("component").asText()).isEqualTo("Escalated/Admin/BusinessHours/Form");
        assertThat(fieldNames(create.get("props"))).contains("timezones").doesNotContain("schedule");
        List<String> zones = new ArrayList<>();
        create.get("props").get("timezones").forEach(zone -> zones.add(zone.asText()));
        assertThat(zones).contains("UTC", "America/Toronto", "Europe/London").isSorted();

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/business-hours").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Toronto %s","timezone":"America/Toronto","is_default":true,
                                 "schedule":{"monday":{"start":"08:00","end":"16:30"},"tuesday":{"start":"09:00","end":"17:00"},
                                  "wednesday":{"start":"09:00","end":"17:00"},"thursday":{"start":"09:00","end":"17:00"},
                                  "friday":{"start":"09:00","end":"24:00"},"saturday":{"start":"","end":""},
                                  "sunday":{"start":"","end":""}},
                                 "holidays":[{"name":"Canada Day","date":"2026-07-01","recurring":true},
                                  {"name":"Retreat","date":"2026-11-13T00:00:00.000000Z","recurring":false}]}
                                """.formatted(unique)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/business-hours"));
        BusinessSchedule created = named("Toronto " + unique);
        assertThat(created.getTimezone()).isEqualTo("America/Toronto");
        assertThat(created.isDefaultSchedule()).isTrue();
        assertThat(created.getMondayStart()).isEqualTo("08:00");
        assertThat(created.getMondayEnd()).isEqualTo("16:30");
        assertThat(created.getFridayEnd()).isEqualTo("24:00");
        assertThat(created.getSaturdayStart()).isNull();
        assertThat(created.getSundayEnd()).isNull();

        JsonNode index = inertia(get("/escalated/admin/business-hours").session(session));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/BusinessHours/Index");
        assertThat(index.get("props").get("flash").get("success").asText()).isEqualTo("Business schedule created.");
        JsonNode row = byId(index.get("props").get("schedules"), created.getId());
        assertThat(fieldNames(row)).containsExactly("id", "name", "timezone", "is_default", "schedule", "holidays",
                "created_at", "updated_at");
        assertThat(row.get("is_default").asBoolean()).isTrue();
        assertThat(row.get("holidays").size()).isEqualTo(2);
        assertThat(fieldNames(row.get("holidays").get(0))).containsExactly("id", "name", "date", "recurring");
        assertThat(row.get("schedule").get("monday").toString()).isEqualTo("{\"start\":\"08:00\",\"end\":\"16:30\"}");
        assertThat(row.get("schedule").get("saturday").toString()).isEqualTo("{\"start\":\"\",\"end\":\"\"}");

        JsonNode edit = inertia(get("/escalated/admin/business-hours/" + created.getId() + "/edit"));
        assertThat(edit.get("component").asText()).isEqualTo("Escalated/Admin/BusinessHours/Form");
        assertThat(fieldNames(edit.get("props"))).contains("schedule", "timezones");
        JsonNode schedule = edit.get("props").get("schedule");
        assertThat(fieldNames(schedule.get("schedule"))).containsExactly("monday", "tuesday", "wednesday",
                "thursday", "friday", "saturday", "sunday");
        JsonNode canadaDay = schedule.get("holidays").get(0);
        assertThat(canadaDay.get("name").asText()).isEqualTo("Canada Day");
        assertThat(canadaDay.get("date").asText()).isEqualTo("2026-07-01");
        assertThat(canadaDay.get("recurring").asBoolean()).isTrue();
        assertThat(schedule.get("holidays").get(1).get("date").asText()).isEqualTo("2026-11-13");

        // A second default takes the flag from the first.
        write(post("/escalated/admin/business-hours"), """
                {"name":"London %s","timezone":"Europe/London","is_default":true,
                 "schedule":{"monday":{"start":"09:00","end":"17:00"}}}
                """.formatted(unique))
                .andExpect(status().isSeeOther());
        assertThat(named("Toronto " + unique).isDefaultSchedule()).isFalse();
        assertThat(named("London " + unique).isDefaultSchedule()).isTrue();

        write(put("/escalated/admin/business-hours/" + created.getId()), """
                {"name":"Ottawa %s","timezone":"America/Toronto","is_default":false,
                 "schedule":{"monday":{"start":"10:00","end":"18:00"},"tuesday":{"start":"","end":""}},
                 "holidays":[{"name":"Boxing Day","date":"2026-12-26","recurring":true}]}
                """.formatted(unique))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/business-hours"));
        BusinessSchedule updated = named("Ottawa " + unique);
        assertThat(updated.getMondayStart()).isEqualTo("10:00");
        assertThat(updated.getTuesdayStart()).isNull();
        assertThat(updated.getWednesdayStart()).isNull();
        JsonNode reloaded = inertia(get("/escalated/admin/business-hours/" + created.getId() + "/edit"))
                .get("props").get("schedule");
        assertThat(reloaded.get("holidays").size()).isEqualTo(1);
        assertThat(reloaded.get("holidays").get(0).get("name").asText()).isEqualTo("Boxing Day");

        // Deleting a schedule a policy follows leaves the policy on the default.
        SlaPolicy policy = new SlaPolicy();
        policy.setName("Hours " + unique);
        policy.setBusinessSchedule(updated);
        policy.setFirstResponseHours(java.util.Map.of("high", 1.0));
        policy.setResolutionHours(java.util.Map.of("high", 8.0));
        policy = slaPolicies.save(policy);
        write(delete("/escalated/admin/business-hours/" + created.getId()), "{}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/business-hours"));
        assertThat(schedules.existsById(created.getId())).isFalse();
        Long policyId = policy.getId();
        assertThat((Object) slaPolicies.findById(policyId).orElseThrow().getBusinessSchedule()).isNull();
    }

    @Test
    void aBusinessScheduleIsValidatedAsLaravelValidatesIt() throws Exception {
        String hours = "\"schedule\":{\"monday\":{\"start\":\"09:00\",\"end\":\"17:00\"}}";
        assertInvalid(post("/escalated/admin/business-hours"), "{\"timezone\":\"UTC\"," + hours + "}", "name");
        assertInvalid(post("/escalated/admin/business-hours"), "{\"name\":\"X\"," + hours + "}", "timezone");
        assertInvalid(post("/escalated/admin/business-hours"),
                "{\"name\":\"X\",\"timezone\":\"Mars/Olympus\"," + hours + "}", "timezone");
        assertInvalid(post("/escalated/admin/business-hours"), "{\"name\":\"X\",\"timezone\":\"UTC\"}", "schedule");
        assertInvalid(post("/escalated/admin/business-hours"),
                "{\"name\":\"X\",\"timezone\":\"UTC\",\"schedule\":{\"monday\":{\"start\":\"9am\",\"end\":\"17:00\"}}}",
                "schedule.monday.start");
        assertInvalid(post("/escalated/admin/business-hours"),
                "{\"name\":\"X\",\"timezone\":\"UTC\"," + hours + ",\"holidays\":[{\"date\":\"2026-01-01\"}]}",
                "holidays.0.name");
        assertInvalid(post("/escalated/admin/business-hours"),
                "{\"name\":\"X\",\"timezone\":\"UTC\"," + hours + ",\"holidays\":[{\"name\":\"N\",\"date\":\"soon\"}]}",
                "holidays.0.date");
    }

    // ── Audit log ───────────────────────────────────────────────────────

    @Test
    void theAuditLogListsEntriesWithTheirActorAndFilters() throws Exception {
        String type = "Widget" + unique;
        auditLogs.logWithIp("create", type, 7L, admin.getEmail(), "203.0.113.9", null, "{\"name\":\"New\"}");
        auditLogs.log("update", type, 7L, agent.getEmail(), "{\"name\":\"New\"}", "{\"name\":\"Renamed\"}");
        auditLogs.log("delete", type, 8L, "nobody-" + unique + "@example.com", null, null);

        JsonNode page = inertia(get("/escalated/admin/audit-log").param("auditable_type", type));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/AuditLog/Index");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("logs", "filters", "users", "actions", "resourceTypes");
        assertThat(props.get("filters").toString()).isEqualTo("{\"auditable_type\":\"" + type + "\"}");
        JsonNode logs = props.get("logs");
        assertThat(fieldNames(logs)).contains("data", "links", "current_page", "last_page", "per_page", "total");
        assertThat(logs.get("per_page").asInt()).isEqualTo(50);
        assertThat(logs.get("total").asInt()).isEqualTo(3);
        JsonNode newest = logs.get("data").get(0);
        assertThat(fieldNames(newest)).containsExactly("id", "user_id", "action", "auditable_type", "auditable_id",
                "old_values", "new_values", "ip_address", "created_at", "user");
        assertThat(newest.get("action").asText()).isEqualTo("delete");
        assertThat(newest.get("user").isNull()).isTrue();
        JsonNode update = logs.get("data").get(1);
        assertThat(update.get("user").get("name").asText()).isEqualTo("Agent " + unique);
        assertThat(update.get("user_id").asLong()).isEqualTo(agent.getId());
        assertThat(update.get("old_values").get("name").asText()).isEqualTo("New");
        assertThat(update.get("new_values").get("name").asText()).isEqualTo("Renamed");
        assertThat(update.get("auditable_type").asText()).isEqualTo(type);
        assertThat(update.get("auditable_id").asLong()).isEqualTo(7L);
        JsonNode created = logs.get("data").get(2);
        assertThat(created.get("ip_address").asText()).isEqualTo("203.0.113.9");
        assertThat(created.get("user").get("id").asLong()).isEqualTo(admin.getId());

        JsonNode users = byId(props.get("users"), agent.getId());
        assertThat(fieldNames(users)).containsExactly("id", "name");
        List<String> actions = new ArrayList<>();
        props.get("actions").forEach(action -> actions.add(action.asText()));
        assertThat(actions).contains("create", "update", "delete");
        List<String> types = new ArrayList<>();
        props.get("resourceTypes").forEach(t -> types.add(t.asText()));
        assertThat(types).contains(type);

        assertThat(total(get("/escalated/admin/audit-log").param("auditable_type", type).param("action", "update")))
                .isEqualTo(1);
        assertThat(total(get("/escalated/admin/audit-log").param("auditable_type", type)
                .param("user_id", String.valueOf(admin.getId())))).isEqualTo(1);
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        assertThat(total(get("/escalated/admin/audit-log").param("auditable_type", type).param("date_from", today)
                .param("date_to", today))).isEqualTo(3);
        assertThat(total(get("/escalated/admin/audit-log").param("auditable_type", type)
                .param("date_from", LocalDate.now(ZoneOffset.UTC).plusDays(2).toString()))).isZero();
        assertThat(total(get("/escalated/admin/audit-log").param("auditable_type", type)
                .param("date_to", LocalDate.now(ZoneOffset.UTC).minusDays(2).toString()))).isZero();

        JsonNode filtered = inertia(get("/escalated/admin/audit-log").param("auditable_type", type)
                .param("action", "update"));
        assertThat(filtered.get("props").get("logs").get("links").get(1).get("url").asText())
                .contains("action=update").contains("auditable_type=" + type).contains("page=1");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private int total(MockHttpServletRequestBuilder request) throws Exception {
        return inertia(request).get("props").get("logs").get("total").asInt();
    }

    private BusinessSchedule named(String name) {
        return schedules.findAll().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private void assertInvalid(MockHttpServletRequestBuilder request, String json, String field) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(request.session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost/escalated/admin/skills")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isSeeOther());
        JsonNode errors = inertia(get("/escalated/admin/skills").session(session)).get("props").get("errors");
        assertThat(fieldNames(errors)).as("errors for %s", json).contains(field);
    }

    private static JsonNode byId(JsonNode array, Long id) {
        for (JsonNode item : array) {
            if (item.get("id").asLong() == id) {
                return item;
            }
        }
        throw new AssertionError("no item " + id + " in " + array);
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
