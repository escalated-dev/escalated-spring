<p align="center">
  <a href="README.ar.md">العربية</a> •
  <a href="README.de.md">Deutsch</a> •
  <a href="../../README.md">English</a> •
  <a href="README.es.md">Español</a> •
  <a href="README.fr.md">Français</a> •
  <a href="README.it.md">Italiano</a> •
  <a href="README.ja.md">日本語</a> •
  <a href="README.ko.md">한국어</a> •
  <b>Nederlands</b> •
  <a href="README.pl.md">Polski</a> •
  <a href="README.pt-BR.md">Português (BR)</a> •
  <a href="README.ru.md">Русский</a> •
  <a href="README.tr.md">Türkçe</a> •
  <a href="README.zh-CN.md">简体中文</a>
</p>

# Escalated Spring

Een inbedbaar helpdesksysteem voor Spring Boot-applicaties. Voeg een volledig uitgerust supportbureau toe aan elke Java-applicatie met een enkele afhankelijkheid.

## Functies

1. **Ticket CRUD** -- Volledig levenscyclusbeheer met statussen, prioriteiten en toewijzingen
2. **SLA Policies** -- Configureerbare SLA's met ondersteuning voor kantooruren en vakantiekalenders
3. **Automations** -- Tijdgebaseerde regels voor automatisch sluiten van opgeloste tickets en automatische toewijzing
4. **Escalation Rules** -- Automatische escalatie bij SLA-schending met hertoewijzing en notificaties
5. **Macros & Canned Responses** -- Voorgedefinieerde acties en antwoordsjablonen voor agenten
6. **Custom Fields** -- Uitbreidbare ticketgegevens met meerdere veldtypen
7. **Knowledge Base** -- Artikelen en categorieën met zoeken, weergavetellers en feedback
8. **Webhooks** -- HMAC-ondertekende webhook-bezorging met herhalingslogica
9. **API Tokens** -- SHA-256 gehashte tokenauthenticatie voor API-toegang
10. **Roles & Permissions** -- Gedetailleerde rolgebaseerde toegangscontrole
11. **Audit Logging** -- Volledige audit trail voor alle acties
12. **Import System** -- Bulk import van tickets uit gestructureerde gegevens
13. **Side Conversations** -- Privé thread-gesprekken binnen tickets
14. **Ticket Merging & Linking** -- Dubbele tickets samenvoegen en gerelateerde koppelen
15. **Ticket Splitting** -- Complexe tickets opsplitsen in afzonderlijke problemen
16. **Ticket Snooze** -- Tickets snoozen met automatisch wekken via `@Scheduled`
17. **Email Threading** -- Merkgebonden HTML e-mailsjablonen via Thymeleaf met correcte Message-ID-threading
18. **Saved Views** -- Aangepaste gefilterde/gesorteerde ticketweergaven per agent
19. **Widget API** -- Openbare REST-endpoints voor het inbedden van een supportwidget
20. **Real-time Broadcasting** -- WebSocket via STOMP/SockJS (optioneel)
21. **Capacity Management** -- Werkbelastingslimieten van agenten bijhouden en afdwingen
22. **Skill-based Routing** -- Tickets routeren naar agenten met overeenkomende vaardigheden
23. **CSAT Ratings** -- Klanttevredenheidsonderzoeken met tokengebaseerde toegang
24. **2FA (TOTP)** -- Tijdgebaseerde eenmalige wachtwoordondersteuning voor agentaccounts
25. **Guest Access** -- Tokengebaseerde tickettoegang zonder authenticatie

## Vereisten

- Java 17+
- Spring Boot 3.2+
- Een relationele database (PostgreSQL, MySQL of H2 voor ontwikkeling)

## Installatie

Voeg de afhankelijkheid toe aan uw `build.gradle.kts`:

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

