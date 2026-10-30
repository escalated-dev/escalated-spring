# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.9] - 2026-10-30

### Upgrading
- **An Inertia visit to an Escalated page that ends in 403, 404, 419, 429, 500 or 503 now gets the shared `Escalated/Error` page** (props `status` and a generic `message`, as Laravel and Rails send them) instead of an empty body, Boot's error JSON or Spring Security's error, which the Inertia client could only show in a modal. A new servlet filter (`InertiaErrorPageFilter`, order `-101`, ahead of Spring Security) does it for `/escalated/**` while `escalated.ui.enabled` is on, so a refused gate or CSRF token, a status a controller returns, a `ResponseStatusException` and an unhandled exception (logged, then answered 500) all get the page. A controller's or exception's own text never reaches it. Untouched: requests without `X-Inertia` (full page loads, JSON and API clients), `/escalated/api/**`, the webhook, SSO, newsletter, WebSocket and widget paths, host routes, redirects, validation's 303 and the version check's 409. There is no debug mode to defer to, so a 500 always gets the page; the exception is in the log.

## [0.2.8] - 2026-10-30

### Upgrading
- Single sign-on is an install-level switch, `escalated.access.sso` /
  `ESCALATED_SSO`, default off. To turn it on: run the migrations (Flyway
  `V27` adds `escalated_sso_message_ids`; a Hibernate-built schema gets it from
  the new entity), give the IdP the endpoints from Admin > Settings > SSO, make
  sure a SAML install has the IdP certificate, decide on
  `escalated.sso.create-users` and `escalated.sso.sync-roles`, then set
  `ESCALATED_SSO=true`. `/escalated/sso/**` is open to guests and exempt from
  CSRF (each response is checked by its own signature or state).
- Two-factor authentication is an install-level switch,
  `escalated.access.two-factor` / `ESCALATED_TWO_FACTOR`, default off. To turn
  it on: run the migrations (Flyway `V26` adds `escalated_two_factors`; a
  Hibernate-built schema gets it from the new `TwoFactor` entity), set
  `escalated.two-factor.encryption-key` / `ESCALATED_TWO_FACTOR_SECRET` (keep
  it stable), then set `ESCALATED_TWO_FACTOR=true`. Set
  `ESCALATED_TWO_FACTOR_REQUIRED=true` (or the setting) only after telling
  staff. `TwoFactorService` was rewritten: its earlier methods
  (`enableTwoFactor`, `verifyAndActivate`, `verifyCode`, `disableTwoFactor`,
  `getProvisioningUri`), which no route used, are gone, and the
  `two_factor_secret` / `two_factor_enabled` columns of the agent profile are
  no longer read. See `docs/access-control.md`.
- Roles and permissions is now an install-level switch,
  `escalated.access.roles-permissions` / `ESCALATED_ROLES_PERMISSIONS`,
  default off (ADR 2026-10-29-configurable-access-features). Off, the roles
  screens and `/escalated/api/admin/roles*` answer 404 and the admin and agent
  gates are the only authority; `escalated.permissions` is `['*']` for staff.
  The newsletter screens no longer check `newsletters.manage` /
  `newsletters.send` while it is off, so any admin may use them; installs that
  relied on those permissions to restrict admins must turn the switch on.
- To turn it on: run the migrations (Flyway `V25` seeds escalated-laravel's
  permissions and the `admin`, `agent` and `light_agent` roles; a
  Hibernate-built schema gets them at startup while the switch is on), give
  every staff user a role (at least one `admin`, e.g.
  `PermissionSeeder.assignRole(email, "admin")`), then set
  `ESCALATED_ROLES_PERMISSIONS=true`. A user without a role has no
  permissions, whatever `is_admin` says. See `docs/access-control.md`.
- Flyway migration `V24` adds `escalated_plugins` and `escalated_plugin_store`
  (PostgreSQL and MySQL editions). Hosts whose schema Hibernate builds get both
  tables from the new entities.
- The plugin system is always present and inert until a plugin is registered
  and activated: with no `EscalatedPlugin` bean and the runtime off (the
  default), nothing runs and every page shares `escalated.plugins` as `{}`.
- Two security filter chains now come first: `/escalated/webhooks/plugins/**`
  (open, `@Order(-1)`) and `/escalated/api/plugins/**` (admin, session or API
  token, `@Order(0)`). A host chain ordered before Escalated's that matches
  these paths keeps them.
- `EscalatedSharedProps` has a constructor taking the `PluginRegistry`; the
  existing one still works and shares no plugin UI.

### Added
- **Admin > Users roles picker.** While roles & permissions is on, the users
  screen sends `roles` (`{id, name, slug}`, by name) and each user's role slugs,
  and `PUT C:/Program Files/Git/escalated/admin/users/{user}/roles` (`{role_ids: [...]}`, needs `role.manage`) changes a user's
  role, as escalated-laravel's `escalated.admin.users.roles`. A user has one
  role here, so the set maps to one role (see docs/access-control.md). 404 while
  the switch is off.
- Single sign-on while `ESCALATED_SSO` is on: SAML 2 (signed assertions with
  Issuer, Audience, Destination, Recipient, validity, InResponseTo and replay
  checks), OAuth 2 / OIDC (state + PKCE) and JWT (pinned algorithm, exp, aud,
  single-use jti), with user lookup or creation through `EscalatedSsoUsers`
  and optional role mapping. Admin > Settings > SSO is served.
- Two-factor authentication is enforced while `ESCALATED_TWO_FACTOR` is on:
  TOTP enrolment for every staff user (Admin > Settings > Two-factor), a
  challenge after the host's login on every admin and agent page
  (`Auth/TwoFactorChallenge`, codes single-use, recovery codes, throttled),
  and an optional "required" mode (`two_factor_required`). Admin > Settings
  accepts a post with only `two_factor_required`, as escalated-laravel #275.
- Roles and permissions are enforced while `ESCALATED_ROLES_PERMISSIONS` is
  on: every admin, agent and staff API route also needs its permission
  (`dev.escalated.security.RoutePermissions`, escalated-laravel's map; an
  unmapped staff route is refused), status changes to resolved/closed/reopened
  need `ticket.close`, bulk actions check each action, and API tokens are
  checked against their owner's permissions. Every page shares
  `escalated.features.roles_permissions`, `two_factor` and `sso`.
