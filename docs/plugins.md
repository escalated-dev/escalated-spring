# Plugins

Escalated Spring runs two kinds of plugin through one registry, one activation
table and one admin screen (Admin > Plugins):

- **Native plugins**: Java beans implementing `dev.escalated.plugins.EscalatedPlugin`.
- **SDK plugins**: TypeScript plugins written with `@escalated-dev/plugin-sdk`
  (`definePlugin()`), run by the `@escalated-dev/plugin-runtime` Node process
  over a JSON-RPC bridge. The official plugins (`escalated-plugins`) are SDK
  plugins.

What every Escalated backend shares is the plugin contract in
escalated-developer-context (`domain-model/plugin-contract.md`). This page covers
what is specific to this package.

## Contents

- [Native plugins](#native-plugins)
- [SDK plugins](#sdk-plugins)
- [Admin screen and pages](#admin-screen-and-pages)
- [Endpoints and webhooks](#endpoints-and-webhooks)
- [Events, filters and ctx](#events-filters-and-ctx)
- [Capabilities](#capabilities)
- [Storage](#storage)
- [Differences from the contract](#differences-from-the-contract)

## Native plugins

Declare a bean. Every `EscalatedPlugin` bean is listed on Admin > Plugins as
`registered`, inactive until an admin activates it. Only `manifest()` is
required.

```java
@Component
public class BillingPlugin implements EscalatedPlugin {

    @Override
    public PluginManifest manifest() {
        return PluginManifest.of("acme/billing", "1.0.0")
                .withDescription("Invoices on tickets")
                .withPages(new PluginManifest.Page("settings", "Settings", null, null,
                        new PluginManifest.Menu("Billing", "admin", 50, "credit-card")));
    }

    @Override
    public Map<String, PluginAction> actions() {
        return Map.of("ticket.created", (event, ctx) ->
                ctx.store().insert("seen", Map.of("ticket", event.get("id"))));
    }

    @Override
    public List<PluginRoute> endpoints() {
        return List.of(PluginRoute.of("GET", "/Settings", (request, ctx) -> ctx.config().all()));
    }
}
```

- `onActivate(ctx)` and `onDeactivate(ctx)` run on activation and deactivation.
- `actions()`, `filters()`, `endpoints()` and `webhooks()` add hooks and routes.
- A slug is the manifest name with a leading `@` removed and `/` as `--`
  (`acme/billing` is `acme--billing`). Two plugins with one slug, a route that is
  not `METHOD /path`, or a manifest without a name fail at startup, naming the
  plugin.

A native plugin runs with the host's privileges. Register only code you would
merge into the host itself.

## SDK plugins

Off by default. Install Node, the runtime and the plugins in a project
directory, then:

```properties
escalated.plugins.runtime.enabled=true
# The directory holding node_modules/@escalated-dev/plugin-runtime and the plugins
escalated.plugins.runtime.directory=/srv/helpdesk-plugins
# Optional: the executable (default node), its arguments (default
# node_modules/@escalated-dev/plugin-runtime/build/bin/escalated-plugins.js)
# and extra environment variables
escalated.plugins.runtime.command=node
escalated.plugins.runtime.env[NODE_ENV]=production
```

The runtime is started on first use (the first page, event or route that needs
it). It is spoken to as JSON-RPC 2.0 over stdin and stdout, one message per
line, at most 10 MB; its stderr goes to the `dev.escalated.plugins.runtime` log.
The host handshakes (protocol 1.0) and refuses a runtime that is not compatible;
endpoints and webhooks need HTTP contract version 1.

When the runtime cannot start or dies, the host carries on: actions are dropped
with a warning, filters return their input, and SDK endpoints and webhooks answer
503. It is restarted on next use with exponential backoff, capped at five
minutes.

SDK plugins are listed as `composer` (package-managed), so the admin screen does
not offer to delete them: remove them with npm. The runtime loads every
installed plugin; the host sends it nothing for an inactive one, and passes
`plugins: [active names]` on every `action` and `filter`.

## Admin screen and pages

Behind `escalated.ui.enabled=true` and the admin gate:

| Method | Path | Does |
|---|---|---|
| GET | `/escalated/admin/plugins` | `Escalated/Admin/Plugins/Index`, with `plugins` and `can_upload: false` |
| POST | `/escalated/admin/plugins/{slug}/activate` | Activate |
| POST | `/escalated/admin/plugins/{slug}/deactivate` | Deactivate |
| DELETE | `/escalated/admin/plugins/{slug}` | Uninstall |
| GET | `/escalated/admin/plugins/{slug}/{route}` | An active plugin's page, `Escalated/Plugin/Page` |

The lifecycle routes answer 303 to the index with a `success` or `error` flash;
an unknown slug is an error flash, or a 404 for a JSON request. There is no
upload route: plugins are installed by whoever deploys the host.

- **Activate**: the row (`is_active`, `activated_at`), then `onActivate` (the
  runtime's `activate`), then `plugin.activated`.
- **Deactivate**: `plugin.deactivated` while the plugin is still active, then
  `onDeactivate` (the runtime's `deactivate`), then the row.
- **Uninstall**: `plugin.uninstalling`, deactivate, and delete the row. The
  plugin stays listed, inactive, because its code is still in the build. Its
  stored data is kept for a reinstall.

Lifecycle actions go to every active plugin, the subject included. A callback
that throws is logged and undoes nothing. Each transition is idempotent.

A page renders `Escalated/Plugin/Page` with `plugin` (the name), `component`,
`layout` (default `admin`) and `props`: the result of the plugin's own
`GET /{component}` endpoint, or `{}` when it has none or it fails.

Every page also gets `escalated.plugins`: active plugins' `menuItems`,
`pageComponents` and `dashboardWidgets`, without items whose capability the
viewer lacks and without `admin` menu items for anyone but an admin. It is `{}`
when no plugin is active. Plugin config is never shared. An SDK plugin's widget
with a `badge` function also carries `badge`: the runtime is asked for it while
the page is built, as the viewer, within 5 s; `null` when that fails.

## Cron

An SDK plugin's `cron` keys are five-field cron expressions (`minute hour
day-of-month month day-of-week`, or `@hourly`, `@daily`, ...), evaluated in
UTC. At the start of every minute `PluginCronScheduler` (Spring scheduling,
which the auto-configuration enables) sends each due schedule of each active
SDK plugin to the runtime as `cron`, side by side, as no user, within 30 s. A
failing or unreadable schedule is logged and skipped; inactive plugins'
schedules never run. Native plugins schedule their own work.

## Endpoints and webhooks

| Kind | Route | Guard |
|---|---|---|
| Endpoint | `/escalated/api/plugins/{slug}{path}` | Admin (session or API token), plus the endpoint's capability (403) |
| Webhook | `/escalated/webhooks/plugins/{slug}{path}` | None: the plugin authenticates the sender |

- Only active plugins answer; anything else is a 404.
- Paths may hold `:param` segments; the handler is dispatched on the declared
  path and receives the values as `params`.
- Handlers receive the parsed body (JSON or form fields), the exact bytes, the
  query and the lower-case headers, never `Cookie`, `Authorization`,
  `X-XSRF-TOKEN`, `X-CSRF-TOKEN` or `Proxy-*`. Bodies are capped at 3 MB (413).
- An endpoint call that changes state (anything but GET, HEAD and OPTIONS) must
  carry `X-Requested-With` or `X-Inertia`, which a plugin page script sends and
  another site cannot without a CORS preflight. Endpoints take no CSRF token.
- A return value is JSON with 200. A `PluginResponse` (native) or an
  `httpResponse()` envelope (SDK) chooses the status, headers and format;
  transport headers and `Set-Cookie` are refused with 502. A handler that throws
  is a 500 with a generic message; the error goes to the log.
- Timeouts: endpoints 30 s, webhooks 60 s.

## Events, filters and ctx

**Actions.** The canonical events of the 2026-10-21 vocabulary reach active
plugins' actions after the change has committed, on another thread, from the
same events that fire webhooks and workflows. Legacy names are never delivered.
Payloads:

| Events | Payload |
|---|---|
| `ticket.*` | The ticket, flat, plus `ticket`. `ticket.status_changed` adds `old_status`, `new_status`; `ticket.assigned` adds `agent_id`; `ticket.priority_changed` adds `old_priority`, `new_priority`; `ticket.department_changed` adds `old_department_id`, `new_department_id`; `ticket.tag_added`/`ticket.tag_removed` add `tag` (`{id, name, slug}`); `ticket.escalated` adds `reason` |
| `reply.created`, `note.created` | The reply, flat, plus `reply` and `ticket` |
| `sla.warning`, `sla.breached` | The ticket, flat, plus `ticket` and `type` |
| `chat.*` | `{session}`: `session_id`, `ticket_id`, `status`, `agent_id`, `department_id` (and the transfer's ids for `chat.transferred`), never the visitor's token or email |

The ticket object: `id`, `reference`, `subject`, `title` (same as subject),
`description`, `status` and `priority` (lower-case), `custom_status`,
`ticket_type`, `channel`, `department_id`, `assigned_to` (agent profile id),
`requester_id` (the contact's host user id, or null), `contact_id`,
`requester_name`, `metadata`, `tags`, `created_at`, `updated_at`.

A slow or failing action never reaches the request (30 s timeout). `ctx.emit`
fires an action of the plugin's choosing to every active plugin; names that
belong to the host (the canonical events, their legacy names, the other ticket
event names and the lifecycle actions) are dropped.

**Filters.** `PluginRegistry.applyFilter(hook, value)` runs active plugins'
filters in ascending priority (default 10). One that throws or takes longer than
5 s is skipped. The runtime applies its plugins' filters in one call, placed at
the lowest SDK priority for the hook. This package applies no filter hook of its
own yet; host code may call `applyFilter` for its own hooks.

**ctx.** Native plugins receive a `PluginContext`; the bridge answers the
runtime's `ctx.*` calls through the same code. Everything is scoped to the
plugin being dispatched, and the bridge refuses a call that names a plugin it is
not serving.

| Call | Here |
|---|---|
| `config.all/get/set` | The `__config__` row; `set` merges |
| `store.get/set/query/insert/update/delete` | `escalated_plugin_store`; `query` filters in Java (equality, array contains, `$gt $gte $lt $lte $ne $in $nin`, dotted fields, `limit`/`orderBy`/`order`), so a field name is never SQL |
| `tickets.find/query/create/update` | Through `TicketService`, so events fire. Only the contract's keys; any other is an error. `query` returns at most 500 rows. `create` needs `contact_id` or `requester_email`; `channel` defaults to `api` |
| `replies.find/query/create` | Through `TicketService`. `query` filters on `ticket_id`, `is_internal`, `type` (`reply` or `note`) |
| `contacts.find/findByEmail/create` | Escalated's contacts; `create` finds or creates by email |
| `tags.all/create`, `departments.all/find`, `agents.all/find` | `agents` are active agent and admin profiles, `{id, name, email}`; tag and department slugs are derived from the name |
| `broadcast.toChannel/toUser/toTicket` | `/topic/{channel}`, `/topic/users/{id}`, `/topic/tickets/{id}` on the host's STOMP broker, as `{event, data}`; a no-op without one. While ticket visibility is restricted, only `toTicket` is delivered, through the host's `AuthorizedTicketBroadcaster` |
| `log`, `emit`, `currentUser` | The `dev.escalated.plugins` log with the plugin's name; see above; `{id, name, email}` of the signed-in user or null |

Writes act as the signed-in user of the request the plugin is serving (a page,
an endpoint, a lifecycle route), or as no user for domain events and webhooks.
A reply written as no user is authored by the plugin, with `author_type`
`system`.

Host code can reach a plugin's context, active or not, with
`PluginRegistry.contextFor(slug)`, for instance to seed its config.

## Capabilities

A page, endpoint, menu item, slot component or widget may declare a
`capability`. By default admins hold every capability and anyone else holds the
permission names of their agent role. A host replaces the rule with a bean:

```java
@Bean
PluginCapabilities pluginCapabilities() {
    return (user, capability) -> user.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("PLUGIN_" + capability));
}
```

## Storage

Flyway migration `V24` (PostgreSQL and MySQL editions) adds:

- `escalated_plugins`: `slug` (unique), `is_active`, `activated_at`,
  `deactivated_at`, timestamps. A registered plugin with no row is inactive.
- `escalated_plugin_store`: `plugin` (the name, 191), `collection` (100), `key`
  (191, nullable), `data` (JSON text), timestamps, indexed on
  `(plugin, collection, key)`.

On H2, or with `escalated.datasource.migrate=false`, Hibernate's schema includes
both tables from the entities `PluginActivation` and `PluginStoreRecord`.

## Differences from the contract

- Plugin pages share one handler and have no per-page route name
  (`escalated.plugin.{slug}.page.{route}`): SDK pages are only known once the
  runtime has started. Nothing calls those names; menu items carry the URL.
- There is no tenant mode in this package, so the contract's tenant rules do not
  apply.
- `ctx.tickets.query` on `requester_id` matches the contact's host user id.
- The JSON-RPC calls carry no request id, so while the runtime serves one SDK
  plugin for two dispatches at once with different users, that plugin's ctx
  calls act as no user.
