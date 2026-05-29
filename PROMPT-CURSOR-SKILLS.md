# Cursor task: skills-management parity for escalated-spring

Read this whole file before doing anything. Self-contained brief.

## Goal

Bring `escalated-spring` to feature parity with the canonical Skills-management contract.

**Tracking issue:** https://github.com/escalated-dev/escalated-spring/issues/60
**Canonical contract:** https://github.com/escalated-dev/escalated-developer-context/blob/main/domain-model/skills-management.md
**ADR:** https://github.com/escalated-dev/escalated-developer-context/blob/main/decisions/2026-05-13-skills-routing-explicit-mapping.md
**NestJS reference (study):** https://github.com/escalated-dev/escalated-nestjs/pull/45
**Frontend contract:** https://github.com/escalated-dev/escalated/pull/65

## Current state

- `src/main/java/dev/escalated/models/Skill.java`: JPA `@Entity Skill` with name, description, `@ManyToMany(mappedBy="skills") Set<AgentProfile> agents`. **No proficiency tracking, no junction entity.**
- `src/main/java/dev/escalated/repositories/SkillRepository.java`: exists, basic Spring Data JPA.
- **No** AgentSkill junction entity, no controller, no routing service, no admin UI.

## Deliverables

1. **Flyway / Liquibase migrations** (in `src/main/resources/db/migration/` — match the existing repo's migration tool):
   - `V<next>__create_escalated_agent_skills.sql` — create the junction table: `id` PK, `user_id` BIGINT, `skill_id` BIGINT FK cascade, `proficiency` SMALLINT default 3, `created_at`/`updated_at`, unique `(user_id, skill_id)`, check constraint `1 <= proficiency <= 5`.
   - `V<next+1>__create_escalated_skill_routing_tags.sql` — `id` PK, `skill_id` FK cascade, `tag_id` FK cascade, unique `(skill_id, tag_id)`, timestamps.
   - `V<next+2>__create_escalated_skill_routing_departments.sql` — `id` PK, `skill_id` FK cascade, `department_id` FK cascade, unique `(skill_id, department_id)`, timestamps.
   - If the existing `Skill.agents` M2M (between `Skill` and `AgentProfile`) was backed by a table, drop it in favour of the new junction. Otherwise leave alone.

2. **JPA entities** (`src/main/java/dev/escalated/models/`):
   - `AgentSkill.java` — `@Entity` with `Long id`, `Long userId`, `@ManyToOne Skill skill`, `Integer proficiency` (1..5, validated), timestamps. `@Table(name = "escalated_agent_skills")`. Unique constraint on (userId, skillId).
   - `SkillRoutingTag.java` and `SkillRoutingDepartment.java` — junction entities.
   - Update `Skill.java`: drop the old M2M to `AgentProfile.skills`. Add `@OneToMany List<AgentSkill> agentSkills`, `@OneToMany List<SkillRoutingTag> routingTags`, `@OneToMany List<SkillRoutingDepartment> routingDepartments`. Add `description` if missing, add timestamps.
   - Update `AgentProfile` to drop its M2M `Set<Skill> skills` if it had one.

3. **Repositories**:
   - `AgentSkillRepository extends JpaRepository<AgentSkill, Long>` with `findByUserId`, `findBySkillId`, `deleteByUserId(Long userId)`, etc.
   - `SkillRoutingTagRepository`, `SkillRoutingDepartmentRepository`.

4. **DTOs** (`src/main/java/dev/escalated/dtos/admin/`):
   - `CreateSkillDto` / `UpdateSkillDto` with `name`, `description`, `routingTagIds: List<Long>`, `routingDepartmentIds: List<Long>`, `agents: List<AgentSkillEntryDto>`.
   - `AgentSkillEntryDto` with `userId: Long`, `proficiency: Integer` (`@Min(1) @Max(5)`).
   - Use Bean Validation (`@Valid` / `@NotNull` / etc.).

5. **Service** (`src/main/java/dev/escalated/services/SkillService.java`):
   - `listForAdmin()` → returns rows with the three count fields.
   - `findForEdit(id)` → returns the routing tag/department ID arrays and agent rows.
   - `getFormContext()` → returns available tags, departments, agents.
   - `create(dto)` / `update(id, dto)` / `delete(id)` — `@Transactional`, syncs all relations.
   - Add a `SkillRoutingService` if not present — `findMatchingAgents(Ticket ticket)` returning eligible users per the contract.

6. **Controller** (`src/main/java/dev/escalated/controllers/admin/SkillController.java`):
   - 6 endpoints under `/escalated/admin/skills`: GET (index), GET `/new`, POST, GET `/{id}/edit`, PUT `/{id}`, DELETE `/{id}`.
   - JSON request/response matching the contract.
   - Wrap writes in `@Transactional`.

7. **Tests** (`src/test/java/dev/escalated/`):
   - Controller integration tests via `MockMvc` covering the 6 endpoints.
   - Service tests for relation sync and proficiency persistence.
   - Routing service test asserting explicit-mapping behaviour.

## Process

1. `git checkout -b feat/admin-skills-management`.
2. Read the contract + NestJS PR diff before coding.
3. Implement: migrations → entities → repositories → DTOs → service → controller → tests.
4. Run: `./gradlew test` (or `mvn test`) until green, `./gradlew spotlessApply` (or whatever the repo uses) for formatting.
5. Commit logically, reference #60.
6. Push, open PR titled `feat(skills): admin skills management parity (#60)`.

## Constraints

- Wrap multi-table writes in `@Transactional`.
- Use Spring Data JPA conventions consistent with the rest of the codebase.
- Snake_case at the wire IF the rest of the controllers use snake_case; otherwise match local convention (Spring/Jackson default is camelCase).
- Don't touch unrelated files.
- Stop after pushing the PR. Do not auto-merge.
- The PROMPT file you're reading is untracked — do not include it in the PR.

## Self-check before pushing

- `./gradlew test` (or `mvn test`) — all green
- `./gradlew spotlessCheck` (or repo's lint task) — clean
- `git log --oneline` shows your commits
- All 7 deliverables addressed
