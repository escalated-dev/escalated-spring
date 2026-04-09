<p align="center">
  <a href="README.ar.md">العربية</a> •
  <b>Deutsch</b> •
  <a href="../../README.md">English</a> •
  <a href="README.es.md">Español</a> •
  <a href="README.fr.md">Français</a> •
  <a href="README.it.md">Italiano</a> •
  <a href="README.ja.md">日本語</a> •
  <a href="README.ko.md">한국어</a> •
  <a href="README.nl.md">Nederlands</a> •
  <a href="README.pl.md">Polski</a> •
  <a href="README.pt-BR.md">Português (BR)</a> •
  <a href="README.ru.md">Русский</a> •
  <a href="README.tr.md">Türkçe</a> •
  <a href="README.zh-CN.md">简体中文</a>
</p>

# Escalated Spring

Ein einbettbares Helpdesk-System für Spring Boot-Anwendungen. Fügen Sie jeder Java-Anwendung mit einer einzigen Abhängigkeit einen voll ausgestatteten Support-Desk hinzu.

## Funktionen

1. **Ticket CRUD** -- Vollständige Lebenszyklusverwaltung mit Status, Prioritäten und Zuweisungen
2. **SLA Policies** -- Konfigurierbare SLAs mit Geschäftszeiten-Unterstützung und Feiertagskalendern
3. **Automations** -- Zeitbasierte Regeln zum automatischen Schließen gelöster Tickets und automatischer Zuweisung
4. **Escalation Rules** -- Automatische Eskalation bei SLA-Verletzung mit Neuzuweisung und Benachrichtigungen
5. **Macros & Canned Responses** -- Vordefinierte Aktionen und Antwortvorlagen für Agenten
6. **Custom Fields** -- Erweiterbare Ticketdaten mit mehreren Feldtypen
7. **Knowledge Base** -- Artikel und Kategorien mit Suche, Aufrufzähler und Feedback
8. **Webhooks** -- HMAC-signierte Webhook-Zustellung mit Wiederholungslogik
9. **API Tokens** -- SHA-256-gehashte Token-Authentifizierung für API-Zugriff
10. **Roles & Permissions** -- Granulare rollenbasierte Zugriffskontrolle
11. **Audit Logging** -- Vollständiger Audit-Trail für alle Aktionen
12. **Import System** -- Massenimport von Tickets aus strukturierten Daten
13. **Side Conversations** -- Private Thread-Konversationen innerhalb von Tickets
14. **Ticket Merging & Linking** -- Doppelte Tickets zusammenführen und verwandte verknüpfen
15. **Ticket Splitting** -- Komplexe Tickets in separate Vorgänge aufteilen
16. **Ticket Snooze** -- Tickets schlummern lassen mit automatischem Aufwecken über `@Scheduled`
17. **Email Threading** -- Gebrandete HTML-E-Mail-Vorlagen über Thymeleaf mit korrektem Message-ID-Threading
18. **Saved Views** -- Benutzerdefinierte gefilterte/sortierte Ticket-Ansichten pro Agent
19. **Widget API** -- Öffentliche REST-Endpunkte zum Einbetten eines Support-Widgets
20. **Real-time Broadcasting** -- WebSocket über STOMP/SockJS (optional)
21. **Capacity Management** -- Arbeitslastgrenzen für Agenten verfolgen und durchsetzen
22. **Skill-based Routing** -- Tickets an Agenten mit passenden Fähigkeiten weiterleiten
23. **CSAT Ratings** -- Kundenzufriedenheitsumfragen mit tokenbasiertem Zugang
24. **2FA (TOTP)** -- Zeitbasierte Einmalpasswort-Unterstützung für Agentenkonten
25. **Guest Access** -- Tokenbasierter Ticketzugriff ohne Authentifizierung

## Voraussetzungen

- Java 17+
- Spring Boot 3.2+
- Eine relationale Datenbank (PostgreSQL, MySQL oder H2 für die Entwicklung)

## Installation

Fügen Sie die Abhängigkeit zu Ihrer `build.gradle.kts` hinzu:

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

