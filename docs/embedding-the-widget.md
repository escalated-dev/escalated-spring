# Embedding the widget on other sites

The support widget, ticket form and live chat can run on a site other than
the one Escalated is installed on: a store, a marketing site or a customer
portal loads one script from your Escalated install. Nothing changes until you
list the sites that may do this.

## 1. Allow the embedding sites

Either in the host's configuration:

```properties
escalated.widget.allowed-origins=https://shop.example.com,https://*.example.org
```

(or the environment variable `ESCALATED_WIDGET_ALLOWED_ORIGINS`, comma
separated, through Spring Boot's relaxed binding), or in the
`widget_allowed_origins` admin setting, one origin per line or comma separated:

```http
POST /escalated/api/admin/settings
Content-Type: application/json

{"key": "widget_allowed_origins", "value": "https://shop.example.com\nhttps://*.example.org", "group": "widget"}
```

Both lists are combined. Each entry is an origin: `scheme://host[:port]` over
http or https, no path, query or user info; a trailing `/` is allowed.
`https://*.example.org` matches every subdomain of `example.org` at any depth
but not `example.org` itself, and only over `https`. A bare `*` is rejected.
The settings endpoint answers 422 and saves nothing if any entry is not an
origin; invalid entries in `escalated.widget.allowed-origins` are ignored.

## 2. Add the script to the other site

```html
<script src="https://help.example.com/escalated/api/widget/embed.js" async></script>
```

The script finds its backend and widget path (`/escalated/api/widget`) from
its own URL. For an on-page support form instead of a floating button:

```html
<div id="support-form"></div>
<script
    src="https://help.example.com/escalated/api/widget/embed.js"
    data-mode="inline"
    data-target="#support-form"
    data-tabs="ticket"
    data-meta-order-number="A-1001"
></script>
```

All host parameters (tabs, department, prefill, metadata, title, the
JavaScript API) are described in the frontend package's
[embedding guide](https://github.com/escalated-dev/escalated/blob/main/docs/embedding-the-widget.md).

## What the server does

`WidgetCorsFilter` runs on `/escalated/api/widget/**` (chat included), ahead
of Spring Security. For a request whose `Origin` is on the list it:

- answers preflight requests (`OPTIONS` with `Access-Control-Request-Method`)
  with 204, `Access-Control-Allow-Origin: <origin>`,
  `Access-Control-Allow-Methods: GET, POST, OPTIONS`,
  `Access-Control-Allow-Headers: Accept, Authorization, Content-Type, X-Requested-With`
  and `Access-Control-Max-Age: 600`;
- adds `Access-Control-Allow-Origin`, `Access-Control-Expose-Headers: Retry-After`
  and `Vary: Origin` to every response, including 401 from the security
  chain, 403, 422 and the guest rate limit's 429;
- treats the visitor as a guest even if the same browser is signed in to the
  host: the request's cookies, session and principal are hidden from
  everything after the filter, and any security context set before it is
  cleared for the request. Guest tokens in the path still work.

`Access-Control-Allow-Credentials` is never sent. A preflight from any other
origin gets a 204 with no grant; other requests from unlisted origins, and
same-origin requests, are handled exactly as before. The widget routes were
already exempt from CSRF and stateless, and stay that way. Guest rate limits
apply to embedded requests unchanged.

The same-origin check compares `Origin` with the request's scheme, host and
port. Behind a reverse proxy, set `server.forward-headers-strategy` so those
are the public ones.

The filter is deliberately not Spring's `CorsConfiguration`: the host's global
CORS mappings, and any `allowCredentials` they set, never apply to these
routes, and the grant is on the response before the host's mappings could
add their own.

### `/config`

`GET /escalated/api/widget/config` returns what the widget needs before it
renders:

```json
{
  "enabled": true,
  "color": "#4F46E5",
  "position": "bottom-right",
  "greeting": "Hi there! How can we help?",
  "departments": [{"id": 1, "name": "Support"}],
  "kb_enabled": false,
  "guest_tickets_enabled": true,
  "accepts_metadata": true,
  "status_lookup_enabled": false
}
```

Colour, position and greeting come from the `widget_color`,
`widget_position` and `widget_greeting` settings; `departments` lists active
departments; `kb_enabled` is `escalated.knowledge-base.enabled`. With
`escalated.widget.enabled=false` it answers 403 with `{"enabled": false}`.
`status_lookup_enabled` is `false` because this backend has no mailbox
verification, so the widget hides its Status tab; there is no lookup of a
ticket by email address.

### Widget routes

All under `/escalated/api/widget`. These are the requests the shared widget
sends.

| Route | Request | Answer |
|---|---|---|
| `GET /articles?q=` | `q`, 2 characters or more | `{"articles": [{title, slug, excerpt}]}`, published articles only |
| `GET /articles/{slug}` | | `{title, slug, body, excerpt, category}`; drafts and unknown slugs 404 |
| `POST /tickets` | `name`, `email`, `subject`, `description` (or `body`), `department_id`, `metadata` | 201: the ticket's fields plus `message`, `reference` (the ticket number) and `guest_access_token`. Missing `subject`, `email` or `description` is a 422 `{message, errors}` |
| `POST /chat/start` | `name`, `email`, `message`, `department_id`, `metadata` | 201 `{id, status, messages, agent, ticketId, ...}` |
| `GET /chat/{id}/messages` | | `{messages: [{id, body, is_agent, author: {name}, created_at}], typing, agent, ended}` (internal notes left out) |
| `POST /chat/{id}/messages` | `{body}` | 201; filed as the visitor, whatever name the body carries |
| `POST /chat/{id}/typing`, `POST /chat/{id}/end` | | 200 |
| `POST /chat/{id}/rate` | `{rating: 1-5, comment}` | 200; stored as the chat ticket's satisfaction rating |

A chat's `id` is a random 256-bit token returned once by `/chat/start`. Only
its SHA-256 hash is stored (`escalated_chat_sessions.widget_token_hash`,
migration `V14`). The numeric session id is not accepted by any widget route:
an unknown token, a number included, is a 404. An unknown or inactive
`department_id` is ignored. `typing` is always `null` for now: agents have no
typing signal in this backend.

### Host parameters

`POST /escalated/api/widget/tickets` and `POST /escalated/api/widget/chat/start`
accept an optional `metadata` object, stored on the ticket (for a chat, the
chat's ticket) in `escalated_tickets.metadata` (JSON text, added by migration
`V13`) and exposed as `Ticket#getMetadata()`. The values come from the
visitor's browser, so only a flat map is kept: up to 25 entries, keys of
letters, digits, `_ . : -` (64 characters at most), and string, number or
boolean values (strings trimmed and cut to 500 characters). `page_url` is kept
only when it is an http(s) URL. `embedded_on` is always set by the server from
the allowed request's `Origin` and cannot be supplied by the page. A
`metadata` that is not an object, or has more than 50 keys, is answered with
422. Treat all of it as unverified visitor input.

### The script

`GET /escalated/api/widget/embed.js` serves the bundle vendored at
`src/main/resources/META-INF/escalated/widget/escalated-widget.js`, from
`@escalated-dev/escalated` 0.12.1 (`dist/widget/escalated-widget.js`), with
`Access-Control-Allow-Origin: *`, `Cross-Origin-Resource-Policy: cross-origin`,
a one-hour cache and an ETag (304 on `If-None-Match`).

## Troubleshooting

- **The browser reports a CORS error.** The page's origin is not on the list,
  or it differs in scheme or port (`http://` vs `https://`, `:8080`).
- **Everything is treated as cross-origin, or nothing is.** Behind a proxy,
  the request's own scheme and host are the proxy's unless
  `server.forward-headers-strategy` is set.
- **A host `WebMvcConfigurer` CORS mapping for `/**`.** It is not needed for
  the widget routes. The filter answers their preflights before it, and for
  listed origins Spring leaves the filter's grant in place.
