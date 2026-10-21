# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
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
- `MacroService` also applies the action types the shared Macros page saves (`status`, `priority`,
  `assign`, `tags`, `department`, `reply`, `note`), alongside the existing `set_status`,
  `set_priority`, `add_tag`, `add_reply`, `add_internal_note` and `assign`.
- `WebhookService.retryDelivery(deliveryId)` sends a recorded delivery again, and
  `WebhookService.update(..., secret)` replaces the signing secret when one is given.
- `TicketService.unsnooze(ticketId, actor)`: wakes a snoozed ticket now (status `open`).
- The built-in root page writes `<meta name="csrf-token">`, and the web chain accepts the token
  in `X-CSRF-TOKEN` as well as `X-XSRF-TOKEN`, so the pages' `fetch` calls (saved views, presence,
  mentions) pass CSRF as on Laravel. A custom `escalated.ui.root-view` receives it as `csrfToken`.

### Not rendered yet
- `Escalated/Admin/SlaPolicies/*` and `Escalated/Admin/EscalationRules/*`: this port's SLA policy
  is one priority per policy, and its escalation rules hang off an SLA policy; the shared pages edit
  escalated-laravel's per-priority hour maps and standalone condition/action rules. Serving them
  needs a schema change first.
- `Escalated/Admin/Statuses/*`: this port has fixed statuses, no custom ones.
- Admin ticket presence, pinned notes, split, merge, links and side conversations from the ticket
  page: the routes are not served here yet, so those controls do nothing.

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
- Not rendered yet (remaining pages of `pages.json`): Admin ApiTokens, AuditLog, BusinessHours,
  CannedResponses, Capacity, CustomFields, CustomObjects, EscalationRules, Import, KnowledgeBase,
  Macros, Plugins, Reports (all), Roles, the Settings sub-pages, Skills, SlaPolicies, Statuses,
  Tickets, Users, Webhooks, Workflows Builder and Logs;
  Auth/TwoFactorChallenge; Customer KnowledgeBase; Error; Guest Create and
  Show; Plugin/Page. The newsletter screens still answer `{component, props}` JSON under
  `/escalated/api/admin/newsletters`. Attachments cannot be uploaded from the pages' writes yet
  (refused with an `attachments` error rather than dropped).
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
- With `escalated.ui.enabled=true` only: the `/escalated/**` web chain keeps its CSRF token in the
  `XSRF-TOKEN` cookie and accepts it in `X-XSRF-TOKEN` (what Inertia's client sends), and
  `/escalated/agent/**` and `/escalated/admin/**` take the agent and admin gates.
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
