# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.1] - 2026-09-13

### Security
- **The admin and agent APIs now check who is calling.** `/escalated/api/admin/**`
  required nothing more than a logged-in user, so any account the host
  authenticated could manage users, roles, settings and webhooks. It now requires
  an admin, and `/escalated/api/agent/**` an agent or admin — the same two gates
  as every other Escalated host. The caller is matched to
  `escalated_agent_profiles` by principal name (email) and must be active with
  `is_admin` / `is_agent` set; a host that keeps roles in its own user store can
  grant `ROLE_ESCALATED_ADMIN` / `ROLE_ESCALATED_AGENT` instead.

  **Upgrading:** a host user who reached the admin API before this change and
  has no admin profile or authority now gets 403.

### Fixed
- **Inbound email is accepted again.** The web security chain required a login
  and a CSRF token on `/escalated/webhook/**`, so every provider webhook was
  refused before `InboundEmailController` could check the shared secret. The
  secret header is now the only gate, as documented.
- **A valid API token authenticates.** The token's agent was loaded lazily and
  read after the transaction closed, so every valid token was answered with 401.
  Using a token also records `last_used_at`, which a read-only transaction had
  been silently discarding.
- **The token filter no longer runs outside Escalated's API chain.** As a plain
  `Filter` bean Boot registered it on every request, where it answered the
  host's own `Bearer` routes — and the host-issued tokens sent to
  `/escalated/api/v1/auth/*` — with 401.
- The README named the inbound-email properties `escalated.mail.*`; they are
  `escalated.email.*`.
- **Webhooks and workflows run after the change that raised them, off the
  request thread.** Both listeners were `@Async`, which does nothing without
  `@EnableAsync`, and nothing enabled it. A webhook's HTTP call ran on the
  request thread inside the ticket's transaction, so a failing endpoint rolled
  the ticket back, and a receiver fetching the ticket could not yet see it.

  Escalated's auto-configuration now enables async execution. Both listeners are
  `@TransactionalEventListener`s, so they run once the change has committed.
  Workflows load the ticket again by id on the worker thread.

  **Host impact:** `@EnableAsync` applies application-wide. `@Async` methods
  elsewhere in a host that had never enabled it now run asynchronously too.
- **`reply.created` workflows and `reply.*` webhooks fire.**
  `TicketService.addReply` published no event.
- **Workflow conditions and actions are stored as JSON.** The `String` fields
  mapped to `JSON` columns were bound as plain strings:
  - PostgreSQL rejected the insert (`column "actions" is of type json but
    expression is of type character varying`).
  - H2 stored a JSON string literal the engine could not parse.

  So no workflow with actions ran on either.
- **Escalated's migrations run, on PostgreSQL as well as MySQL.** They did not run on
  either. Boot 4 moved Flyway auto-configuration into `spring-boot-flyway`, which
  Escalated does not bring in, so on a shared database nothing ran them and a host
  on `ddl-auto=validate` could not start. Flyway 12 also needs a module per
  database, so on a dedicated database Flyway answered `Unsupported Database` for
  both engines. The scripts were MySQL-only (`AUTO_INCREMENT`, `MODIFY COLUMN`),
  and MySQL's V1 fails on MySQL 8, which rejects `TEXT DEFAULT '*'`.

  Escalated now runs its migrations itself, in both database arrangements, from
  `db/escalated/{postgresql,mysql}` into `escalated_flyway_schema_history`.
  - The MySQL V1-V10 are byte-for-byte what 0.1.0 shipped, so recorded checksums
    still match.
  - A fresh MySQL install runs V1 without the one default MySQL 8 refuses, and is
    baselined at 1.
  - An install whose own Flyway applied the scripts from `db/migration` is adopted,
    and those rows are removed from the host's history.
- **The migrations create the schema the entities map.** V11 adds:
  - `escalated_tickets.channel`
  - timestamps on `escalated_newsletter_list_members`
  - the `escalated_workflows`, `escalated_workflow_logs`,
    `escalated_deferred_workflow_jobs`, `escalated_chat_sessions` and
    `escalated_chat_routing_rules` tables, which no migration had created
  - corrected types for `proficiency` and `attempt_count`

  `AgentProfile.signature` and `EscalationRule.notifyEmails` now map to the
  `TEXT` columns the migrations create. New tests run every migration on an
  empty PostgreSQL and MySQL database and boot with `ddl-auto=validate`.
