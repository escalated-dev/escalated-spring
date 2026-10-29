<p align="center">
  <a href="docs/translations/README.ar.md">العربية</a> •
  <a href="docs/translations/README.de.md">Deutsch</a> •
  <b>English</b> •
  <a href="docs/translations/README.es.md">Español</a> •
  <a href="docs/translations/README.fr.md">Français</a> •
  <a href="docs/translations/README.it.md">Italiano</a> •
  <a href="docs/translations/README.ja.md">日本語</a> •
  <a href="docs/translations/README.ko.md">한국어</a> •
  <a href="docs/translations/README.nl.md">Nederlands</a> •
  <a href="docs/translations/README.pl.md">Polski</a> •
  <a href="docs/translations/README.pt-BR.md">Português (BR)</a> •
  <a href="docs/translations/README.ru.md">Русский</a> •
  <a href="docs/translations/README.tr.md">Türkçe</a> •
  <a href="docs/translations/README.zh-CN.md">简体中文</a>
</p>

# Escalated Spring

[![Views](https://hits.sh/github.com/escalated-dev/escalated-spring.svg?style=flat&label=views&color=007ec6)](https://hits.sh/github.com/escalated-dev/escalated-spring/)

An embeddable helpdesk system for Spring Boot applications. Add a full-featured support desk to any Java application with a single dependency.

For opt-in department/account visibility and targeted SLAs, see [ticket access and routing](docs/ticket-access-and-routing.md).

To run the support widget on other sites (a store, a marketing site), see [embedding the widget](docs/embedding-the-widget.md).

To extend the helpdesk with plugins (Java beans, or `@escalated-dev/plugin-sdk` plugins through the Node plugin runtime), see [plugins](docs/plugins.md).

To serve the shared agent, customer and admin screens (`@escalated-dev/escalated`, Vue 3 + Inertia), set `escalated.ui.enabled=true`; see [the Inertia pages](docs/ui.md). It is off by default, so an API-only host is unchanged.

## Features

1. **Ticket CRUD** -- Full lifecycle management with statuses, priorities, and assignments
2. **SLA Policies** -- Configurable SLAs with business hours support and holiday calendars
3. **Automations** -- Time-based rules for auto-closing resolved tickets and auto-assignment
4. **Escalation Rules** -- Condition-based escalation (SLA breach, age, no response) with reassignment, priority and department changes
5. **Macros & Canned Responses** -- Pre-defined actions and response templates for agents
6. **Custom Fields** -- Extensible ticket data with multiple field types
7. **Knowledge Base** -- Articles and categories with search, view counts, and feedback
8. **Webhooks** -- HMAC-signed webhook delivery with retry logic
9. **API Tokens** -- SHA-256 hashed token authentication for API access
10. **Roles & Permissions** -- Granular role-based access control
11. **Audit Logging** -- Complete audit trail for all actions
12. **Import System** -- Bulk ticket import from structured data
13. **Side Conversations** -- Private threaded conversations within tickets
14. **Ticket Merging & Linking** -- Merge duplicate tickets and link related ones
15. **Ticket Splitting** -- Split complex tickets into separate issues
16. **Ticket Snooze** -- Snooze tickets with automatic wake-up via `@Scheduled`
17. **Email Threading** -- Branded HTML email templates via Thymeleaf with proper Message-ID threading
18. **Saved Views** -- Custom filtered/sorted ticket views per agent
19. **Widget API** -- Public REST endpoints for embedding a support widget
20. **Real-time Broadcasting** -- WebSocket via STOMP/SockJS (opt-in)
21. **Capacity Management** -- Track and enforce agent workload limits
22. **Skill-based Routing** -- Route tickets to agents with matching skills
23. **CSAT Ratings** -- Customer satisfaction surveys with token-based access
24. **2FA (TOTP)** -- Time-based one-time password support for agent accounts
25. **Guest Access** -- Token-based ticket access without authentication
26. **Inbound Email** -- Single webhook endpoint with Postmark + Mailgun + AWS SES parsers, signed Reply-To ticket resolution, and replies accepted only from the ticket's requester
27. **Plugins** -- Native Java plugins and SDK plugins (through the Node plugin runtime), with Admin > Plugins, plugin pages, endpoints, webhooks and domain events ([docs](docs/plugins.md))

## Requirements

- Java 17+
- Spring Boot 4.1.x (the current build uses 4.1.1)
- A relational database (PostgreSQL, MySQL, or H2 for development)

## Installation

Build the current source into your local Maven repository first:

```bash
git clone https://github.com/escalated-dev/escalated-spring.git
cd escalated-spring
./gradlew publishToMavenLocal
```

On Windows, use `gradlew.bat` for the Gradle commands in this README.

Add the local artifact to your host application’s `build.gradle.kts`:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.escalated:escalated-spring:0.2.7")
}
```

Or `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.2.7</version>
</dependency>
```

## Configuration

Add to your `application.properties` or `application.yml`:

```properties
# Enable/disable the helpdesk
escalated.enabled=true

# Route prefix (default: escalated)
escalated.route-prefix=escalated

# The shared Inertia pages (off by default; Laravel defaults to on). See docs/ui.md
escalated.ui.enabled=false

# Feature toggles
escalated.knowledge-base.enabled=true
escalated.broadcasting.enabled=false
escalated.two-factor.enabled=true
escalated.widget.enabled=true
# Other sites allowed to embed the widget (none by default); see docs/embedding-the-widget.md
escalated.widget.allowed-origins=
escalated.guest-access.enabled=true
# Verified guest access (docs/guest-access.md): grant lifetime, the key for codes and
# grants (falls back to escalated.email.inbound-secret), and permanent tokens
escalated.guest-access.ttl-minutes=1440
escalated.guest-access.secret=change-me
escalated.guest-access.legacy-tokens=true

# Per-client-IP limits on the public guest endpoints (429 + Retry-After when exceeded)
escalated.guest-rate-limit.enabled=true
escalated.guest-rate-limit.tickets-per-minute=5
escalated.guest-rate-limit.replies-per-minute=10
# Every request on the mobile guest routes (/escalated/api/v1/mobile/guest/*).
escalated.guest-rate-limit.requests-per-minute=60

# SLA checking interval
escalated.sla.check-interval-seconds=60
# Minutes before an SLA deadline at which sla.warning fires (0 = off)
escalated.sla.warning-minutes=30

# Automations (admin-defined, time-based) and the built-in auto-close
escalated.automations.check-interval-seconds=60
# Days after which resolved tickets are closed (0 = off)
escalated.automations.auto-close-days=7

# Snooze wake-up interval
escalated.snooze.check-interval-seconds=60

# Webhook settings
escalated.webhook.max-retries=3

# Inbound email (symmetric secret used for signed Reply-To + webhook verification)
escalated.email.domain=support.yourapp.com
escalated.email.inbound-secret=${ESCALATED_INBOUND_SECRET}

# Database (example for PostgreSQL)
spring.datasource.url=jdbc:postgresql://localhost:5432/myapp
spring.datasource.username=user
spring.datasource.password=secret
# Escalated runs its own migrations; see Database Setup
spring.jpa.hibernate.ddl-auto=validate
```

## Ticket subjects

A ticket has a **requester** (who raised it) and a **subject line** (free text).
Tickets can also be *about* host-app entities — a Project, Customer, asset — that
are not people. Attach them as **ticket subjects** so agents see what the ticket
concerns and can jump to the entity in your app.

Implement the `dev.escalated.contracts.TicketSubject` interface on host models and
register a `TicketSubjectResolver` bean to resolve `type`/`id` pairs for the UI:

```java
@Component
public class ProjectTicketSubjectResolver implements TicketSubjectResolver {
    private final ProjectRepository projects;

    @Override
    public TicketSubject resolve(String subjectType, String subjectId) {
        if (!"com.example.Project".equals(subjectType)) {
            return null;
        }
        return projects.findById(subjectId).orElse(null);
    }
}
```

Allow types the admin API may attach (prevents arbitrary type injection):

```yaml
escalated:
  ticket-subjects:
    types:
      - com.example.Project
      - com.example.Customer
```

Attach or detach via the admin API:

```
POST   /escalated/api/admin/tickets/{ticketId}/subjects   { "type", "id", "role"? }
DELETE /escalated/api/admin/tickets/{ticketId}/subjects/{linkId}
```

Ticket detail responses include `subjects[]` with
`{ type, id, role, title, subtitle, url, color, icon, missing }`. When no
resolver is registered or the entity is gone, `title` falls back to `type#id`
and `missing` is `true`.

Programmatic attach via `TicketSubjectService` works for any type when the
allowlist is empty; the API only accepts allowlisted types.

## Custom Ticket Actions

Host applications can add custom buttons to the agent ticket screen and handle
clicks with a Spring `@EventListener`. Register actions in `application.yml`:

```yaml
escalated:
  ticket-actions:
    - key: sync-crm
      label: Sync CRM
      variant: primary          # primary | secondary | danger
      confirmation: "Sync this ticket to the CRM?"
      metadata:
        icon: refresh-cw
```

Visible actions are exposed on the agent ticket detail response as
`custom_actions` (each with a `url` and `method`). Triggering one
(`POST /escalated/api/agent/tickets/{id}/actions/{action}`) validates the action
is visible (404) and enabled (403), records an internal note for auditability,
and publishes a `CustomActionTriggeredEvent`:

```java
@Component
public class CrmSyncListener {
    @EventListener
    public void onCustomAction(CustomActionTriggeredEvent event) {
        if (!"sync-crm".equals(event.getAction())) {
            return;
        }
        // event.getTicket(), event.getUserEmail(), event.getPayload(), event.getMetadata()
    }
}
```

## Database Setup

Escalated creates its tables (all prefixed `escalated_`) with Flyway migrations of its own and runs them on startup, before Hibernate validates anything. It does this itself: your application needs no Flyway configuration, and Escalated's migrations never mix with yours.

- **Where they live:** `classpath:db/escalated/postgresql` and `classpath:db/escalated/mysql` (MariaDB uses the MySQL edition). They are deliberately outside `db/migration`, the location Boot's Flyway reads, so your own migrations and Escalated's cannot collide on a version number.
- **History:** recorded in `escalated_flyway_schema_history`, never in your `flyway_schema_history`.
- **Engines:** PostgreSQL and MySQL/MariaDB. On anything else, set `escalated.datasource.migrate=false` and create the schema yourself.
- **Opting out:** `escalated.datasource.migrate=false` skips them, whether Escalated shares your database or has its own.

Upgrading from 0.1.0, which shipped the MySQL scripts in `db/migration`:

- If your own Flyway applied them, Escalated finds their rows in your `flyway_schema_history`, baselines its own history at the highest of them, and removes those rows from yours, so your Flyway does not report them missing. Nothing is run twice.
- If Hibernate `ddl-auto` created Escalated's tables instead, they are left alone and a warning is logged, as before.

### A database of Escalated's own

By default Escalated shares your application's `DataSource`, `EntityManagerFactory` and transaction manager — its tables sit alongside yours. Point it somewhere else with one property:

```properties
escalated.datasource.url=jdbc:postgresql://support-db:5432/support
```

That is enough. Escalated then gets a persistence unit of its own: its repositories bind to it, its transactions open on it, and its Flyway migrations run against it under a schema history table of their own (`escalated_flyway_schema_history`). Your application's database is left alone entirely — no Escalated tables are created in it and no Escalated transaction is opened on it.

Everything else falls back to your own `spring.datasource.*`, so a second database on the same server needs one line rather than five:

| Property | Default | |
|---|---|---|
| `escalated.datasource.url` | *unset* | The only property that decides. Unset, or blank, means "share the host's database" |
| `escalated.datasource.username` | your `spring.datasource.username` | |
| `escalated.datasource.password` | your `spring.datasource.password` | |
| `escalated.datasource.driver-class-name` | derived from the URL | |
| `escalated.datasource.platform` | detected | Hibernate dialect |
| `escalated.datasource.ddl-auto` | `none` | Schema management belongs to Flyway |
| `escalated.datasource.migrate` | `true` | Run Escalated's migrations on startup; applies to the shared database too |

The two need not be the same engine — your application on MySQL and Escalated on PostgreSQL is a supported arrangement.

### Your users stay in your database

Escalated owns no user entity. The admin roles page reads `is_admin` / `is_agent` on `escalated_agent_profiles`, which is one of Escalated's own tables, and ticket columns that reference one of your users hold a plain value with no foreign key behind it.

That is deliberate, and it is what makes the split possible at all: no database can join across two connections. The cost is that Escalated cannot filter or sort its tables by a column that lives on your user — assignment, skill routing and agent load all resolve ids from Escalated's own tables first.

### Transactions

Every `@Transactional` in the package names `escalatedTransactionManager` explicitly. While Escalated shares your database that name is an *alias* of your own transaction manager, so it is the same bean and the same transaction it always was. A host `@Transactional` method that calls into Escalated joins one transaction as before.

Once Escalated has a database of its own, the two are separate transactions, because they are separate connections. A host transaction that rolls back will not roll back the Escalated work it triggered.

## Internationalization

Translations are consumed from the central [`dev.escalated:escalated-locale`](https://github.com/escalated-dev/escalated-locale) Maven artifact, which ships bundles at `META-INF/escalated/locale/messages_{locale}.properties` on the classpath. The auto-configured `MessageSource` chains two basenames so host apps can override keys without forking the central bundle:

1. `classpath:i18n/overrides/messages` — sparse host-app overrides (first match wins)
2. `classpath:META-INF/escalated/locale/messages` — central artifact (canonical strings)

Drop a `messages_{locale}.properties` file under `src/main/resources/i18n/overrides/` to override individual keys. See [`src/main/resources/i18n/overrides/README.md`](src/main/resources/i18n/overrides/README.md) for examples. To fix a typo or mistranslation that affects every host plugin, open a PR against the central [`escalated-locale`](https://github.com/escalated-dev/escalated-locale) repo instead.

## Inbound email

Point your Postmark, Mailgun, or AWS SES (via SNS HTTP subscription) inbound webhook at:

```
POST /escalated/webhook/email/inbound?adapter=postmark
POST /escalated/webhook/email/inbound?adapter=mailgun
POST /escalated/webhook/email/inbound?adapter=ses
```

The adapter can be selected via the query parameter or the `X-Escalated-Adapter` header. Your provider must attach the shared secret as an `X-Escalated-Inbound-Secret` header, which is compared with `MessageDigest.isEqual` (timing-safe).

Because the webhook requires `escalated.email.inbound-secret`, outbound mail carries the signed `Reply-To` address (`reply+{id}.{hmac8}@domain`), and that address is the only thing that links an inbound message to an existing ticket. When the service is called without a secret configured, it falls back to the canonical `Message-ID` headers and subject-reference tags (e.g. `[ESC-00001]`).

A matched message becomes a reply only when its `From` address is the ticket's requester (compared case-insensitively). The reply is posted as the requester, never as whoever the `From` header names, and it reopens a resolved or closed ticket. Agents reply in the app, not by email. Mail from anyone else, including an address that belongs to an agent, opens a new ticket of its own and leaves the matched ticket untouched. Unmatched messages with real content create a new ticket; SNS subscription confirmations and empty body+subject messages are skipped.

`From` can still be forged, so also have your inbound provider enforce SPF, DKIM and DMARC.

See the [inbound email docs](https://docs.escalated.dev/inbound-email) for provider setup, the response shape, and a ready-to-paste curl test recipe.

## API Endpoints

### Admin (`/escalated/api/admin/`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/tickets` | List tickets (paginated, filterable) |
| POST | `/tickets` | Create ticket |
| GET | `/tickets/{id}` | Get ticket |
| PUT | `/tickets/{id}` | Update ticket |
| POST | `/tickets/{id}/assign` | Assign ticket |
| POST | `/tickets/{id}/status` | Change status |
| POST | `/tickets/{id}/snooze` | Snooze ticket |
| POST | `/tickets/{id}/merge` | Merge tickets |
| POST | `/tickets/{id}/split` | Split ticket |
| POST | `/tickets/{id}/department` | Change department (`department_id`) |
| POST | `/tickets/{id}/tags` | Add a tag (`tag`) |
| DELETE | `/tickets/{id}/tags/{tagId}` | Remove a tag |
| DELETE | `/tickets/{id}` | Delete ticket |
| GET/POST | `/departments` | CRUD departments |
| GET/POST | `/agents` | CRUD agents |
| GET/POST | `/webhooks` | CRUD webhooks |
| GET/POST | `/workflows` | List / create workflows (the shared builder's body, see the [workflow admin contract](https://github.com/escalated-dev/escalated-developer-context/blob/main/domain-model/workflow-admin-contract.md)) |
| GET | `/workflows/options` | Builder options: `trigger_events`, `action_types`, `operators`, `condition_fields` |
| GET/PUT/DELETE | `/workflows/{id}` | Show / update / delete a workflow |
| POST | `/workflows/{id}/toggle` | Enable or disable |
| POST | `/workflows/reorder` | `{"workflow_ids": [3, 1, 2]}` |
| GET | `/workflows/{id}/logs` | Run log |
| GET/POST | `/automations` | List / create time-based automations (`name`, `conditions`, `actions`, `active`, `repeat_after_hours`) |
| GET | `/automations/options` | Condition fields, operators and action types the runner understands |
| GET/PUT/DELETE | `/automations/{id}` | Show / update / delete an automation |
| POST | `/automations/{id}/toggle` | Enable or disable |
| GET/POST | `/roles` | CRUD roles |
| GET/POST | `/custom-fields` | CRUD custom fields; `POST /custom-fields/reorder` (see [docs/custom-fields.md](docs/custom-fields.md)) |
| GET/POST | `/custom-objects` | CRUD custom objects; `/custom-objects/{id}/records` for their records |
| GET/POST | `/settings` | Manage settings |
| GET/PUT | `/settings/public-tickets` | Runtime guest-policy mode (unassigned / guest_user / prompt_signup). See [docs.escalated.dev/public-tickets](https://docs.escalated.dev/public-tickets). |
| GET | `/audit-logs` | View audit logs |
| POST | `/import/tickets` | Import tickets |
| GET/POST | `/kb/categories` | Manage KB categories |
| GET/POST | `/kb/articles` | Manage KB articles |

### Agent (`/escalated/api/agent/`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/tickets` | List assigned/filtered tickets |
| GET | `/tickets/{id}` | View ticket |
| POST | `/tickets/{id}/replies` | Add reply |
| POST | `/tickets/{id}/macro/{macroId}` | Apply macro |
| POST | `/tickets/{id}/side-conversations` | Create side conversation |
| POST | `/tickets/{id}/links` | Link tickets |
| GET/POST | `/saved-views` | Manage saved views |
| GET/POST | `/canned-responses` | Manage canned responses |

### Customer (`/escalated/api/customer/`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/tickets?email=` | List customer tickets |
| POST | `/tickets` | Create ticket |
| POST | `/tickets/{id}/replies` | Add reply |

### Widget (`/escalated/api/widget/`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/config` | Widget configuration |
| GET | `/embed.js` | Standalone widget script |
| POST | `/verification` | Email a verification code (`email`, `purpose`) |
| POST | `/tickets` | Create ticket (public; needs a `ticket` code; optional `metadata` object) |
| POST | `/lookup` | Find a ticket by reference and verified email; answers fresh grants |
| GET | `/tickets/{reference}` | Ticket status, with `Authorization: Bearer <grant>` (or a permanent token in the path while `legacy-tokens` is on) |
| POST | `/tickets/{reference}/replies` | Reply, with a grant or a permanent token |
| GET | `/kb/search?query=` | Search knowledge base |
| POST | `/csat/{token}` | Submit satisfaction rating |

### Guest (`/escalated/api/guest/`)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/verification`, `/lookup` | As on the widget |
| GET | `/tickets/{reference}` | View ticket (grant or permanent token, as on the widget) |
| GET | `/tickets/{reference}/replies` | View replies |
| POST | `/tickets/{reference}/replies` | Add reply |

### Guest endpoint rate limits

Guest ticket creation (`POST /escalated/api/widget/tickets`) and widget chat starts
(`POST /escalated/api/widget/chat/start`, which files a ticket too) share a limit of
5 per client IP per minute, and guest replies (`POST .../tickets/{token}/replies` under
both `/widget` and `/guest`) to 10, each with its own counter. A request over the
limit gets `429` with `Retry-After`. Replies are counted before the guest token is
looked up, so wrong-token guesses count too. Tune or switch it off with
`escalated.guest-rate-limit.*`; turn it off only when you already throttle these
routes upstream.

The client IP is `HttpServletRequest#getRemoteAddr()`. **Behind a reverse proxy
or load balancer, set `server.forward-headers-strategy` (and your container's
trusted proxies)**, or every guest shares the proxy's address. Counters are kept
in memory per process; a multi-instance deployment should define a shared
`dev.escalated.services.ratelimit.GuestRateLimitStore` bean (e.g. Redis-backed).

## Architecture

```
dev.escalated/
  config/              Auto-configuration, properties, WebSocket config
  models/              JPA entities with full relationships
  repositories/        Spring Data JPA repositories
  services/            Business logic (transactional)
  controllers/
    admin/             Admin REST API
    agent/             Agent REST API
    customer/          Customer REST API
    widget/            Public widget API
  events/              Spring application events + webhook listener
  security/            API token auth filter, security config, 2FA
  scheduling/          @Scheduled tasks (snooze, SLA, automations)
```

## Authentication

### Who may call what

| Path | Requires |
|---|---|
| `/escalated/api/admin/**` | an admin |
| `/escalated/api/agent/**` | an agent or an admin |
| `/escalated/api/customer/**` | any authenticated user; only their own tickets (requester email = principal name) |
| `/escalated/api/attachments/{id}/download` | an agent who can see the ticket, or the ticket's requester (never an internal note's attachment). Always served as a download, `nosniff` |
| `/escalated/api/widget/**`, `/escalated/api/guest/**`, `/escalated/api/csat/**` | nothing |
| `/escalated/api/v1/auth/**` | nothing; the endpoints check host-issued tokens through your `EscalatedApiAuthenticator` |
| `/escalated/webhook/email/inbound` | the `X-Escalated-Inbound-Secret` header |

An admin is a caller whose `escalated_agent_profiles` row, matched by principal name (email), is active with `is_admin` set; an agent is the same with `is_agent` or `is_admin`. If your roles live in your own user store instead, grant your users `ROLE_ESCALATED_ADMIN` or `ROLE_ESCALATED_AGENT` (`EscalatedAuthorization.ADMIN_ROLE` / `AGENT_ROLE`) and no profile lookup is made.

The API chain is stateless: it authenticates Escalated API tokens and does not read your application's HTTP session.

### API tokens

API endpoints use Bearer token authentication. Create tokens via the admin API:

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

The response includes the plain-text token (shown only once). Use it in subsequent requests:

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

`abilities` is a list of `agent`, `admin` and `*` (the default), and
`expires_in_days` (1-365) is optional. As in the Laravel reference, the agent
API needs a token with `agent` (or `*`) and the admin API one with `admin` (or
`*`). The token's owner must also still pass that gate, so a token never grants
more than its owner has. Tokens made before abilities were enforced hold `*`.
`GET /escalated/api/admin/tokens` lists tokens (never the token itself),
`PUT /tokens/{id}` changes `name` or `abilities`, and `DELETE /tokens/{id}`
revokes one.

## WebSocket (Real-time)

Enable with `escalated.broadcasting.enabled=true`. Connect to `/escalated/ws` via SockJS/STOMP.

- Origins: same-origin, plus the sites in `escalated.widget.allowed-origins`.
- Staff authenticate with the host session used for the handshake, or an Escalated API token in the STOMP `CONNECT` frame (`Authorization: Bearer <token>`). Staff may subscribe to `/topic/chat-queue` and any chat topic.
- A widget visitor may subscribe only to their own chat's `/topic/chat/{id}`, `/topic/chat/{id}/messages` and `/topic/chat/{id}/typing`, sending the chat token from `/widget/chat/start` in a `chat-token` header on `SUBSCRIBE`.
- Clients cannot `SEND` to `/topic` or `/queue` destinations.

## Development

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest

# Verify the wrapper checksum and boot a host from the documented Maven package
python scripts/verify_package.py

# Audit the resolved runtime, test, processor, Checkstyle and Gradle-plugin graphs
python scripts/verify_dependencies.py
```

Package verification requires Python 3, Java 17+ and Maven 3.9+. Alternatively,
`python scripts/verify_package.py --maven-docker` runs the Maven consumer with
Java 17 in Docker. It checks Gradle metadata, POM-only resolution and actual
Maven compilation, host startup and dependency audits.

## Newsletters (optional, partial port)

JPA entities + renderer for the admin-only newsletter broadcast feature. Schema is auto-derived by Hibernate from the new `@Entity` classes when `spring.jpa.hibernate.ddl-auto=update`. Production hosts using Flyway / Liquibase generate the SQL migration with `mvn spring-boot:run` or `mvn flyway:migrate` after referencing this package.

```java
import dev.escalated.services.newsletter.NewsletterRenderer;

var opts = new NewsletterRenderer.Options();
opts.baseUrl = "https://support.example.com";
opts.defaultTheme = "default";
opts.trackingEnabled = true;
opts.themesDir = "src/main/resources/templates/escalated/newsletter_themes";
opts.markdownToHtml = md -> /* plug in flexmark, commonmark-java, etc. */;
opts.brandName = "Acme";
opts.brandAccent = "#2563eb";

var renderer = new NewsletterRenderer(opts);
var html = renderer.render(delivery, newsletter, contact, template);
```

Ships: `models/newsletter/*.java` (5 JPA entities), `models/Contact.java` (gains `marketingOptOutAt`), `services/newsletter/NewsletterRenderer.java`, `resources/templates/escalated/newsletter_themes/{default,branded}.html`.

Follow-up PR: Flyway / Liquibase migration files, planner/dispatcher/tracker services using Spring `JpaRepository`s, Spring MVC controllers.

## License

MIT License. See [LICENSE](LICENSE) for details.
