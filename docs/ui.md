# The Inertia pages

Escalated's agent, customer and admin screens are the Vue 3 + Inertia pages of
[`@escalated-dev/escalated`](https://github.com/escalated-dev/escalated). Every Escalated backend
serves the same pages (escalated-developer-context `guides/ui-optional-pattern.md`). This package
serves them behind one switch:

```properties
escalated.ui.enabled=true
```

It is **off by default**, so a host that uses Escalated as an API only is unchanged. (Laravel
defaults `escalated.ui.enabled` to `true`; here a host opts in.)

## What a host provides

- **Sign-in.** The pages live under `/escalated/**`, in Escalated's web security chain, which
  requires an authenticated session. The host's own login supplies it. The principal's name is the
  user's email address, as everywhere else in this package.
- **The frontend build.** The host bundles `@escalated-dev/escalated` with `@inertiajs/vue3` and a
  page resolver, as in the frontend package's README, and tells Escalated where the built files are:

  ```properties
  escalated.ui.scripts[0]=/build/assets/app.js
  escalated.ui.stylesheets[0]=/build/assets/app.css
  # Changes with each build; a client holding another version reloads (409).
  escalated.ui.version=2026-10-21.1
  escalated.ui.title=Support
  ```

## The protocol

`dev.escalated.ui.Inertia` implements the [Inertia protocol](https://inertiajs.com/the-protocol):

| Request | Answer |
|---|---|
| First visit (no `X-Inertia`) | HTML: the page object in `<script data-page="app" type="application/json">` (Inertia 3) and in `#app`'s `data-page` attribute (Inertia 1 and 2) |
| `X-Inertia: true` | The page object as JSON, with `X-Inertia: true` and `Vary: X-Inertia` |
| `X-Inertia-Version` differs from `escalated.ui.version` on a GET | `409` with `X-Inertia-Location`; the client reloads in full |
| `X-Inertia-Partial-Component` names the page | Only the props in `X-Inertia-Partial-Data`, less those in `X-Inertia-Partial-Except`; `errors` always |
| A write (POST, PUT, PATCH, DELETE) | `303` back to the page (its `Referer`) or to the ticket |
| A write with invalid input | `303` back, with `errors` (field to first message) on the next page |

Writes accept JSON, and form or multipart bodies (arrays as `key[0]`, `key[1]`), as Inertia sends
them. They need the CSRF token: with the UI on, the web chain keeps it in the `XSRF-TOKEN` cookie
(readable by script) and accepts it back in `X-XSRF-TOKEN`, which is what Inertia's client does.
The components that call `fetch` instead (saved views, presence, mentions) send the
`<meta name="csrf-token">` value as `X-CSRF-TOKEN`, as on Laravel; the built-in root page writes
that tag, and the chain accepts either header.

## Shared props

Every page receives, as Laravel's `HandleInertiaRequests` and `shareInertiaData` share them:

- `auth.user`: `{id, name, email}`, or null. `id` is the agent profile's id (what assignment and the
  "mine" filters take); a signed-in user without an agent profile has none.
- `flash`: `{success, error}`, shown once.
- `errors`: the last write's validation errors, shown once.
- `escalated`: `prefix` (`escalated`), `is_agent`, `is_admin`, `permissions` (`["*"]` for admins,
  otherwise the role's permission names), `features.newsletters`, `broadcasting.channel_prefix`,
  `agent_type`, `guest_tickets_enabled`, `show_powered_by`, `knowledge_base_enabled`,
  `knowledge_base_public` and `knowledge_base_feedback_enabled`.

Page props use snake_case keys exactly as escalated-laravel's controllers send them. They are built
field by field (`dev.escalated.ui.UiPresenter`), never by serialising an entity, so no stored
credential can reach a page.

## `route()`

The pages call Ziggy's `route(name, params)`. Laravel hosts get it from Ziggy. Here the root page
defines a compatible `window.route` from `dev.escalated.ui.UiRoutes`, unless the host already has
one. A route name this port does not serve yet answers `'#'` and logs a console warning, so a page
whose secondary screens are missing still renders.

## Customising

Each piece is a bean the host can replace:

| Bean | Default | For |
|---|---|---|
| `EscalatedUiRenderer` | `InertiaUiRenderer` | Rendering the pages some other way entirely |
| `InertiaRootTemplate` | `DefaultInertiaRootTemplate` | The first-visit HTML document |
| `UiRoutes` | the routes below | Adding or moving `route()` names |
| `EscalatedSharedProps` | as above | The shared props |
| any `InertiaSharedProps` | none | Adding shared props of the host's own (merged in `@Order`) |

Or name a Thymeleaf view for the first visit with `escalated.ui.root-view`. It receives `page`, the
JSON `pageJson`, `routesScript` (the `route()` definition) and `csrfToken` (for a
`<meta name="csrf-token">` tag):

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
  <script th:utext="${routesScript}"></script>
  <script type="module" src="/build/assets/app.js"></script>
</head>
<body>
  <div id="app" th:attr="data-page=${pageJson}"></div>
</body>
</html>
```

## Pages served

| Page | Route |
|---|---|
| `Escalated/Agent/Dashboard` | `GET /escalated/agent` |
| `Escalated/Agent/TicketIndex` | `GET /escalated/agent/tickets` |
| `Escalated/Agent/TicketShow` | `GET /escalated/agent/tickets/{reference}` |
| `Escalated/Customer/Index` | `GET /escalated` |
| `Escalated/Customer/Create` | `GET /escalated/create` |
| `Escalated/Customer/Show` | `GET /escalated/{reference}` |
| `Escalated/Admin/Settings` | `GET`/`POST /escalated/admin/settings` |
| `Escalated/Admin/Workflows/Index` | `GET /escalated/admin/workflows` |
| `Escalated/Admin/Workflows/Form` | `GET /escalated/admin/workflows/create`, `GET /escalated/admin/workflows/{id}` |
| `Escalated/Admin/Workflows/Logs` | `GET /escalated/admin/workflows/logs` (`workflow` to filter; `/workflows/{id}/logs` redirects there) |
| `Escalated/Admin/Automations/Index` | `GET /escalated/admin/automations` |
| `Escalated/Admin/Automations/Form` | `GET /escalated/admin/automations/create`, `.../{id}/edit` |
| `Escalated/Admin/Departments/Index` | `GET /escalated/admin/departments` |
| `Escalated/Admin/Departments/Form` | `GET /escalated/admin/departments/create`, `.../{id}/edit` |
| `Escalated/Admin/Tags/Index` | `GET /escalated/admin/tags` |
| `Escalated/Admin/Tickets/Index` | `GET /escalated/admin/tickets` (the agent queue's filters) |
| `Escalated/Admin/Tickets/Show` | `GET /escalated/admin/tickets/{reference}` |
| `Escalated/Admin/Macros/Index` | `GET /escalated/admin/macros` |
| `Escalated/Admin/CannedResponses/Index` | `GET /escalated/admin/canned-responses` |
| `Escalated/Admin/Webhooks/Index` | `GET /escalated/admin/webhooks` |
| `Escalated/Admin/Webhooks/Form` | `GET /escalated/admin/webhooks/create`, `.../{id}/edit` |
| `Escalated/Admin/Webhooks/DeliveryLog` | `GET /escalated/admin/webhooks/{id}/deliveries` |
| `Escalated/Admin/Users/Index` | `GET /escalated/admin/users` (`search`, 20 a page) |
| `Escalated/Admin/Roles/Index` | `GET /escalated/admin/roles` |
| `Escalated/Admin/Roles/Form` | `GET /escalated/admin/roles/create`, `.../{id}/edit` |
| `Escalated/Admin/ApiTokens/Index` | `GET /escalated/admin/api-tokens` |
| `Escalated/Admin/KnowledgeBase/Articles/Index` | `GET /escalated/admin/kb-articles` (`search`, `status`, `category_id`) |
| `Escalated/Admin/KnowledgeBase/Articles/Form` | `GET /escalated/admin/kb-articles/create`, `.../{id}/edit` |
| `Escalated/Admin/KnowledgeBase/Categories/Index` | `GET /escalated/admin/kb-categories` |
| `Escalated/Customer/KnowledgeBase/Index` | `GET /escalated/kb` (`search`, `category`) |
| `Escalated/Customer/KnowledgeBase/Article` | `GET /escalated/kb/{slug}` |
| `Escalated/Admin/SlaPolicies/Index` | `GET /escalated/admin/sla-policies` |
| `Escalated/Admin/SlaPolicies/Form` | `GET /escalated/admin/sla-policies/create`, `.../{id}/edit` |
| `Escalated/Admin/EscalationRules/Index` | `GET /escalated/admin/escalation-rules` |
| `Escalated/Admin/EscalationRules/Form` | `GET /escalated/admin/escalation-rules/create`, `.../{id}/edit` |
| `Escalated/Admin/CustomFields/Index` | `GET /escalated/admin/custom-fields` |
| `Escalated/Admin/CustomFields/Form` | `GET /escalated/admin/custom-fields/create`, `.../{id}/edit` |
| `Escalated/Admin/CustomObjects/Index` | `GET /escalated/admin/custom-objects` |
| `Escalated/Admin/CustomObjects/Form` | `GET /escalated/admin/custom-objects/create`, `.../{id}/edit` |
| `Escalated/Admin/CustomObjects/Records` | `GET /escalated/admin/custom-objects/{id}/records` |
| `Escalated/Admin/Reports` | `GET /escalated/admin/reports` |
| `Escalated/Admin/Reports/Dashboard`, `AgentMetrics`, `SlaReport`, `CsatReport`, `Comparison` | `GET /escalated/admin/reports/` `dashboard`, `agents`, `sla`, `csat`, `comparison` |
| `Escalated/Admin/Reports/SlaTrends`, `ResponseTimes`, `ResolutionTimes`, `AgentRanking`, `Cohorts` | `GET /escalated/admin/reports/` `sla-trends`, `response-times` (or `frt`), `resolution-times` (or `resolution`), `agent-ranking`, `cohorts` |
| `Escalated/Guest/Create` | `GET /escalated/guest/create` (no sign-in) |
| `Escalated/Guest/Show` | `GET /escalated/guest/{token}` (no sign-in) |

Customer writes: `POST /escalated` (`subject`, `description`, `priority`, `department_id`,
`ticket_type`, `custom_fields` keyed by field slug; see `docs/custom-fields.md`), and `POST /escalated/{reference}/` `reply`, `close` (unless the `allow_customer_close`
setting is off), `reopen` and `rate` (`rating` 1-5, `comment`; resolved or closed tickets, once). A
customer sees only the tickets whose requester email is theirs (any case); anyone else's is a 404.
The customer pages show public replies only, with staff by display name and no email.

Admin writes, as Laravel's resource routes: `POST` to the collection, `PUT` and `DELETE` on a
member. Workflows also take `POST /{id}/toggle` and `POST /reorder` (`workflow_ids`). Workflows
and automations go through the same services as their JSON admin APIs
(`/escalated/api/admin/workflows`, `/automations`), so both validate and store alike. The Settings
page's `supported_settings` lists only what this port stores and honours: `allow_customer_close`,
`show_powered_by`, `widget_color`, `widget_position`, `widget_greeting` and
`widget_allowed_origins`; the page hides the rest, and the save ignores any other key. Deleting a
department leaves its tickets and agents without one; deleting a tag takes it off its tickets.

Macros and canned responses take `POST` to the collection and `PUT`/`DELETE` on a member, and go
back to their page. A macro saved from the page uses the page's action types (`status`,
`priority`, `assign` by agent id or email, `tags` as comma-separated names, `department` by id or
name, `reply`, `note`), which `MacroService` applies alongside the API's `set_status`-style names.
Webhooks are a resource as on Laravel, plus `GET /webhooks/{id}/deliveries` (25 a page) and
`POST /webhooks/deliveries/{delivery}/retry` (sends the stored payload again, one more attempt). A
URL that is not http(s) or resolves to a private address is a `url` error. A webhook's signing
secret is never sent to a page: the form's secret field starts blank, and leaving it blank keeps
the stored secret.

Users are the agent profiles: `PATCH /escalated/admin/users/{id}/role` (`role` `admin` or `agent`,
`value`) grants or revokes the flag, as `UserService` does for the JSON API (granting admin also
grants agent, revoking agent also revokes admin, and an admin cannot remove their own admin role).
Roles are a resource; `permissions` on the form is grouped by category, each with `id`, `name`,
`slug` (the permission's name), `group` and `description`. System roles cannot be changed or
deleted; deleting a role leaves its agents without one. API tokens: `POST /escalated/admin/api-tokens`
(`name`, `user_id`, `abilities`, `expires_in_days`), `PUT` and `DELETE /api-tokens/{id}`; the new
token's plain text is flashed once as `flash.plain_text_token`, and no page carries a token hash.

The knowledge base: articles are a resource at `/escalated/admin/kb-articles` (`title`, `slug`,
`body`, `status` `draft` or `published`, `category_id`; a blank slug is made from the title, and
publishing stamps `published_at` once), categories at `/escalated/admin/kb-categories` (`name`,
`slug`, `parent_id`, `position`, `description`). Deleting a category leaves its articles
uncategorised and its sub-categories top level. Readers use `/escalated/kb`: published articles
only, each read counts a view, and `POST /escalated/kb/{slug}/feedback` (`helpful`) counts a vote
while the `knowledge_base_feedback_enabled` setting is on. The reader pages answer 404 while
`escalated.knowledge-base.enabled` is off, and 403 to someone not signed in once the
`knowledge_base_public` setting is turned off.

SLA policies and escalation rules are resources as on Laravel. A policy takes `name`,
`description`, `is_default`, `first_response_hours` and `resolution_hours` (hours per priority,
`{"high": 4}`; a blank figure means no target), `business_hours_only`, `is_active`, and
`department_id` and `ticket_type` for targeted SLAs (the form does not post these two, so an update
keeps them). New tickets get the oldest active default policy with a target for their priority (or,
with `escalated.targeted-slas`, the most specific match). Business hours follow the policy's own
schedule, else the first active one, else 09:00-17:00 Monday to Friday UTC. Deleting a policy
leaves its tickets without one. An escalation rule takes `name`, `description`, `trigger_type`,
`conditions` and `actions` (lists of `{field, value}` and `{type, value}`), `order`, `is_active` and
`repeat_after_hours`; the SLA sweep runs active rules in `order` against open tickets, each once per
ticket (see `docs/ticket-access-and-routing.md`).

The report screens take `days` (or `period`; 1-365, default 30) and cover tickets created in that
period, dates in UTC; the dashboard's status and priority breakdowns are all-time, as on Laravel.
A ticket breached its SLA when it was answered or resolved after the due time, or is still open
past it. Ratings are the ones given (a survey sent but not answered is not counted). The figures
are worked out from the period's tickets in memory, which suits the usual 30-day window; the JSON
reports under `/escalated/api/admin/reports` aggregate in SQL. The analytics screens (SLA trends,
response and resolution times, agent ranking, cohorts) group their counts and breach rates in SQL
and work out hours, medians and 90th percentiles from the timestamps the queries return. SLA trends
group breaches by creation `day`, ISO `week` or `month` (`group_by`) and list open tickets with a
deadline in the next 8 hours (at most 50). The response and resolution targets are 4 and 24 hours,
as on Laravel. The agent ranking scores as Laravel does: resolution rate 30%, first response 25%
(100 less 5 an hour, 50 without one), CSAT 25% and volume 20% (2 a ticket, at most 100).

The guest pages need no sign-in (`/escalated/guest/**` is open in the web chain; writes still need
the CSRF token). The form verifies the email with the existing `/escalated/api/guest/verification`
and finds earlier tickets with `/escalated/api/guest/lookup` (both named in its props), then posts
`POST /escalated/guest` (`guest_name`, `guest_email`, `subject`, `description`, `priority`,
`department_id`, `verification_id`, `verification_code`). The code is spent with the ticket, and
the guest is sent to `/escalated/guest/{grant}`, the expiring grant from `docs/guest-access.md`.
That page posts `reply` (refused once the ticket is closed) and `rate` (resolved or closed tickets,
once). An unknown, expired or revoked link is a 404; a permanent token still opens its ticket while
`escalated.guest-access.legacy-tokens` is on. The form is a 404 while the `guest_tickets_enabled`
setting is off. Guest responses send `Cache-Control: no-store, private` and
`Referrer-Policy: no-referrer`, and the writes share the guest API's rate limits. Attachments are
listed on the guest page without a download link (downloads need a signed-in session).

Agent writes: `POST /escalated/agent/tickets/{reference}/` `reply`, `note`, `assign` (`agent_id`),
`status`, `priority`, `tags` (`tag_ids`), `department` (`department_id`), `macro` (`macro_id`),
`follow` and `actions/{action}`. The admin ticket page posts the same writes under
`/escalated/admin/tickets/{reference}/`, less `actions/{action}`, plus `snooze` (`snoozed_until`, a
future date-time; the ticket becomes `snoozed`) and `unsnooze` (back to `open`). The admin queue
posts `POST /escalated/admin/tickets/bulk` (`ticket_ids`, at most 50; `action` one of `status`,
`priority`, `assign`, `tags`, `department`, `delete`; `value`), which skips a ticket the action
cannot apply to and flashes how many changed. Its saved views are JSON, called with `fetch`:
`GET`/`POST /escalated/admin/saved-views`, `PUT`/`DELETE /escalated/admin/saved-views/{id}`
(`name`, `filters` object, `is_shared`, `is_default`); only a view's owner changes it. Attachments to the pages' writes: `GET
/escalated/attachments/{id}/download`, with the same access rules as the API's download.

The agent pages need the agent gate, the admin pages the admin gate (the same as the
`/escalated/api/agent` and `/escalated/api/admin` APIs). Tickets outside an agent's department or
account scope are a 404.

Statuses are the lower-cased constants (`open`, `pending`, `in_progress`, `resolved`, `closed`,
`snoozed`, `merged`). The shared frontend's `waiting_on_customer` and `waiting_on_agent` are stored
as `pending`; its `escalated` status and `critical` priority do not exist here and are refused.

The newsletter screens predate this layer and still answer `{component, props}` JSON under
`/escalated/api/admin/newsletters`.