- Native plugins get cron schedules and widget badges, like SDK plugins:
  `EscalatedPlugin.cron()` (cron expression to `PluginCronTask`, run by
  `PluginCronScheduler` when due, as no user, within 30 s, for active plugins
  only) and `EscalatedPlugin.badges()` (widget component to `PluginBadge`,
  asked as the viewer within 5 s while `escalated.plugins.dashboardWidgets` is
  built; the answer is the widget's `badge`, `null` when it fails). Both
  default to none.
- SDK plugin cron schedules run: every minute `PluginCronScheduler` sends the
  due five-field (UTC) schedules of active SDK plugins to the runtime as
  `cron`, as no user. SDK plugin widgets with a `badge` function carry its
  answer in `escalated.plugins.dashboardWidgets[].badge`, asked as the viewer
  within 5 s (`null` when it fails).
- SDK plugin calls in flight at once keep their own users: the bridge reads
  the `host_call_id` the plugin runtime (after 0.2.0) stamps on each ctx call,
  so `ctx.currentUser` and the author of a plugin write are those of the
  request the call serves, even while another request is served. With an
  older runtime the bridge behaves as before.
- Plugins, per the shared plugin contract (escalated-developer-context
  `domain-model/plugin-contract.md`); see `docs/plugins.md`.
  - **Native plugins**: beans implementing `dev.escalated.plugins.EscalatedPlugin`
    (actions, filters, endpoints, webhooks, `onActivate`/`onDeactivate`), listed
    as `registered`. Two plugins with one slug, or a malformed route, fail at
    startup.
  - **SDK plugins** (`@escalated-dev/plugin-sdk`) through
    `@escalated-dev/plugin-runtime`, started with `ProcessBuilder` when
    `escalated.plugins.runtime.enabled=true` (off by default): JSON-RPC 2.0 over
    stdio, handshake, HTTP contract 1, `activate`/`deactivate`, the active
    allow-list on actions and filters, `ctx.*` served while the host waits and
    only for the plugin being dispatched, restart with backoff, and 503 /
    dropped actions / unfiltered values while it is down. Listed as `composer`.
  - **Admin > Plugins** (`Escalated/Admin/Plugins/Index`, `can_upload: false`):
    activate, deactivate and uninstall in the contract's order, idempotent, 303
    with a flash; an unknown slug is an error flash, or 404 for JSON. Uninstall
    keeps the plugin's stored data. No upload route.
  - **Plugin pages** (`Escalated/Plugin/Page`, props from the plugin's
    `GET /{component}`), **endpoints** (`/escalated/api/plugins/{slug}{path}`,
    admin plus capability, `:param` matching, credentials stripped, page-script
    header for state changes, generic 500s, validated `httpResponse()`
    envelopes) and **webhooks** (`/escalated/webhooks/plugins/{slug}{path}`,
    unguarded). Inactive plugins: 404.
  - **Domain events** to active plugins' actions after commit: every canonical
    event, never a legacy name, with the contract's payloads.
  - **ctx**: config, store (queries evaluated in Java, field names validated),
    tickets and replies through `TicketService` with the contract's keys only
    (500 rows), contacts, tags, departments, agents, broadcast (STOMP), log,
    emit (host names dropped), currentUser.
  - **`escalated.plugins`** on every page: active plugins' `menuItems`,
    `pageComponents` and `dashboardWidgets`, filtered by capability.
  - `PluginCapabilities` bean to decide capabilities (default: admins hold all,
    others their role's permissions).
- `TicketService.update(id, subject, body, priority, ticketType, metadata)`.

## [0.2.7] - 2026-10-28

### Upgrading
- The mobile guest routes are always mounted (this package has no REST API
  on/off switch) and permitted without a token. They honour the
  `guest_tickets_enabled` setting (default on): off, ticket creation, lookup and
  `ticket`/`lookup` codes there answer 403.
- `POST /escalated/api/widget/chat/start` now answers exactly as
  escalated-laravel: `201 {id, session_id, expires_at, ticket_reference,
  status, agent_name}`, where `id` and `session_id` are both the chat token,
  plus the widget's `messages` and `agent` and the existing camelCase session
  fields. `ticketId` is no longer returned; use `ticket_reference` to name the
  ticket.
- Flyway migration `V23` adds a nullable `widget_token_expires_at` to
  `escalated_chat_sessions`. Chats already in progress keep working: the next
  widget request with their token gives it a full lifetime.
- Widget chat tokens now expire `escalated.guest-access.ttl-minutes` after the
  chat starts (default 1440, clamped to 5 minutes .. 7 days), as in
  escalated-laravel. After that every `/widget/chat/{token}/...` route answers
  404, a STOMP `SUBSCRIBE` with it as `chat-token` is refused, and the visitor
  starts a new chat.

### Added
- The mobile guest API under `/escalated/api/v1/mobile/guest`, per the canonical
  contract (escalated-developer-context `domain-model/mobile-guest-api.md`):
  `POST verification`, `POST lookup`, `POST tickets`, `GET tickets/{token}` and
  `POST tickets/{token}/replies`, where `{token}` is the guest grant. They reuse
  the widget/guest verification and lookup handlers, the same emailed codes and
  expiring grants (a grant from any surface opens the others), and the same
  per-IP guest budgets; a bad, expired, rotated or chat grant is a 404. Every
  answer carries `Cache-Control: private, no-store` and
  `Referrer-Policy: no-referrer`. See `docs/guest-access.md`.
- `escalated.guest-rate-limit.requests-per-minute` (default 60): a per-IP budget
  counted on every mobile guest request.

### Security
- Checkstyle's Guava is pinned to 33.7.2-jre (GHSA-xxph-c9ww-hj94). Build
  tooling only; nothing in the published library depends on Guava.

### Changed
- `POST /widget/chat/start` also returns `expires_at`. Only the token's SHA-256
  is stored, as before.
- An ended chat answers 422 to `typing` (was 200) and to a second `end` (was
  200), and `rate` answers 422 until the chat has ended (it rated open chats),
  as in escalated-laravel. A message to an ended chat is refused before its
  body is checked. The transcript (`ended: true`) and rating stay available
  until the token expires.

## [0.2.6] - 2026-10-22

### Added
- Live chat transfer (escalated-laravel `ChatController::transfer`): `POST /escalated/api/agent/chat/{sessionId}/transfer`, behind the agent check like the rest of the agent chat API, and, for the shared `Agent/TicketShow` transfer menu, `POST /escalated/agent/chat/{session}/transfer` from the agent's browser session (CSRF-checked, like End Chat), registered as `escalated.admin.chat.transfer` (Laravel's name). The body is `{agent_id, department_id}` (either or both); the shared `ChatActionBar`'s `{type: "agent"|"department", id}` is accepted too. With an agent, the session and ticket are reassigned to them. With only a department, the ticket moves there and the chat goes back to the queue (unassigned, waiting), then chat routing runs again for the new department. A missing or non-integer target, an unknown department, an ended chat, or an agent who is not active staff or cannot be assigned the ticket is a 422; an unknown session is a 404. New `ChatSessionService.transfer(...)`.
- `chat.transferred` is sent to outbound webhooks, runs workflows and is now in `EventNames.EMITTED`, so the webhook form and workflow builder offer it. The webhook data adds `from_agent_id`, `to_agent_id` and `to_department_id` to the chat fields (no widget token). `ChatEvent` has a `TRANSFERRED` type and `getTransfer()`.
- End Chat on the agent ticket page works from the Inertia UI: `POST /escalated/agent/chat/{session}/end`
  (`escalated.admin.chat.end`, the name the shared TicketShow asks for, taking the chat session id)
  ends the session from the agent's signed-in browser session with the page's CSRF token. It reads
  no body and answers `200 {"message":"Chat ended."}`, `422 {"message":"Chat is already ended."}`
  when the chat has already ended (as escalated-laravel), and 404 for an unknown session. The
  existing `POST /escalated/api/agent/chat/{sessionId}/end` is unchanged; it is on the stateless
  API chain, so the page's browser session could not reach it.

## [0.2.5] - 2026-10-22

### Upgrading
- Run migration `V22__business_schedule_default` (PostgreSQL and MySQL). It adds
  `escalated_business_schedules.is_default`, false for existing rows. A business-hours SLA policy
  without its own schedule now follows the default schedule when one is set, and the first active
  schedule otherwise, as before.
- Business hours may end at `24:00` (the schedule editor's midnight), read as the end of the day.

### Added
- The Inertia UI renders `Escalated/Admin/Reports/AgentDetail` at
  `GET /escalated/admin/reports/agent/{id}/detail` (`escalated.admin.reports.agent-detail`, which
  the agent ranking links to): one agent's tickets, response percentiles, productivity, workload
  and CSAT breakdown over `days`, against the period before. The props are the ones the page
  declares; escalated-laravel's controller passes `agent_id`, `metrics` and `percentiles`, which
  the page does not read.
- The Inertia UI (`escalated.ui.enabled`) renders `Escalated/Admin/Skills/Index` and `Form`,
  `Admin/Capacity/Index`, `Admin/BusinessHours/Index` and `Form`, and `Admin/AuditLog/Index`, with
  escalated-laravel's props and routes (`escalated.admin.skills.*`, `capacity.*`,
  `business-hours.*`, `audit-log`). See `docs/ui.md`.

## [0.2.4] - 2026-10-22

### Upgrading
- Saving a webhook with the secret left blank keeps the stored secret, on the admin page (as
  before) and now also through `PUT /escalated/api/admin/webhooks/{id}`, which used to ignore
  `secret` altogether: a non-blank `secret` now replaces it there. Send `clear_secret: true` (page
  or JSON) to remove it.
- The JSON webhook API answers `has_secret` instead of embedding each webhook's deliveries, and
  a delivery answers `webhookId` instead of embedding its webhook. List deliveries through
  `GET /escalated/api/admin/webhooks/{id}/deliveries`.
- `RetentionScheduler` runs the data retention purge daily (`escalated.retention.cron`, default
  `0 30 3 * * *`). Every period defaults to `never`, so until an admin sets one on the Data
  retention page it only deletes expired guest verification codes. A closed ticket past its period
  is deleted outright with its replies, attachments, activities and the other rows that point at
  it: tickets have no soft delete here, so there is no 30-day grace period.
- Ticket numbers are still the ticket count plus one, but now step past a number that is still
  taken, which the count alone repeats once tickets have been deleted.
- Run the migrations: `V21__create_escalated_import_tables` adds `escalated_import_jobs` and `escalated_import_source_maps` (MySQL and PostgreSQL).
- To import attachments, declare an `AttachmentStorage` bean (for example `LocalFileAttachmentStorage` on a directory the web server does not serve); import adapters must send each file's bytes as `content`.
- Run migration `V20` (PostgreSQL and MySQL). It adds `escalated_ticket_statuses`, seeded with one
  system row per built-in status, and a nullable `escalated_tickets.ticket_status_id`
  (`ON DELETE SET NULL`). Existing tickets have no custom status and behave exactly as before; the
  built-in `status` column and its values are unchanged.
- Run migration `V19` (PostgreSQL and MySQL). It adds `context` (default `ticket`), `placeholder`,
  `validation_rules` and `conditions` to `escalated_custom_fields`, keeps one value per field per
  ticket in `escalated_custom_field_values` (the newest, where an install stored a pair twice) under
  a new unique key, and creates `escalated_custom_objects` and `escalated_custom_object_records`.
  Existing columns keep their names (`field_key` is the slug, `field_type` the type, `sort_order`
  the position).
- Once a required, active ticket custom field exists, ticket creates through
  `POST /escalated/api/admin/tickets`, `POST /escalated/api/customer/tickets` and the customer page
  must carry it in `custom_fields`, or answer 422 (the page: back with errors). With no custom
  fields defined, every request behaves as before.
- Run migration `V18` (PostgreSQL and MySQL). It converts SLA policies and escalation rules to
  escalated-laravel's shape, and existing ones keep doing what they did:
  - Each SLA policy's one `priority` with `first_response_minutes`/`resolution_minutes` becomes
    `first_response_hours`/`resolution_hours` maps (`{"high": 4}`) for that priority, and the
    policy is marked `is_default`. A policy with a business schedule becomes
    `business_hours_only` and keeps it. `department_id` and `ticket_type` are kept.
  - Each escalation rule no longer hangs off a policy (`sla_policy_id` is dropped, and deleting a
    policy no longer deletes its rules). Its policy and resolution breach become the conditions
    `sla_policy_id` and `sla_breached: resolution`, its routing conditions a
    `workflow_conditions` condition, its `action_type`/`action_target` a one-item `actions` list
    (`reassign` is `assign_to`), and `minutes_before_or_after` its position (`sort_order`).
- Java API: `SlaPolicy` loses `get/setPriority`, `get/setFirstResponseMinutes`,
  `get/setResolutionMinutes` and `getEscalationRules`, and gains `get/setFirstResponseHours`,
  `get/setResolutionHours` (per-priority maps), `is/setDefaultPolicy` and
  `is/setBusinessHoursOnly`. `EscalationRule` loses `get/setSlaPolicy`, `minutesBeforeOrAfter`,
  `actionType`, `actionTarget` and `notifyEmails`, and gains `actions`, `description`, `category`
  and `sortOrder`; `conditions` is now a list of `{field, value}`. A host that builds these in code
  needs the new setters.

### Added
- The admin Settings sub-pages, with escalated-laravel's routes, props, validation and flash
  messages (UI on, admins only): `Escalated/Admin/Settings/CsatSettings`
  (`/escalated/admin/settings/csat`), `DataRetention` (`/data-retention`, with `purgePreview`),
  `EmailSettings` (`/email`: `email_addresses` normalised to `email`, `display_name`,
  `department_id`, `dkim_status`, and `default_reply_address`; stored only, as in Laravel),
  `PublicTickets` (`/public-tickets`, the guest-policy keys the widget reads, saved over `POST` or
  `PUT`) and a read-only `DatabaseConnection` (`/database`, `POST /database/test`). The route names
  are in `UiRoutes`.
- `DataRetentionService` and `RetentionScheduler`: escalated-laravel's `escalated:purge-expired`
  for closed tickets, attachments, audit logs and expired guest verification codes.
- The admin Import wizard and the import framework behind it, as escalated-laravel's: the shared `Admin/Import/Index`, `Connect`, `Mapping`, `Review` and `Progress` pages on Laravel's paths and props under `/escalated/admin/import` (route names `escalated.admin.import.*`), admins only. `POST .../{platform}/test-connection` answers `{success[, error]}` (422 when the source throws); `review` writes nothing; `start` creates the job and queues it; `status/{job}` answers the Progress poll; `pause`, `resume` and `cancel` act on the job.
  - Sources are beans implementing `dev.escalated.contracts.ImportAdapter`; Escalated ships none, so a Zendesk or Freshdesk adapter is the host's or a plugin library's.
  - `ImportJob` and `ImportSourceMap` (migration `V21`), and `ImportJobService`, which resumes each entity type from its saved cursor, skips what is already mapped (so a paused, failed or interrupted run imports nothing twice), saves progress and an error log per batch, and stops between batches when the job is paused or cancelled. It imports agents (existing agent profiles, by email), contacts, tags, departments, tickets (status and priority falling back to open and medium, requester from the contact, assignee, department, tags, source creation time), replies, custom fields, attachments and satisfaction ratings.
  - Tested credentials wait in the session, sealed with AES-GCM under a key derived from `escalated.guest-access.secret`, until the import starts, then on the job; never in a page. A completed or cancelled job drops them.
  - Imports run on a thread of their own (`ImportRunner`); declare a `@Primary` `ImportDispatcher` bean to hand them to a queue and call `ImportJobService.run(jobId)` from it.
  - Import mode (`ImportContext`, thread-bound) holds back SLA policy attachment, SLA breach checks and automations during a run.
  - Custom fields: a `custom_fields` record (`name`, `type`, `options`) is found by slug or created through `CustomFieldService.save`, as Laravel's `persistCustomField`, so the admin form's rules hold (a known type, options for a select). A ticket record's `custom_fields` object (`{key: value}`) becomes the ticket's values: the wizard's ticket field mapping decides each key (`"custom_fields.<slug>"` lands on that field, `""` skips it), else the key is a custom field source id from the same import, else a slug. Each value is checked by `TicketCustomFieldValues.validate` as a ticket update is; one that fails is left out and logged under `custom_field_values` with the ticket's source id, and the ticket is kept. Fields a record does not carry are not required.
  - Attachments: an `attachments` record (`parent_type` `ticket` or `reply`, default `reply`; `parent_source_id`, `filename`, `content` as bytes or base64) is held to Laravel's live-upload rules (`escalated.imports.attachment-max-kb`, default 10240; Laravel's allowed extensions; markup refused whatever the name; the type from the extension; the base name only) and written through the host's `AttachmentStorage` bean, so it is served only by the authorised download route. Unlike Laravel, a source `path` is never trusted: a record without `content` fails, and with no `AttachmentStorage` bean every attachment fails.
  - Satisfaction ratings: a `satisfaction_ratings` record (`ticket_source_id`, `rating` or `score`, `comment`, `created_at`) is written on its imported ticket as a 1-5 integer, one per ticket, rated by the ticket's requester, with no access token and none of a live rating's rules or effects.
