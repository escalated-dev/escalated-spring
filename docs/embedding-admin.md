# Embedding the admin in your own admin panel (optional)

Escalated ships a complete standalone admin at `/escalated/admin`, with its own
sidebar and top bar (the Inertia pages behind `escalated.ui.enabled`; see
[ui.md](ui.md)). **That is the default, and it needs no setup.** If your Spring
app already has an admin panel, you can instead show Escalated's admin screens
inside it: under your sidebar and header, your URL prefix and your security.

This is an optional alternative to the standalone admin. Nothing on this page
has any effect until you turn it on: with no embed configuration every route,
filter, security chain, gate, page, prop and header is exactly what it was
before (a test compares the live route table with a snapshot taken before the
feature existed, `src/test/resources/embed/default-routes.json`). The design is
in the ADR
[`2026-11-05-embeddable-admin`](https://github.com/escalated-dev/escalated-developer-context/blob/main/decisions/2026-11-05-embeddable-admin.md);
the frontend side is documented in
[`@escalated-dev/escalated`](https://github.com/escalated-dev/escalated/blob/main/README.md).

## Contents

- [Pick your setup](#pick-your-setup)
- [Turning it on](#turning-it-on)
- [Configuration reference](#configuration-reference)
- [Your security chain and middleware](#your-security-chain-and-middleware)
- [What changes, and what does not](#what-changes-and-what-does-not)
- [1. Vue + Inertia host](#1-vue--inertia-host)
- [2. Server-rendered host (Thymeleaf)](#2-server-rendered-host-thymeleaf)
- [3. iframe](#3-iframe)
- [The admin menu: `escalated.navigation`](#the-admin-menu-escalatednavigation)
- [Security](#security)
- [What does not move](#what-does-not-move)
- [Turning it off](#turning-it-off)

## Pick your setup

| Your admin panel | Use | `mode` |
|---|---|---|
| Vue + Inertia in this app | [Vue + Inertia host](#1-vue--inertia-host) | `layout` |
| Server-rendered templates (Thymeleaf) | [Server-rendered host](#2-server-rendered-host-thymeleaf) (your template as the root view) | `layout` |
| Another app or origin, a non-Vue SPA, a CMS admin | [iframe](#3-iframe) | `iframe` |

## Turning it on

In `application.properties` (the UI has to be on):

```properties
escalated.ui.enabled=true
escalated.ui.embed.enabled=true
escalated.ui.embed.prefix=admin/support
escalated.ui.embed.middleware=hostAdminOnly
escalated.ui.embed.layout=Layouts/AdminLayout
```

Or from Java, with an `EscalatedEmbedConfigurer` bean (the `embedAdmin(...)`
call of the other Escalated hosts; equivalent to the properties, applied over
them when the context starts):

```java
import dev.escalated.ui.EscalatedEmbedConfigurer;

@Bean
EscalatedEmbedConfigurer escalatedEmbed() {
    return embed -> embed.embedAdmin()
            .prefix("admin/support")
            .middleware("hostAdminOnly")
            .layout("Layouts/AdminLayout");
}
```

Or from the environment, with no code change: `ESCALATED_EMBED=true`,
`ESCALATED_EMBED_MODE`, `ESCALATED_EMBED_PREFIX` and
`ESCALATED_EMBED_FRAME_ANCESTORS` (comma-separated). Any other setting of the
same keys (properties, YAML, `ESCALATED_UI_EMBED_*`) wins over these.

## Configuration reference

All under `escalated.ui.embed`. **Default: everything off.**

| Property | Configurer | Env var | Default | Meaning |
|---|---|---|---|---|
| `enabled` | `embedAdmin()` | `ESCALATED_EMBED` | `false` | Master switch. While `false`, nothing below has any effect. |
| `mode` | `.mode(...)` | `ESCALATED_EMBED_MODE` | `layout` | `layout` (Vue + Inertia or server-rendered host) or `iframe`. |
| `prefix` | `.prefix(...)` | `ESCALATED_EMBED_PREFIX` | `admin/support` | URL path the admin moves under, instead of `/escalated/admin`. |
| `middleware` | `.middleware(...)` | | none | Names of `jakarta.servlet.Filter` beans to run on embedded admin requests, in order, after your security chain and before Escalated's guards. See [below](#your-security-chain-and-middleware). |
| `replace-admin-gate` | `.replaceAdminGate(true)` | | `false` | `true` drops Escalated's admin gate from the embedded admin, so your security chain and middleware alone decide. See [Security](#security). |
| `layout` | `.layout(...)` | | none | Name of your Vue admin layout, matched against the frontend plugin's `layouts` option. |
| `root-view` | `.rootView(...)` | | none | Thymeleaf view for embedded pages' first visits ([server-rendered host](#2-server-rendered-host-thymeleaf)). |
| `frame-ancestors` | `.frameAncestors(...)` | `ESCALATED_EMBED_FRAME_ANCESTORS` | `[]` | iframe mode: exact origins allowed to frame the admin. `*`, wildcards, paths and anything that is not an http(s) origin are dropped. |

`enabled` and `prefix` are read when the application starts; the others on
every request.

## Your security chain and middleware

The embedded admin is a URL of your app (`/admin/support/...`), so your own
`SecurityFilterChain` for it runs first, on the request as the browser sent
it: that is where your sign-in, roles and session handling apply. Then the
filters named in `middleware` run, in order; any of them can answer the
request itself (a 403, a redirect). Only then does Escalated take over:
the request is seen at its standalone path (`/admin/support/tickets` as
`/escalated/admin/tickets`) and passes through Escalated's own security chain
(CSRF, the admin gate, two-factor, route permissions) to the same handler as
the standalone admin.

A host admin panel at `/admin`, as the tests set it up
(`src/test/java/embedhost/EmbedHost.java`):

```java
@Bean
@Order(5)
SecurityFilterChain hostAdminSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/admin/**")
            // Escalated's chain checks CSRF on the embedded pages, with the token its
            // pages send (the XSRF-TOKEN cookie echoed as X-XSRF-TOKEN).
            .csrf(csrf -> csrf.ignoringRequestMatchers("/admin/support/**"))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
    return http.build();
}

/** Your own check on who may use your admin panel. */
@Bean
Filter hostAdminOnly() {
    return (request, response, chain) -> {
        Authentication user = SecurityContextHolder.getContext().getAuthentication();
        if (user == null || !isHostAdmin(user)) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    };
}

/** Boot registers every Filter bean on every URL; this one runs only where Escalated runs it. */
@Bean
FilterRegistrationBean<Filter> hostAdminOnlyRegistration(Filter hostAdminOnly) {
    FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(hostAdminOnly);
    registration.setEnabled(false);
    return registration;
}
```

Leave CSRF on the embed prefix to Escalated's chain, as above: a chain with its
own CSRF token store would refuse the token Escalated's pages send. Escalated's
chain still checks every write there. Your chain and Escalated's read the same
`SecurityContext` from the session, so a user signed in to your app is signed
in to the embedded admin; the principal's name is the user's email address, as
everywhere else in this package.

## What changes, and what does not

While embedding is on:

- Every admin page that lived under `/escalated/admin` (the admin screens,
  the plugin admin and plugin pages, API tokens, the import wizard, reports,
  settings) answers under your `prefix` instead, behind your security chain
  and `middleware`, followed by Escalated's own guards: CSRF, the admin gate
  (unless you replace it), two-factor and route permissions, then the same
  handler. The old `/escalated/admin/...` URLs answer 404.
- **Route names do not change.** The `route()` table the pages read
  (`UiRoutes`) points the `escalated.admin.*` names at the new URLs, so the
  shared frontend's links follow. Redirects the admin answers with (after a
  save, a plugin action) go to the new URLs too, and Inertia's `url` keeps the
  embedded URL.
- Embedded pages carry `escalated.embed`, and every Escalated page carries
  `escalated.navigation`, the admin menu for your sidebar.

`escalated.embed` on an embedded page:

```json
{
  "enabled": true,
  "mode": "layout",
  "layout": "Layouts/AdminLayout",
  "route": "escalated.admin.tickets.index",
  "base_url": "/admin/support",
  "allowed_origins": []
}
```

On every other page `enabled` is `false` and `route` is `null`. `route` is the
`route()` name of the current URL (this port's controllers have no route names
of their own).

## 1. Vue + Inertia host

Backend: turn embedding on (above) with your prefix, your middleware and the
name of your admin layout.

Frontend: register your admin layout under the name you gave `layout`:

```js
import { EscalatedPlugin } from '@escalated-dev/escalated'
import AdminLayout from '@/Layouts/AdminLayout.vue'
import AppLayout from '@/Layouts/AppLayout.vue'

// In createInertiaApp's setup(), with the page resolver from the frontend README:
app.use(EscalatedPlugin, {
    layout: AppLayout,                                 // customer pages, as before
    layouts: { 'Layouts/AdminLayout': AdminLayout },   // embedded admin pages
    theme: { panel: { mode: 'light' } },               // match your admin
})
```

To use one layout for customer and admin pages, pass
`layout: AdminLayout, layoutScope: ['customer', 'admin']` instead of
`layouts`.

Your admin layout needs a `#header` slot (Escalated puts the page title
there) and a default slot. List Escalated's menu with `<EscalatedNavigation />`:

```vue
<!-- Layouts/AdminLayout.vue -->
<script setup>
import { EscalatedNavigation } from '@escalated-dev/escalated'
</script>

<template>
    <div class="flex min-h-screen">
        <aside class="w-64 border-r bg-white p-3">
            <a href="/admin">Dashboard</a>
            <!-- ...your own items... -->
            <p class="mt-4 px-3 text-xs font-semibold uppercase text-gray-500">Support</p>
            <EscalatedNavigation />
        </aside>
        <div class="flex-1">
            <header class="border-b bg-white px-6 py-4"><slot name="header" /></header>
            <main class="p-6"><slot /></main>
        </div>
    </div>
</template>
```

Escalated renders each admin page chrome-free (no Escalated sidebar, top bar,
logo or footer) inside your layout. If your own Inertia pages are rendered with
Escalated's `dev.escalated.ui.Inertia` bean, they get `escalated.navigation`
too; otherwise share `AdminNavigation#adminNavigation(request)` (below) from
your own Inertia adapter.

## 2. Server-rendered host (Thymeleaf)

If your admin layout is a Thymeleaf template, make it the root view of the
embedded pages: a template with your chrome and the Inertia mount point.

```properties
escalated.ui.embed.enabled=true
escalated.ui.embed.prefix=admin/support
escalated.ui.embed.middleware=hostAdminOnly
escalated.ui.embed.root-view=host/admin
```

The view gets what `escalated.ui.root-view` gets ([ui.md](ui.md)): `page`,
`pageJson`, `routesScript` and `csrfToken`, plus `escalatedNavigation`, the
admin menu (a list of maps, shape [below](#the-admin-menu-escalatednavigation))
for the request. This is the template the tests render
(`src/test/resources/templates/host/admin.html`):

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" th:if="${csrfToken}" th:content="${csrfToken}">
    <title>Host admin</title>
    <script th:utext="${routesScript}"></script>
    <script type="module" src="/build/app.js"></script>
</head>
<body>
<div class="host-admin">
    <aside class="host-sidebar">
        <a href="/admin">Dashboard</a>
        <a href="/admin/orders">Orders</a>
        <nav aria-label="Support">
            <a th:each="item : ${escalatedNavigation}"
               th:href="${item.url}"
               th:text="${item.label}"
               th:attr="aria-current=${item.active} ? 'page' : null"></a>
        </nav>
    </aside>
    <main class="host-content">
        <div id="app" th:attr="data-page=${pageJson}"></div>
    </main>
</div>
</body>
</html>
```

Only embedded admin pages use this root view; every other Escalated page keeps
the built-in document (or `escalated.ui.root-view`). Your Inertia entry needs
no extra option: Escalated sees `escalated.embed.enabled` and renders the page
chrome-free (title + content) with no host layout.

For the menu on your other admin pages, inject the `AdminNavigation` bean:

```java
@Controller
class HostAdminController {

    private final AdminNavigation navigation;

    HostAdminController(AdminNavigation navigation) {
        this.navigation = navigation;
    }

    @GetMapping("/admin/orders")
    String orders(HttpServletRequest request, Model model) {
        model.addAttribute("escalatedNavigation", navigation.adminNavigation(request));
        return "host/orders";
    }
}
```

`adminNavigation(request)` returns the list for the signed-in user (or
`adminNavigation(authentication, request)` for another one), whether or not
embedding is on: the URLs follow wherever the admin is.

## 3. iframe

For an admin that is not part of this app (another origin, another stack),
frame the embedded admin:

```properties
escalated.ui.embed.enabled=true
escalated.ui.embed.prefix=admin/support
escalated.ui.embed.middleware=hostAdminOnly
escalated.ui.embed.mode=iframe
escalated.ui.embed.frame-ancestors=https://admin.example.com
```

or `ESCALATED_EMBED=true`, `ESCALATED_EMBED_MODE=iframe`,
`ESCALATED_EMBED_FRAME_ANCESTORS=https://admin.example.com`.

Embedded responses then carry
`Content-Security-Policy: frame-ancestors 'self' https://admin.example.com`
and no `X-Frame-Options` (the allow-listed `frame-ancestors` replaces it on
those responses only). A servlet filter ahead of every other filter
(Spring Security's included) owns those two headers on embedded responses, so
the `X-Frame-Options: DENY` Spring Security writes, or one your own filters
add, cannot undo them. A CSP you already send is kept and this directive goes
out as a second policy; if your own policy has a `frame-ancestors` directive,
list the same origins there too, since browsers enforce both. No other route's
headers change.

On the framing page:

```html
<iframe id="support-admin" src="https://support.example.com/admin/support/tickets"
        style="width:100%;border:0" title="Support administration"></iframe>
<script type="module">
    import { connectEscalatedFrame } from '@escalated-dev/escalated/embed'

    const frame = connectEscalatedFrame(document.getElementById('support-admin'), {
        origin: 'https://support.example.com',
        onNavigate: ({ route, title }) => highlightMenu(route),
    })
    frame.setTheme('light')
</script>
```

The frame sizes itself to its content and reports each page change; the
protocol is in the frontend guide. It posts only to an allow-listed parent
origin and ignores messages from anywhere else.

### Session cookies in a frame

- **Same site** (`admin.example.com` framing `support.example.com`): the
  default session cookie works.
- **Cross-site** (different registrable domains): the session cookie must be
  sent in a third-party context, so serve both sites over HTTPS and set

  ```properties
  server.servlet.session.cookie.same-site=none
  server.servlet.session.cookie.secure=true
  ```

  In iframe mode Escalated's `XSRF-TOKEN` cookie goes out
  `SameSite=None; Secure` by itself. Browsers that block third-party cookies
  will not keep a session in a cross-site frame at all; use the Vue + Inertia
  or server-rendered setup there.
- CSRF protection is unchanged.

## The admin menu: `escalated.navigation`

While embedding is on, every Escalated page shares the admin menu, and
`AdminNavigation#adminNavigation` returns the same list:

```json
[
  {
    "key": "tickets",
    "label": "Tickets",
    "route": "escalated.admin.tickets.index",
    "url": "/admin/support/tickets",
    "icon": "tickets",
    "position": 20,
    "active": true,
    "badge": null,
    "plugin": null,
    "children": []
  }
]
```

It is filtered for the user, so a sidebar never shows a link that would 403
or 404:

- empty for anyone who fails Escalated's admin gate (with
  `replace-admin-gate`, for anyone not signed in);
- items whose page this host does not serve are left out (Newsletters: this
  package's newsletter admin is a JSON API, with no page to link);
- Roles only while roles & permissions is on;
- while roles & permissions is on, each item needs every permission its page
  requires (`RoutePermissions`); a user without `settings.view` gets no
  Settings;
- active plugins' admin menu items are included (`key` `plugin:<slug>:<n>`,
  `route` `null`), with the capability filtering the plugin contract applies;
- sorted by `position`, the standalone sidebar's order. `active` is true on
  the item's URL and below it. Core `icon` keys match the frontend's
  `escalatedNavIcons`.

## Security

- **The admin gate stays on by default.** Your security chain and
  `middleware` run *in addition to* Escalated's admin gate. Set
  `replace-admin-gate` only if they themselves restrict the prefix to admins:
  with it on, they are the only check on who reaches the admin (Escalated
  then only requires a signed-in user). Two-factor and route permissions
  still apply either way, and the agent panel and the shared `is_admin` keep
  Escalated's gates.
- **CSRF is still checked** on every embedded write, by Escalated's chain.
- **Framing is allow-listed.** In iframe mode only the origins you list (plus
  your own) may frame the admin; nothing else, and never `*`.
- **No open `postMessage`.** The frame talks only to an allow-listed parent
  origin.

## What does not move

The agent panel (`/escalated/agent`), customer portal, guest pages, knowledge
base, widget, the JSON API (`/escalated/api/**`, including the admin API and
the newsletter admin), SSO, and the two-factor enrolment pages
(`/escalated/admin/settings/two-factor`) and challenge keep their URLs.

## Turning it off

Remove the `escalated.ui.embed.*` settings, the configurer bean and
`ESCALATED_EMBED`. The admin returns to `/escalated/admin` with Escalated's own
chrome.
