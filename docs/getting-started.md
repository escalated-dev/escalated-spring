# Getting started

## Requirements

- Java 17+
- Spring Boot 4.1.x (the current build uses 4.1.2)
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
    implementation("dev.escalated:escalated-spring:0.2.13")
}
```

Or `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.2.13</version>
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

# The admin embedded in your own admin panel (off by default). See docs/embedding-admin.md
escalated.ui.embed.enabled=false

# Debug mode (off by default): a 500 on an Inertia visit reaches Boot's own error handling
# instead of the generic Error page. Development only; see docs/ui.md
escalated.debug=false

# Feature toggles
escalated.knowledge-base.enabled=true
escalated.broadcasting.enabled=false
escalated.two-factor.enabled=true
# The widget's routes (env ESCALATED_WIDGET_ENABLED). false mounts no /escalated/api/widget/** route
escalated.widget.enabled=true
# Other sites allowed to embed the widget (none by default); see docs/embedding-the-widget.md
escalated.widget.allowed-origins=
escalated.guest-access.enabled=true
# Verified guest access (docs/guest-access.md): grant lifetime, the key for codes and
# grants (falls back to escalated.email.inbound-secret), and permanent tokens
escalated.guest-access.ttl-minutes=1440
escalated.guest-access.secret=change-me
escalated.guest-access.legacy-tokens=true
# The guest portal's routes (env ESCALATED_GUEST_ROUTES). false mounts no /escalated/guest/** and
# no /escalated/api/guest/** route; the guest_tickets_enabled setting still switches it at runtime
escalated.guest.routes-enabled=true

# Extra guards on the customer portal (customer pages, /escalated/api/customer/**, /escalated/kb/**),
# on top of being signed in. Empty by default. See "A staff-only install" below
escalated.routes.customer-middleware=

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

## Required: schedule the recurring jobs

SLA checks, escalation rules, automations and snooze wake-ups are recurring jobs. In this package
they are `@Scheduled` methods that Escalated's auto-configuration registers with Spring's
in-process scheduler (it turns on `@EnableScheduling`), so there is nothing to add to cron and no
command to run. They need the application running with its task scheduler working:

| Job | Interval |
|---|---|
| `SlaScheduler#checkSlaBreaches` (SLA checks and escalation rules) | `escalated.sla.check-interval-seconds`, default every 60 seconds |
| `AutomationScheduler#runAutomations` (automations) | `escalated.automations.check-interval-seconds`, default every 60 seconds |
| `AutomationScheduler#autoCloseResolvedTickets` (auto-close) | every 5 minutes |
| `SnoozeScheduler#wakeUpSnoozedTickets` (snooze wake-ups) | `escalated.snooze.check-interval-seconds`, default every 60 seconds |

Each SLA check records a heartbeat (the `scheduler_heartbeat_at` setting). When it is missing or
more than 15 minutes old, admins see a warning naming these jobs: the `scheduler` prop on
`Escalated/Admin/Settings`, `Escalated/Admin/Reports` and `Escalated/Admin/Reports/Dashboard`, and
`GET /escalated/api/admin/settings/scheduler` for API-only installs, each
`{healthy, last_run_at, message, commands: [{command, interval}]}`. A warning is logged too. The
usual causes are a host that disables scheduling, a scheduler pool kept busy by other work, or a job
that fails every time (check the log).

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

## Authentication

### Who may call what

| Path | Requires |
|---|---|
| `/escalated/api/admin/**` | an admin |
| `/escalated/api/agent/**` | an agent or an admin |
| `/escalated/api/customer/**` | any authenticated user, plus `escalated.routes.customer-middleware` when set; only their own tickets (requester email = principal name) |
| `/escalated/api/attachments/{id}/download` | an agent who can see the ticket, or the ticket's requester (never an internal note's attachment). Always served as a download, `nosniff` |
| `/escalated/api/widget/**`, `/escalated/api/guest/**`, `/escalated/api/csat/**` | nothing |
| `/escalated/api/v1/auth/**` | nothing; the endpoints check host-issued tokens through your `EscalatedApiAuthenticator` |
| `/escalated/webhook/email/inbound` | the `X-Escalated-Inbound-Secret` header |

An admin is a caller whose `escalated_agent_profiles` row, matched by principal name (email), is active with `is_admin` set; an agent is the same with `is_agent` or `is_admin`. If your roles live in your own user store instead, grant your users `ROLE_ESCALATED_ADMIN` or `ROLE_ESCALATED_AGENT` (`EscalatedAuthorization.ADMIN_ROLE` / `AGENT_ROLE`) and no profile lookup is made.

The API chain is stateless: it authenticates Escalated API tokens and does not read your application's HTTP session.

### A staff-only install

By default the customer portal (the customer pages, `/escalated/api/customer/**` and the customer
knowledge base under `/escalated/kb`) is open to any signed-in user, so where only staff use
Escalated, a signed-in user who was never given Escalated access could still list and raise tickets.
To close it:

```properties
# Customer routes: Escalated's agent gate on top of being signed in
escalated.routes.customer-middleware=escalated-agent
# No guest portal and no widget routes at all
escalated.guest.routes-enabled=false
escalated.widget.enabled=false
```

Each `customer-middleware` entry is `escalated-agent` or `escalated-admin` (Escalated's own gates)
or the bean name of your own `AuthorizationManager<RequestAuthorizationContext>`; a request must pass
every one. Spring Security answers a refusal: 403 for a signed-in user, your sign-in entry point for
anyone else. With any entry set the knowledge base is no longer public. A name that is neither gate
nor a bean stops the application at start-up.

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