- Custom ticket statuses, as the shared statuses spec: admin-defined sub-statuses of five categories
  (`new`, `open`, `pending`, `on_hold`, `solved`), managed on `Escalated/Admin/Statuses/Index` and
  `Form` (`/escalated/admin/statuses`, behind `escalated.ui.enabled`, admins only) with
  escalated-laravel's props and validation; `is_default` is one per category; the built-in
  statuses' system rows keep their slug and category and cannot be deleted. Every status input
  (ticket pages, bulk action, admin API, macros, automations, workflows) takes a custom slug; the
  ticket takes the category's built-in anchor through the normal status change (so a `solved`
  status resolves, stops the SLA and is auto-closed), and setting any built-in status clears it.
  Staff ticket JSON and props gain `custom_status` and `status_category`; customers never see the
  custom label. Status-change webhooks that involve a custom status add `old_custom_status`,
  `new_custom_status`, `old_category` and `new_category`; built-in-only changes are unchanged.
  Changing a custom status's category does not rewrite tickets that already have it.
- Custom fields and custom objects, as escalated-laravel's (see `docs/custom-fields.md`):
  - Ticket values: the admin and customer ticket create/update APIs and the customer create page
    take `custom_fields`, an object keyed by field slug (or numeric id). Only active fields with
    context `ticket` count; an unknown or inactive key, a missing required field, or a value of the
    wrong kind (`number`, `date` as `YYYY-MM-DD`, `checkbox`, `select`/`multi_select` options,
    `text` up to 255) is an error keyed `custom_fields.<slug>`, checked before the ticket is
    written. On update only the keys sent change; an empty optional value clears it.
  - Ticket reads carry `custom_fields` (definitions with each `value`, decoded by type): the admin,
    agent and customer JSON detail responses, the customer `Create` page (definitions only) and the
    agent, admin and customer `Show` pages (a top-level prop).
  - The pages, behind `escalated.ui.enabled`: `Escalated/Admin/CustomFields/Index` and `Form`
    (`/escalated/admin/custom-fields`, with `reorder`) and `Escalated/Admin/CustomObjects/Index`,
    `Form` and `Records` (`/escalated/admin/custom-objects`, `.../{id}/records`), with Laravel's
    props and validation. A field's slug is made from its name when none is given (`_2`, `_3`
    when taken); a select needs at least one option. Record `data` is checked against the object's
    schema (`data.<name>` errors); deleting an object deletes its records.
  - JSON: `/escalated/api/admin/custom-fields` takes Laravel's keys as well as its older
    `field_key`/`field_type`, and answers 422 on invalid input; new
    `/escalated/api/admin/custom-objects` with records.
  - Not yet: values on guest and widget ticket creates.