Of `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Configuratie

Voeg toe aan uw `application.properties` of `application.yml`:

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

## Database-installatie

Flyway-migraties zijn inbegrepen en worden automatisch uitgevoerd. De migratie maakt alle tabellen aan met het voorvoegsel `escalated_` en vult standaardrollen en -machtigingen in.

## API-endpoints

### Admin (`/escalated/api/admin/`)
| Methode | Pad | Beschrijving |
|--------|------|-------------|
| GET | `/tickets` | Tickets weergeven (gepagineerd, filterbaar) |
| POST | `/tickets` | Ticket aanmaken |
| GET | `/tickets/{id}` | Ticket ophalen |
| PUT | `/tickets/{id}` | Ticket bijwerken |
| POST | `/tickets/{id}/assign` | Ticket toewijzen |
| POST | `/tickets/{id}/status` | Status wijzigen |
| POST | `/tickets/{id}/snooze` | Ticket snoozen |
| POST | `/tickets/{id}/merge` | Tickets samenvoegen |
| POST | `/tickets/{id}/split` | Ticket opsplitsen |
| DELETE | `/tickets/{id}` | Ticket verwijderen |
| GET/POST | `/departments` | Afdelingen beheren |
| GET/POST | `/agents` | Agenten beheren |
| GET/POST | `/webhooks` | Webhooks beheren |
| GET/POST | `/roles` | Rollen beheren |
| GET/POST | `/custom-fields` | Aangepaste velden beheren |
| GET/POST | `/settings` | Instellingen beheren |
| GET | `/audit-logs` | Auditlogs bekijken |
| POST | `/import/tickets` | Tickets importeren |
| GET/POST | `/kb/categories` | KB-categorieën beheren |
| GET/POST | `/kb/articles` | KB-artikelen beheren |

### Agent (`/escalated/api/agent/`)
| Methode | Pad | Beschrijving |
|--------|------|-------------|
| GET | `/tickets` | Toegewezen/gefilterde tickets weergeven |
| GET | `/tickets/{id}` | Ticket bekijken |
| POST | `/tickets/{id}/replies` | Antwoord toevoegen |
| POST | `/tickets/{id}/macro/{macroId}` | Macro toepassen |
| POST | `/tickets/{id}/side-conversations` | Zijgesprek aanmaken |
| POST | `/tickets/{id}/links` | Tickets koppelen |
| GET/POST | `/saved-views` | Opgeslagen weergaven beheren |
| GET/POST | `/canned-responses` | Standaardantwoorden beheren |

### Customer (`/escalated/api/customer/`)
| Methode | Pad | Beschrijving |
|--------|------|-------------|
| GET | `/tickets?email=` | Klanttickets weergeven |
| POST | `/tickets` | Ticket aanmaken |
| POST | `/tickets/{id}/replies` | Antwoord toevoegen |

### Widget (`/escalated/api/widget/`)
| Methode | Pad | Beschrijving |
|--------|------|-------------|
| POST | `/tickets` | Ticket aanmaken (openbaar) |
| GET | `/tickets/{token}` | Ticket bekijken via gasttoken |
| POST | `/tickets/{token}/replies` | Antwoorden via gasttoken |
| GET | `/kb/search?query=` | Kennisbank doorzoeken |
| POST | `/csat/{token}` | Tevredenheidsbeoordeling indienen |

### Guest (`/escalated/api/guest/`)
| Methode | Pad | Beschrijving |
|--------|------|-------------|
| GET | `/tickets/{token}` | Ticket bekijken |
| GET | `/tickets/{token}/replies` | Antwoorden bekijken |
| POST | `/tickets/{token}/replies` | Antwoord toevoegen |

## Architectuur

```
dev.escalated/
  config/              Auto-configuratie, eigenschappen, WebSocket-configuratie
  models/              JPA-entiteiten met volledige relaties
  repositories/        Spring Data JPA-repositories
  services/            Bedrijfslogica (transactioneel)
  controllers/
    admin/             Admin REST API
    agent/             Agent REST API
    customer/          Klant REST API
    widget/            Openbare widget-API
  events/              Spring-applicatiegebeurtenissen + webhook-listener
  security/            API-tokenauthenticatiefilter, beveiligingsconfiguratie, 2FA
  scheduling/          @Scheduled-taken (snooze, SLA, automatiseringen)
```

## Authenticatie

API-endpoints gebruiken Bearer-tokenauthenticatie. Maak tokens aan via de admin-API:

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

Het antwoord bevat het token in platte tekst (wordt slechts eenmaal getoond). Gebruik het in volgende verzoeken:

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket (Realtime)

Activeer met `escalated.broadcasting.enabled=true`. Verbind met `/escalated/ws` via SockJS/STOMP.

## Ontwikkeling

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## Licentie

MIT-licentie. Zie [LICENSE](LICENSE) voor details.
