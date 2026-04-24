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

An embeddable helpdesk system for Spring Boot applications. Add a full-featured support desk to any Java application with a single dependency.

## Features

1. **Ticket CRUD** -- Full lifecycle management with statuses, priorities, and assignments
2. **SLA Policies** -- Configurable SLAs with business hours support and holiday calendars
3. **Automations** -- Time-based rules for auto-closing resolved tickets and auto-assignment
4. **Escalation Rules** -- Automatic escalation on SLA breach with reassignment and notifications
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

## Requirements

- Java 17+
- Spring Boot 3.2+
- A relational database (PostgreSQL, MySQL, or H2 for development)

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

Or `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Configuration

Add to your `application.properties` or `application.yml`:

```properties
# Enable/disable the helpdesk
escalated.enabled=true

# Route prefix (default: escalated)
escalated.route-prefix=escalated

# Feature toggles
escalated.knowledge-base.enabled=true
escalated.broadcasting.enabled=false
escalated.two-factor.enabled=true
escalated.widget.enabled=true
escalated.guest-access.enabled=true

# SLA checking interval
escalated.sla.check-interval-seconds=60

# Snooze wake-up interval
escalated.snooze.check-interval-seconds=60

# Webhook settings
escalated.webhook.max-retries=3

# Database (example for PostgreSQL)
spring.datasource.url=jdbc:postgresql://localhost:5432/myapp
spring.datasource.username=user
spring.datasource.password=secret
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

## Database Setup

Flyway migrations are included and run automatically. The migration creates all tables prefixed with `escalated_` and seeds default roles and permissions.

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
| DELETE | `/tickets/{id}` | Delete ticket |
| GET/POST | `/departments` | CRUD departments |
| GET/POST | `/agents` | CRUD agents |
| GET/POST | `/webhooks` | CRUD webhooks |
| GET/POST | `/roles` | CRUD roles |
| GET/POST | `/custom-fields` | CRUD custom fields |
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
| POST | `/tickets` | Create ticket (public) |
| GET | `/tickets/{token}` | View ticket by guest token |
| POST | `/tickets/{token}/replies` | Reply via guest token |
| GET | `/kb/search?query=` | Search knowledge base |
| POST | `/csat/{token}` | Submit satisfaction rating |

### Guest (`/escalated/api/guest/`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/tickets/{token}` | View ticket |
| GET | `/tickets/{token}/replies` | View replies |
| POST | `/tickets/{token}/replies` | Add reply |

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

## WebSocket (Real-time)

Enable with `escalated.broadcasting.enabled=true`. Connect to `/escalated/ws` via SockJS/STOMP.

## Development

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## License

MIT License. See [LICENSE](LICENSE) for details.