- The SLA policy and escalation rule pages, behind `escalated.ui.enabled`, as escalated-laravel's:
  `Escalated/Admin/SlaPolicies/Index` and `Form` (`/escalated/admin/sla-policies`, with
  `tickets_count`, and the form's `priorities`, `targeted`, `departments` and `ticketTypes`) and
  `Escalated/Admin/EscalationRules/Index` and `Form` (`/escalated/admin/escalation-rules`), with
  create, update and delete, validated as Laravel's `StoreSlaPolicyRequest` and
  `StoreEscalationRuleRequest`. Deleting a policy leaves its tickets without one; deleting a rule
  forgets its fire log.
- The shared Inertia pages, behind `escalated.ui.enabled` (default `false`, so API-only hosts are
  unchanged; escalated-laravel defaults it to `true`). See `docs/ui.md`.
  - The Inertia protocol (`dev.escalated.ui.Inertia`): the first visit gets an HTML document with
    the page object (`data-page`, both the Inertia 3 script element and the Inertia 1/2
    attribute), `X-Inertia` visits get JSON, partial reloads, asset versioning with 409 and
    `X-Inertia-Location` (`escalated.ui.version`), 303 after writes, and validation errors and flash
    messages carried to the next page. The first-visit document is a replaceable
    `InertiaRootTemplate` bean, or a Thymeleaf view named in `escalated.ui.root-view`; the host's
    build is linked with `escalated.ui.scripts` and `escalated.ui.stylesheets`.
  - Shared props as Laravel shares them: `auth.user` (`id`, `name`, `email` only), `flash`,
    `errors` and `escalated` (`prefix`, `is_agent`, `is_admin`, `permissions`, `features`,
    `broadcasting`, `agent_type`, knowledge base and guest ticket switches).
  - A Ziggy-compatible `window.route()` built from `UiRoutes`, for hosts without Ziggy.
  - The agent pages: `Escalated/Agent/Dashboard` (`GET /escalated/agent`), `TicketIndex`
    (`GET /escalated/agent/tickets`, with Laravel's queue filters) and `TicketShow`
    (`GET /escalated/agent/tickets/{reference}`), with the writes TicketShow posts: `reply`, `note`,
    `assign`, `status`, `priority`, `tags`, `department`, `macro`, `follow` and `actions/{action}`.
  - The customer pages: `Escalated/Customer/Index` (`GET /escalated`), `Create`
    (`GET /escalated/create`, `POST /escalated`) and `Show` (`GET /escalated/{reference}`), with
    `reply`, `close`, `reopen` and `rate`. A customer sees only tickets they requested (by email);
    another's is a 404. Public replies only, staff by display name.
  - The admin pages: `Escalated/Admin/Settings` (with `supported_settings`: `allow_customer_close`,
    `show_powered_by` and the widget settings, the keys this port stores), `Workflows/Index` and
    `Form` (on the workflow admin service from #117, with toggle and reorder), `Automations/Index`
    and `Form` (on the automation admin service from #118), `Departments/Index` and `Form`, and
    `Tags/Index`, with their create, update and delete writes.
  - `GET /escalated/attachments/{id}/download`: the attachment download for a browser session, with
    the API download's access rules.
  - Page props are built field by field (`UiPresenter`), never by serialising entities.
- The admin ticket pages, behind `escalated.ui.enabled`: `Escalated/Admin/Tickets/Index`
  (`GET /escalated/admin/tickets`, with the agent queue's filters, plus `agents` and `savedViews`)
  and `Escalated/Admin/Tickets/Show` (`GET /escalated/admin/tickets/{reference}`, plus `agents` and
  `is_snoozed`), as escalated-laravel's `Admin\TicketController`. They post the agent ticket writes
  under `/escalated/admin/tickets/{reference}/` (`reply`, `note`, `assign`, `status`, `priority`,
  `tags`, `department`, `macro`, `follow`), and also `snooze` (`snoozed_until`) and `unsnooze`.
- `POST /escalated/admin/tickets/bulk`: the admin queue's bulk actions (`status`, `priority`,
  `assign`, `tags`, `department`, `delete`) on up to 50 tickets; a ticket the action fails on is
  skipped, as on Laravel.
- `GET`/`POST /escalated/admin/saved-views` and `PUT`/`DELETE /escalated/admin/saved-views/{id}`:
  the saved queue views the admin queue's sidebar and save dialog call (JSON). Only the owner may
  change or delete a view; shared views are listed to everyone.
- The admin configuration pages, behind `escalated.ui.enabled`, as escalated-laravel's:
  `Escalated/Admin/Macros/Index` and `CannedResponses/Index` (create, update, delete on the page),
  and `Webhooks/Index`, `Form` and `DeliveryLog` (create, update, delete, the delivery log and
  delivery retry). Webhook secrets are never sent to a page; a blank secret on update keeps the
  stored one.
- The people pages, behind `escalated.ui.enabled`, as escalated-laravel's: `Escalated/Admin/Users/Index`
  (search, and granting or revoking the admin and agent flags with
  `PATCH /escalated/admin/users/{id}/role`), `Roles/Index` and `Form` (create, update, delete, with
  the permissions grouped by category; system roles are refused), and `ApiTokens/Index` (issue,
  rename, revoke; the plain-text token is flashed once as `plain_text_token`).
- The knowledge base pages, behind `escalated.ui.enabled`, as escalated-laravel's:
  `Escalated/Admin/KnowledgeBase/Articles/Index` and `Form` and `Categories/Index` (create, update,
  delete), and the reader pages `Escalated/Customer/KnowledgeBase/Index` and `Article` at
  `/escalated/kb` and `/escalated/kb/{slug}`, with `POST /escalated/kb/{slug}/feedback`. The reader
  pages follow `escalated.knowledge-base.enabled` (404 when off) and the `knowledge_base_public`
  and `knowledge_base_feedback_enabled` settings.
- The guest pages, behind `escalated.ui.enabled`, as escalated-laravel's `Guest\TicketController`:
  `Escalated/Guest/Create` (`GET /escalated/guest/create`, `POST /escalated/guest` with a verified
  email code) and `Escalated/Guest/Show` (`GET /escalated/guest/{token}` by expiring grant, with
  `reply` and `rate`). `/escalated/guest/**` is open in the web chain when the UI is on. The form
  uses the existing `/escalated/api/guest/verification` and `/lookup`.
- `Escalated/Admin/Workflows/Logs` (`GET /escalated/admin/workflows/logs`, `?workflow=` to filter):
  the newest 100 workflow executions with `event`, `workflow_name`, `ticket_reference`, `matched`,
  `actions_executed` (a count), `action_details`, `status` and `duration_ms`, as escalated-laravel's
  `WorkflowController::logs`. `GET /escalated/admin/workflows/{id}/logs` redirects to it.
  (`Workflows/Builder` is not rendered by escalated-laravel either; the Form page is the builder.)
- The report screens, behind `escalated.ui.enabled`, as escalated-laravel's `Admin\ReportController`:
  `Escalated/Admin/Reports` (`GET /escalated/admin/reports`) and `Reports/Dashboard`,
  `AgentMetrics`, `SlaReport`, `CsatReport` and `Comparison` (`/reports/dashboard`, `/agents`,
  `/sla`, `/csat`, `/comparison`), each for the last `days` (default 30).
- The analytics report screens, behind `escalated.ui.enabled`, as escalated-laravel's
  `ReportController`: `Escalated/Admin/Reports/SlaTrends` (`/reports/sla-trends`, `group_by`
  `day`, `week` or `month`), `ResponseTimes` (`/reports/response-times`, also `/reports/frt`),
  `ResolutionTimes` (`/reports/resolution-times`, also `/reports/resolution`), `AgentRanking`
  (`/reports/agent-ranking`, Laravel's composite score) and `Cohorts` (`/reports/cohorts`: by tag,
  department, channel, type and priority). Counts and rates are grouped in SQL; hours, medians
  and 90th percentiles are worked out from the timestamps the queries return. Cohort CSAT is the
  average rating given on the cohort's tickets (Laravel's screen always shows 0 there).
- `MacroService` also applies the action types the shared Macros page saves (`status`, `priority`,
  `assign`, `tags`, `department`, `reply`, `note`), alongside the existing `set_status`,
  `set_priority`, `add_tag`, `add_reply`, `add_internal_note` and `assign`.
- `WebhookService.retryDelivery(deliveryId)` sends a recorded delivery again, and
  `WebhookService.update(..., secret)` replaces the signing secret when one is given.
- `TicketService.unsnooze(ticketId, actor)`: wakes a snoozed ticket now (status `open`).
- The built-in root page writes `<meta name="csrf-token">`, and the web chain accepts the token
  in `X-CSRF-TOKEN` as well as `X-XSRF-TOKEN`, so the pages' `fetch` calls (saved views, presence,
  mentions) pass CSRF as on Laravel. A custom `escalated.ui.root-view` receives it as `csrfToken`.

### Security
- **Stored secrets are never serialised**, as escalated-laravel 258. Webhook signing secrets, import
  source credentials, API token hashes and two-factor seeds were already kept out of every page
  and of Jackson. `SecretsNeverSerializedTest` now guards each page and JSON
  response that carries these records (import index, progress, status and wizard steps; webhook
  index, edit and delivery-log pages and the webhook JSON API; the API token page and JSON), and
  the entities' own serialisation. The import pages are built from Laravel's explicit field list
  (`id`, `platform`, `status`, `progress`, `field_mappings`, the newest 50 of `error_log`,
  `started_at`, `completed_at`, `created_at`), so `user_id` and `updated_at` are no longer sent
  and a column added later is not sent by default. Webhooks expose `has_secret` in their JSON too.
  This port has no SSO settings and keeps the inbound-email secret in host configuration, so
  there is no SSO or inbound secret on any settings page to mask.

### Fixed
- `GET /escalated/api/admin/webhooks`, `/{id}` and `/{id}/deliveries` failed for a webhook with a
  delivery: the webhook embedded its deliveries and each delivery its webhook again, until Jackson
  stopped at its nesting limit (500) after about 50 KB of the response had been sent.

### Changed
- A custom field's JSON no longer embeds its stored values (`values`), which looped back through
  each value's field and carried every ticket's answers. Field options are stored as a JSON array;
  options saved before as a comma-separated string still read as a list.
- **SLA policy selection follows escalated-laravel**: a new ticket gets the oldest active
  *default* policy with a target for its priority, or with `escalated.targeted-slas` the most
  specific match (department and type, department, type, then a default). A deadline the policy
  has no figure for is cleared. Business hours apply when `business_hours_only` is set, on the
  policy's schedule, else the first active schedule, else 09:00-17:00 Monday to Friday UTC.
- **Escalation rules are standalone, as on Laravel.** The SLA sweep runs the active rules in
  `sort_order` against every open ticket matching all their conditions (`status`, `priority`,
  `assigned`, `age_hours`, `no_response_hours`, `sla_breached`, `department_id`, `ticket_type`,
  `account`, `account_type`, `account_id`, plus `sla_policy_id` and `workflow_conditions`), and
  performs their actions in order (`escalate`, `change_priority`, `assign_to`,
  `change_department`; `notify` is still only logged). Still once per ticket, with
  `repeat_after_hours`, and every firing is announced as `ticket.escalated`. `escalate` changes no
  status (this port has no escalated status).
- With `escalated.ui.enabled=true` only: the `/escalated/**` web chain keeps its CSRF token in the
  `XSRF-TOKEN` cookie and accepts it in `X-XSRF-TOKEN` (what Inertia's client sends), and
  `/escalated/agent/**` and `/escalated/admin/**` take the agent and admin gates.

### Not rendered yet
- `Escalated/Admin/Statuses/*`: this port has fixed statuses, no custom ones.
- `Escalated/Admin/Reports/AgentDetail`: escalated-laravel passes it props the page does not read,
  so there is no contract to match yet; the ranking's agent links go nowhere. The report export is
  not served either.
- Admin ticket presence, pinned notes, split, merge, links and side conversations from the ticket
  page: the routes are not served here yet, so those controls do nothing.
- The newsletter screens still answer `{component, props}` JSON under
  `/escalated/api/admin/newsletters`.
- Attachments cannot be uploaded from the pages' writes yet (refused with an `attachments`
  error rather than dropped).

## [0.2.3] - 2026-10-21

### Upgrading
- **Widget tickets and chats now need an emailed code.** `POST /widget/tickets` and
  `POST /widget/chat/start` answer 422 without `verification_id` and `verification_code`. The
  bundled widget (0.12.3) sends them once `/config` asks. Configure outbound mail
  (`JavaMailSender`, optionally `escalated.guest-access.from-address`). Set
  `escalated.guest-access.secret` (or `escalated.email.inbound-secret`) to the same value on
  every node, or grants will not survive a restart. Run migration `V17`.
- Permanent guest tokens keep working on the token routes while
  `escalated.guest-access.legacy-tokens` is `true` (the default), until a grant is issued for the
  ticket. Turn it off once old links have aged out. See `docs/guest-access.md`.
- Run migration `V16` (automations). No automation exists until an admin creates one, so nothing
  changes on upgrade; the 7-day auto-close keeps running unless `escalated.automations.auto-close-days=0`.
- Run migration `V15` (it runs itself unless `escalated.datasource.migrate=false`).
- Legacy names keep working when read: a workflow saved as `ticket.tagged`, `ticket.replied`,
  `internal_note.added` or `chat.session_started`, or a webhook subscribed to them, receives the
  canonical event. Creating or updating a webhook stores canonical names.
- A webhook subscription is now matched name by name instead of by substring.

### Added
- `GET`/`POST /escalated/api/admin/tokens`, `PUT`/`DELETE /tokens/{id}`, which the README already
  documented and nothing mapped. They issue API tokens from `name`, `agent_id` (or `user_id`),
  `abilities` (`agent`, `admin`, `*`; default `*`) and `expires_in_days` (1-365). The plain-text
  token is answered once.
- Verified guest access, as in escalated-laravel (`docs/guest-access.md`):
  - `POST /verification` (on `/escalated/api/widget` and `/escalated/api/guest`) emails an 8-digit
    code for `ticket`, `chat` or `lookup`. Codes are bound to the address and purpose, last ten
    minutes, allow five guesses and are spent once. They are budgeted at 3 an hour per mailbox
    per client IP and 10 in total, and stored only as HMAC digests (`escalated_guest_verifications`,
    migration `V17`).
  - `POST /lookup` finds a ticket by reference and verified email and answers fresh grants.
  - `guest_access_token` is now an expiring, encrypted grant (`escalated.guest-access.ttl-minutes`,
    default 24 hours). The ticket stores only a hash of its nonce, so a new grant revokes earlier
    ones, and a changed requester email revokes them too. Send it as `Authorization: Bearer`
    with the ticket reference in the path. Guest answers are `no-store` and `no-referrer`.
  - The widget `/config` answers `guest_verification_required: true` and
    `status_lookup_enabled: true`. `GET /widget/tickets/{reference}` answers what the Check
    Status tab reads.
- Configurable automations, as in Laravel's `AutomationRunner`: a new `Automation` entity
  (`escalated_automations`, migration `V16`, PostgreSQL and MySQL) and admin JSON CRUD at
  `/escalated/api/admin/automations` (index, options, store, show, update, toggle, destroy), taking
  what the shared Automations form posts. Every `escalated.automations.check-interval-seconds`
  (default 60) each active automation acts on the open tickets matching all its conditions
  (`hours_since_created`, `hours_since_updated`, `hours_since_assigned` with `>`/`>=`/`<`/`<=`/`=`;
  `status`, `priority`, `assigned`, `ticket_type`, `department_id`, `account_type`, `account_id`,
  `subject_contains`) with `change_status`, `change_priority`, `assign`, `add_tag`,
  `set_ticket_type` and `add_note`. It acts once per ticket, again only after the ticket stops
  matching or after `repeat_after_hours` (1-8760), recorded in `escalated_rule_executions`.
- `escalated.automations.auto-close-days` (default 7, `0` = off) for the built-in auto-close of
  resolved tickets.
- Workflow admin JSON CRUD per the shared workflow admin contract (escalated-developer-context
  `domain-model/workflow-admin-contract.md`): `POST /escalated/api/admin/workflows`,
  `PUT`/`DELETE /workflows/{id}`, `POST /workflows/{id}/toggle`, `POST /workflows/reorder` and
  `GET /workflows/options`. They take the builder's body (`name`, `description`, `trigger_event`,
  `conditions` as `{all}`/`{any}`, `actions` as `{type, value}`, `is_active`). Invalid bodies
  answer 422 `{message, errors}`. A legacy trigger name is stored as its canonical name.
- Workflow conditions evaluate the contract's fields `channel`, `description`, `tags`,
  `assigned_to`, `hours_since_created` and `hours_since_updated`. Conditions stored as a flat list
  (read as `all`) or as `{match, rules}` keep working.
- Events from the shared vocabulary (escalated-developer-context ADR `2026-10-21-event-vocabulary`),
  sent to webhooks and fired as workflow triggers under their canonical names:
  `ticket.unassigned`, `ticket.department_changed`, `ticket.tag_added`, `ticket.tag_removed`,
  `ticket.escalated`, `note.created`, `sla.warning`, `sla.breached`, `chat.started`,
  `chat.assigned`, `chat.message` and `chat.ended`. `dev.escalated.events.EventNames` holds the
  canonical names and the legacy aliases.
- `POST /escalated/api/admin/tickets/{id}/department` (`department_id`, or null) and
  `DELETE /escalated/api/admin/tickets/{id}/tags/{tagId}`.
- `escalated.sla.warning-minutes` (default 30, `0` turns warnings off): `sla.warning` is announced
  once per deadline that falls due within that many minutes.
- `escalated_rule_executions` (migration `V15`, PostgreSQL and MySQL) records when an escalation
  rule, SLA breach or SLA warning last acted on a ticket, and `escalated_escalation_rules` gains a
  nullable `repeat_after_hours`.

### Changed
- **API token abilities are enforced**, as in Laravel's `AuthenticateApiToken`. The agent API needs
  a token with `agent` or `*`, the admin API one with `admin` or `*`, and the owner must still pass
  the gate. A token request is no longer let through by its owner's profile alone. Tokens issued
  until now hold `*`, so they keep working; an `admin`-only token no longer reaches the agent API.
- The built-in auto-close now reaches every resolved ticket past the threshold, oldest first. It
  used to look at an unordered first 100 resolved tickets only. Its 7-day default is unchanged.
- `GET /escalated/api/admin/workflows` and `GET /workflows/{id}` answer workflow objects
  (`conditions` as an object, `actions` as an array, snake_case keys) instead of the entity with
  JSON strings and its log collection.
- `WorkflowEngine.ACTION_TYPES` lists only what the executor performs. `send_webhook` and
  `send_notification` were listed but never executed; `insert_canned_reply` was executed but not
  listed.
- **Escalation rules act once per ticket.** The SLA sweep used to re-apply every matching rule
  every minute for as long as a ticket breached (an activity row and an `ticket.assigned` webhook
  each time). A rule now acts once, again only after the ticket stops breaching, or after the
  rule's `repeat_after_hours`. Each firing is announced as `ticket.escalated`.
- Unassigning a ticket is announced as `ticket.unassigned`, no longer as `ticket.assigned`.
- An internal note is announced as `note.created`. Webhooks used to get it as `reply.created`, and
  it no longer runs `reply.created` workflows.
- Resolving, closing and reopening are announced both as `ticket.status_changed` and as
  `ticket.resolved` / `ticket.closed` / `ticket.reopened`, to webhooks and workflows alike.
  Webhooks used to get only the specific name; workflows only `ticket.status_changed` (and
  `ticket.reopened` for a reopen).
- Changing a ticket's department to the one it is already in is a no-op (no activity row).
- The workflow trigger list (`WorkflowEngine.TRIGGER_EVENTS`) is the canonical list.
  `ticket.tagged` became `ticket.tag_added`/`ticket.tag_removed`; `reply.agent_reply`, which was
  offered but never fired and is not in the vocabulary, is gone.
- Chat-related and SLA webhook payloads add keys beside `event`, `entity_id` and `timestamp`
  (`chat_session_id`, `ticket_id`, `agent_id`; `sla_type`; `tag`; `reason`; department ids).
  The visitor's chat token is never included.

## [0.2.2] - 2026-10-20

### Changed
- The bundled widget is now `@escalated-dev/escalated` 0.12.3. It keeps its script settings when `embed.js` is placed in the page `<head>`, and Enter no longer picks a stale mention suggestion.

### Security
- Staff JSON no longer carries credentials. `AgentProfile.twoFactorSecret` (sent to every agent
  through `assignedAgent` on the agent ticket routes), the ticket guest access token, API token
  hashes, CSAT and newsletter tracking tokens are never serialised; a webhook secret is write-only.
- Attachment downloads follow the Laravel reference's access rules: agents need to see the ticket,
  anyone else must be the ticket's requester and never gets an internal note's attachment. The
  response is always `Content-Disposition: attachment` with an RFC 6266 filename, `nosniff`, and
  `application/octet-stream` for anything but a few inert types. Unknown ids answer 404, not 500.
- The STOMP endpoint (`escalated.broadcasting.enabled=true`) checks origins (same-origin plus
  `escalated.widget.allowed-origins`, no longer `*`), authorises every `SUBSCRIBE` (staff, or the
  chat's own widget token in a `chat-token` header) and refuses client `SEND` to broker topics.
- `escalated.widget.enabled=false` disables every widget route, not only `/config`; the widget's
  article routes answer 404 while the knowledge base is disabled.
- `POST /widget/tickets` answers `{message, reference, guest_access_token, expires_at}` instead of
  the whole ticket entity, which echoed the stored contact for any email address.
- Agent replies, chat messages and side conversations are authored by the signed-in agent; admin
  ticket actions and imports record the signed-in admin. Author and actor fields in the request
  body are ignored.
- Webhook URLs are resolved and every address checked (private, loopback, link-local, CGNAT,
  IPv6 ULA, IPv4-mapped) when saved and again before each delivery. Redirects are never followed.

## [0.2.1] - 2026-10-19

### Changed
- The bundled widget is now `@escalated-dev/escalated` 0.12.2. It hides its Check Status tab when `/config` returns `status_lookup_enabled: false`.

### Added

- Opt-in department and account ticket visibility across repositories, policies,
  HTTP/API queries, reports, bulk actions, widget lookups and ticket-owned records.
- Host account resolvers, additional department memberships, exact account filters,
  and account/type conditions for workflows, escalation rules and built-in automations.
- Optional department/type SLA targets, assignment eligibility checks and authorized
  notification/broadcast hooks. See [configuration and upgrade notes](docs/ticket-access-and-routing.md).
- Embedding the widget on other sites. Origins listed in
  `escalated.widget.allowed-origins` (`ESCALATED_WIDGET_ALLOWED_ORIGINS`) or the
  `widget_allowed_origins` admin setting get CORS on `/escalated/api/widget/**`,
  including preflights and error answers, and are always treated as guests.
  Nothing changes until an origin is listed. Adds `GET /escalated/api/widget/config`
  (with `accepts_metadata: true`), `GET /escalated/api/widget/embed.js` (the
  `@escalated-dev/escalated` 0.12.1 bundle), and an optional `metadata` object on
  widget tickets and chats, stored in the new `escalated_tickets.metadata` column
  (migration `V13`). See [embedding the widget](docs/embedding-the-widget.md).
- The widget API matches the shared widget (`@escalated-dev/escalated` 0.12.x):
  `GET /widget/articles?q=` and `GET /widget/articles/{slug}`;
  `POST /widget/tickets` reads `description` (`body` still works), files into
  an active `department_id`, answers 422 `{message, errors}` when `subject`,
  `email` or the description is missing, and adds top-level `message`,
  `reference` and `guest_access_token` to the ticket fields;
  `GET /widget/chat/{id}/messages`, `POST .../typing` and `POST .../rate`;
  `/widget/config` adds `status_lookup_enabled: false`.

### Changed

- `POST /widget/chat/start` answers with `id` set to the chat's widget token
  (a string) instead of the numeric session id, plus `messages` and `agent`;
  the other session fields stay.
- A widget ticket with a description but no `body` used to fail with a 500;
  it is now stored.

### Security

- Widget chat routes are keyed by an unguessable token instead of the numeric
  session id. Before, anyone could post into or end any chat by counting
  `POST /widget/chat/{sessionId}/messages` and `/end`, with any author name.
  The token is 256 random bits, stored only as a SHA-256 hash (migration
  `V14`, `escalated_chat_sessions.widget_token_hash`); numeric ids now get 404.
  Chats started before upgrading have no token and can no longer be reached
  from the widget; agents can still answer and end them.

### Security

- The customer ticket endpoints (`/escalated/api/customer/tickets`) now serve
  only the signed-in user's own tickets: the principal's name (their email) must
  match the ticket's requester email, ignoring case. Before, any signed-in user
  could list anyone's tickets with `?email=`, and read, list the replies of, or
  reply to any ticket by id. Someone else's ticket now answers 404. The `email`
  parameter is ignored, a new ticket is filed under the signed-in user, replies
  are filed as the ticket's requester whatever the body says, and the reply list
  leaves out internal notes.
- `GET /escalated/api/customer/tickets/{id}` answered with the agent detail view,
  so the owner of a live chat could read internal notes in `chat_messages`, and
  any owner saw linked tickets' subjects and numbers (`related_tickets`),
  `requester_ticket_count`, `chat_metadata`, `custom_actions`, `subjects` and the
  ticket entity's staff fields (activity log, assigned agent, SLA, tags, guest
  token, metadata). It now answers with a customer view: `id`, `reference`,
  `subject`, `description`, `status`, `priority`, `channel`, `department`
  (`{name}`), `requester_name`, `created_at`, `updated_at`, `resolved_at`,
  `closed_at`, `attachments`, `replies` (public replies only, as the guest
  widget's), `satisfaction_rating` (`{rating, comment, created_at}` or `null`)
  and, for a live chat, `chat_messages` (the same public replies; `null`
  otherwise). Clients reading any other field from this endpoint must stop.
- `GET /escalated/api/customer/tickets` and `POST /escalated/api/customer/tickets`
  answered with the ticket entity itself, so a customer's own list carried every
  internal note (in `replies`, and through `last_reply_author`), the activity
  log, the assigned agent's profile, SLA data, tags, metadata and the guest
  access token. The list (still a page: `content`, `totalElements`, ...) now
  holds customer summaries: `id`, `reference`, `subject`, `status`, `priority`,
  `channel`, `department` (`{name}`), `requester_name`, `created_at`,
  `updated_at`, `resolved_at`, `closed_at`, and `last_reply_at` /
  `last_reply_author` over public replies only. Creating a ticket answers `201`
  with the same customer view as `GET /customer/tickets/{id}`. Clients reading
  any other field from these endpoints must stop.
- `GET` and `POST /escalated/api/customer/tickets/{id}/replies` answered with
  the reply entity, so a customer saw each agent's email address (`authorEmail`),
  mail headers (`emailMessageId`) and the `internal` flag. Both now answer with
  the guest reply shape: `id`, `body`, `author_name`, `author_type`, `is_agent`,
  `created_at`, `attachments` (snake_case; the old camelCase `authorName`,
  `authorType` and `createdAt` are gone). The list still holds public replies
  only.
- `POST /escalated/api/widget/chat/start` had no rate limit, so anyone could
  create chat tickets without bound. It now shares the guest ticket limit
  (`escalated.guest-rate-limit.tickets-per-minute`, 5 per client IP per minute
  together with `POST /widget/tickets`) and answers `429` with `Retry-After`,
  which an allowed widget origin can read.

## [0.2.0] - 2026-10-08

### Upgrading

- **Only the requester can reply by email.** A threaded message becomes a reply
  only when `From` matches the ticket's requester email. Mail from anyone else,
  an agent's address included, opens a new ticket, so agents reply in the app.
- **Set `escalated.email.inbound-secret`.** With it set, only the signed
  Reply-To address links a message to a ticket; `In-Reply-To`, `References` and
  subject references no longer do.
- **Accepted email replies reopen resolved and closed tickets.**
- **Guest endpoints are rate-limited per client IP** (5 tickets and 10 replies
  a minute). Behind a proxy, set `server.forward-headers-strategy` and trust
  your proxies, or every guest shares one limit. A multi-instance host defines a
  shared `GuestRateLimitStore` bean. Configure with
  `escalated.guest-rate-limit.enabled`, `.tickets-per-minute` and
  `.replies-per-minute`.
- **Guest ticket responses have a new shape.** `GET .../tickets/{token}` returns
  a guest ticket DTO with public replies only, the reply list returns public
  replies only, and `POST .../tickets/{token}/replies` returns the reply DTO.
  Clients that read other entity fields from these responses must stop.
- **Guest replies ignore `name` and `email` in the request body.** They are
  filed as the ticket's requester.
- **An unknown guest token answers `403`** with
  `{"error":"Invalid guest access token"}` instead of an error page.

### Added
- **Per-IP rate limit on guest ticket creation and replies.**
  `POST /escalated/api/widget/tickets` allows 5 tickets and the guest reply
  endpoints (`/escalated/api/widget/tickets/{token}/replies` and
  `/escalated/api/guest/tickets/{token}/replies`, sharing one counter) 10
  replies per client IP per minute; beyond that the response is `429` with
  `Retry-After`. The check runs before the guest token is looked up, so
  wrong-token requests count. Counters live in a `GuestRateLimitStore`; the
  default is in memory (#96).

### Security
- **Guest ticket responses leaked internal notes.** The guest token endpoints
  returned the whole `Ticket` entity graph: every reply including internal
  notes, the activity log, the assigned agent's email and SLA data, and the
  guest reply list returned internal replies too. They now return
  `GuestTicketDto` / `GuestReplyDto` with public replies only (#98).
- **A guest could file a reply under any address.** The guest reply endpoints
  took the author's name and email from the request body. Replies are now filed
  as the ticket's requester, and a body without an email no longer fails (#99).
- **Inbound email replies are accepted only from the ticket's requester.** A
  message that threaded onto a ticket (by `In-Reply-To`, `References`, or a
  subject reference such as `[ESC-00001]`) was added as a reply whoever sent it,
  under the `From` name and address. A threaded message now becomes a reply only
  when `From` matches the ticket's requester email (case-insensitive); it is
  posted as the requester and reopens a resolved or closed ticket. Anyone else's
  message, including one naming an agent's address, opens a new ticket and leaves
  the matched one untouched. With `escalated.email.inbound-secret` configured,
  only the signed Reply-To address links a message to a ticket.

### Fixed
- **Ticket JSON nested until it was truncated.** Child-to-parent references
  (`Reply.ticket`, `TicketActivity.ticket`, `Department.tickets` and others)
  were serialized, so admin and agent ticket views, agent reply lists and the
  admin department endpoints returned about 45 KB of unparseable JSON under a
  200. Those back-references are now `@JsonIgnore` (#98).
- **An unknown guest token got an error page.** The six guest-token endpoints
  answer `403 {"error":"Invalid guest access token"}`, as the NestJS reference
  does, instead of forwarding to `/error` (a 401 behind host security, or a 500)
  (#97).

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