Oder `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Konfiguration

Fügen Sie dies zu Ihrer `application.properties` oder `application.yml` hinzu:

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

## Datenbank-Einrichtung

Flyway-Migrationen sind enthalten und werden automatisch ausgeführt. Die Migration erstellt alle Tabellen mit dem Präfix `escalated_` und legt Standardrollen und -berechtigungen an.

## API-Endpunkte

### Admin (`/escalated/api/admin/`)
| Methode | Pfad | Beschreibung |
|--------|------|-------------|
| GET | `/tickets` | Tickets auflisten (paginiert, filterbar) |
| POST | `/tickets` | Ticket erstellen |
| GET | `/tickets/{id}` | Ticket abrufen |
| PUT | `/tickets/{id}` | Ticket aktualisieren |
| POST | `/tickets/{id}/assign` | Ticket zuweisen |
| POST | `/tickets/{id}/status` | Status ändern |
| POST | `/tickets/{id}/snooze` | Ticket schlummern lassen |
| POST | `/tickets/{id}/merge` | Tickets zusammenführen |
| POST | `/tickets/{id}/split` | Ticket aufteilen |
| DELETE | `/tickets/{id}` | Ticket löschen |
| GET/POST | `/departments` | Abteilungen verwalten |
| GET/POST | `/agents` | Agenten verwalten |
| GET/POST | `/webhooks` | Webhooks verwalten |
| GET/POST | `/roles` | Rollen verwalten |
| GET/POST | `/custom-fields` | Benutzerdefinierte Felder verwalten |
| GET/POST | `/settings` | Einstellungen verwalten |
| GET | `/audit-logs` | Audit-Logs anzeigen |
| POST | `/import/tickets` | Tickets importieren |
| GET/POST | `/kb/categories` | KB-Kategorien verwalten |
| GET/POST | `/kb/articles` | KB-Artikel verwalten |

### Agent (`/escalated/api/agent/`)
| Methode | Pfad | Beschreibung |
|--------|------|-------------|
| GET | `/tickets` | Zugewiesene/gefilterte Tickets auflisten |
| GET | `/tickets/{id}` | Ticket anzeigen |
| POST | `/tickets/{id}/replies` | Antwort hinzufügen |
| POST | `/tickets/{id}/macro/{macroId}` | Makro anwenden |
| POST | `/tickets/{id}/side-conversations` | Nebenkonversation erstellen |
| POST | `/tickets/{id}/links` | Tickets verknüpfen |
| GET/POST | `/saved-views` | Gespeicherte Ansichten verwalten |
| GET/POST | `/canned-responses` | Vorgefertigte Antworten verwalten |

### Customer (`/escalated/api/customer/`)
| Methode | Pfad | Beschreibung |
|--------|------|-------------|
| GET | `/tickets?email=` | Kundentickets anzeigen |
| POST | `/tickets` | Ticket erstellen |
| POST | `/tickets/{id}/replies` | Antwort hinzufügen |

### Widget (`/escalated/api/widget/`)
| Methode | Pfad | Beschreibung |
|--------|------|-------------|
| POST | `/tickets` | Ticket erstellen (öffentlich) |
| GET | `/tickets/{token}` | Ticket per Gast-Token anzeigen |
| POST | `/tickets/{token}/replies` | Per Gast-Token antworten |
| GET | `/kb/search?query=` | Wissensdatenbank durchsuchen |
| POST | `/csat/{token}` | Zufriedenheitsbewertung abgeben |

### Guest (`/escalated/api/guest/`)
| Methode | Pfad | Beschreibung |
|--------|------|-------------|
| GET | `/tickets/{token}` | Ticket anzeigen |
| GET | `/tickets/{token}/replies` | Antworten anzeigen |
| POST | `/tickets/{token}/replies` | Antwort hinzufügen |

## Architektur

```
dev.escalated/
  config/              Auto-Konfiguration, Eigenschaften, WebSocket-Konfiguration
  models/              JPA-Entitäten mit vollständigen Beziehungen
  repositories/        Spring Data JPA-Repositories
  services/            Geschäftslogik (transaktional)
  controllers/
    admin/             Admin REST API
    agent/             Agenten REST API
    customer/          Kunden REST API
    widget/            Öffentliche Widget-API
  events/              Spring-Anwendungsereignisse + Webhook-Listener
  security/            API-Token-Auth-Filter, Sicherheitskonfiguration, 2FA
  scheduling/          @Scheduled-Aufgaben (Schlummern, SLA, Automatisierungen)
```

## Authentifizierung

API-Endpunkte verwenden Bearer-Token-Authentifizierung. Erstellen Sie Tokens über die Admin-API:

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

Die Antwort enthält den Klartext-Token (wird nur einmal angezeigt). Verwenden Sie ihn in nachfolgenden Anfragen:

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket (Echtzeit)

Aktivieren mit `escalated.broadcasting.enabled=true`. Verbinden Sie sich mit `/escalated/ws` über SockJS/STOMP.

## Entwicklung

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## Lizenz

MIT-Lizenz. Siehe [LICENSE](LICENSE) für Details.
