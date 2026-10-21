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
JSON `pageJson`, and `routesScript` (the `route()` definition):

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

Customer writes: `POST /escalated` (`subject`, `description`, `priority`, `department_id`,
`ticket_type`), and `POST /escalated/{reference}/` `reply`, `close` (unless the `allow_customer_close`
setting is off), `reopen` and `rate` (`rating` 1-5, `comment`; resolved or closed tickets, once). A
customer sees only the tickets whose requester email is theirs (any case); anyone else's is a 404.
The customer pages show public replies only, with staff by display name and no email.

Agent writes: `POST /escalated/agent/tickets/{reference}/` `reply`, `note`, `assign` (`agent_id`),
`status`, `priority`, `tags` (`tag_ids`), `department` (`department_id`), `macro` (`macro_id`),
`follow` and `actions/{action}`. Attachments to the pages' writes: `GET
/escalated/attachments/{id}/download`, with the same access rules as the API's download.

The agent pages need the agent gate, the admin pages the admin gate (the same as the
`/escalated/api/agent` and `/escalated/api/admin` APIs). Tickets outside an agent's department or
account scope are a 404.

Statuses are the lower-cased constants (`open`, `pending`, `in_progress`, `resolved`, `closed`,
`snoozed`, `merged`). The shared frontend's `waiting_on_customer` and `waiting_on_agent` are stored
as `pending`; its `escalated` status and `critical` priority do not exist here and are refused.

The newsletter screens predate this layer and still answer `{component, props}` JSON under
`/escalated/api/admin/newsletters`.