- `application-test.properties` moved to the test classpath; the library jar
  shipped the suite's H2 profile.

## [0.1.0] - 2026-09-12

### Changed
- **The test suite runs on PostgreSQL and MySQL as well as H2.** It had only
  ever seen H2 — the one database no host deploys on. `ESCALATED_TEST_URL` and
  its companions select the connection, defaulting to H2 so running the suite
  locally still needs nothing installed.

  Which database the suite is *meant* to be on is passed separately, as
  `-Pdatabase=...`. That is deliberate: a Gradle daemon started before the
  environment variables were exported keeps the environment it was started with,
  so an environment-only check cannot tell a real PostgreSQL leg from one that
  silently fell back to H2. A command-line property cannot be stale, and
  `DatabaseEngineTest` compares it against the product name the connection
  actually reports.

  315 tests pass on all three. `DatabaseConnectionTest` stays pinned to H2
  whatever the leg: the claim it makes is that two `DataSource`s are two
  databases, which is the same wiring on every engine.

### Added
- **Configurable database connection.** Every repository in the package bound to
  the host's `EntityManagerFactory`, which forced Escalated's tables into the
  host's database — unusable in any host that partitions its data. Setting
  `escalated.datasource.url` now gives Escalated a persistence unit of its own:
  its repositories bind to it, its transactions open on it, and its Flyway
  migrations run against it under their own schema history table. The two need
  not be the same engine.

  Leave it unset — the default — and nothing changes.
  `escalatedEntityManagerFactory` and `escalatedTransactionManager` are then
  registered as *aliases* of the host's own beans, not as new ones: a second
  bean of either type would make `@Autowired EntityManagerFactory` ambiguous
  throughout a host application that has nothing to do with support tickets.

  All 161 `@Transactional` sites now name `escalatedTransactionManager`. A bare
  `@Transactional` resolves the host's primary transaction manager, which is the
  right bean while the database is shared and the wrong one the moment it is
  not — the transaction would open on a connection with none of Escalated's
  tables in it.

  Escalated's entities are scanned into the host's persistence unit only while
  the database is shared. Otherwise Hibernate would map every support table onto
  the host's database as well, validating — and under `ddl-auto`, creating —
  tables the host never asked for.

### Fixed
- `WorkflowEngine` carried no stereotype annotation, so it was never registered
  as a bean while `WorkflowRunnerService` required it in its constructor. No
  application context that component-scanned the package could start. The suite
  is entirely Mockito and slice tests, so nothing had ever tried.
- Ticket subjects: attach host-app entities (Project, Customer, asset, …) that a ticket is *about*, distinct from the requester. Host models implement `dev.escalated.contracts.TicketSubject`; a `TicketSubjectResolver` bean resolves type/id pairs for serialization. Tickets expose `subjects[]` on detail responses; admin attach/detach endpoints honor `escalated.ticket-subjects.types`. `subject_id` is stored as `VARCHAR(255)` for integer, UUID, or string host keys. Mirrors Laravel reference (`escalated-laravel#122`).
- Admin users-management endpoint (`GET /escalated/api/admin/users`, `PATCH /escalated/api/admin/users/{userId}/role`) backing the `Escalated/Admin/Users/Index` page in the shared frontend. Lets an admin grant or revoke the `is_admin` / `is_agent` flags from the panel, with self-demote protection so an admin cannot lock themselves out. Mirrors the Laravel reference (`escalated-laravel#94`).

### Changed
- Translations are now consumed from the central `dev.escalated:escalated-locale` Maven artifact via a chained `ReloadableResourceBundleMessageSource`. Host apps can layer sparse overrides under `classpath:i18n/overrides/messages_{locale}.properties`.

### Fixed
- Make `SimpMessagingTemplate` an optional dependency so the app boots without STOMP broker configuration (#19)
- Include `url` in attachment JSON serialization (#10)
- Include computed ticket fields in ticket JSON serialization (#11)
- Include chat, context panel, and activity fields in ticket serialization (#12)
- Include missing workflow and workflow log computed fields in serialization (#13)

### Internal
- Docker dev/demo environment under `docker/` with click-to-login agent picker and seeded profiles (#14, #18)
- Complete README translations across supported locales (#9)

## [0.1.0] — initial release

Spring Boot 3.2 port of `escalated` reaching feature parity with the Laravel reference: tickets, workflow engine, chat, KB, reports, SLA tracking, and Inertia-driven Vue frontend served through the shared `@escalated-dev/escalated` package.
