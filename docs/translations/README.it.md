<p align="center">
  <a href="README.ar.md">العربية</a> •
  <a href="README.de.md">Deutsch</a> •
  <a href="../../README.md">English</a> •
  <a href="README.es.md">Español</a> •
  <a href="README.fr.md">Français</a> •
  <b>Italiano</b> •
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

Un sistema di helpdesk integrabile per applicazioni Spring Boot. Aggiungi un desk di supporto completo a qualsiasi applicazione Java con una singola dipendenza.

## Funzionalità

1. **Ticket CRUD** -- Gestione completa del ciclo di vita con stati, priorità e assegnazioni
2. **SLA Policies** -- SLA configurabili con supporto orari lavorativi e calendari festività
3. **Automations** -- Regole temporali per la chiusura automatica dei ticket risolti e assegnazione automatica
4. **Escalation Rules** -- Escalation automatica in caso di violazione SLA con riassegnazione e notifiche
5. **Macros & Canned Responses** -- Azioni predefinite e modelli di risposta per gli agenti
6. **Custom Fields** -- Dati ticket estensibili con molteplici tipi di campo
7. **Knowledge Base** -- Articoli e categorie con ricerca, conteggio visualizzazioni e feedback
8. **Webhooks** -- Consegna webhook firmati HMAC con logica di ripetizione
9. **API Tokens** -- Autenticazione con token hashato SHA-256 per accesso API
10. **Roles & Permissions** -- Controllo degli accessi granulare basato sui ruoli
11. **Audit Logging** -- Traccia di audit completa per tutte le azioni
12. **Import System** -- Importazione massiva di ticket da dati strutturati
13. **Side Conversations** -- Conversazioni private con thread all'interno dei ticket
14. **Ticket Merging & Linking** -- Unire ticket duplicati e collegare quelli correlati
15. **Ticket Splitting** -- Dividere ticket complessi in problemi separati
16. **Ticket Snooze** -- Posticipare ticket con risveglio automatico tramite `@Scheduled`
17. **Email Threading** -- Modelli email HTML personalizzati tramite Thymeleaf con threading corretto di Message-ID
18. **Saved Views** -- Viste ticket personalizzate filtrate/ordinate per agente
19. **Widget API** -- Endpoint REST pubblici per incorporare un widget di supporto
20. **Real-time Broadcasting** -- WebSocket tramite STOMP/SockJS (opzionale)
21. **Capacity Management** -- Monitoraggio e applicazione dei limiti di carico di lavoro degli agenti
22. **Skill-based Routing** -- Instradamento ticket verso agenti con competenze corrispondenti
23. **CSAT Ratings** -- Sondaggi di soddisfazione clienti con accesso basato su token
24. **2FA (TOTP)** -- Supporto password monouso basata sul tempo per account agente
25. **Guest Access** -- Accesso ai ticket basato su token senza autenticazione

## Requisiti

- Java 17+
- Spring Boot 3.2+
- Un database relazionale (PostgreSQL, MySQL o H2 per lo sviluppo)

## Installazione

Aggiungi la dipendenza al tuo `build.gradle.kts`:

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

O `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Configurazione

Aggiungi al tuo `application.properties` o `application.yml`:

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

## Configurazione del database

Le migrazioni Flyway sono incluse e vengono eseguite automaticamente. La migrazione crea tutte le tabelle con il prefisso `escalated_` e inizializza ruoli e permessi predefiniti.

## Endpoint API

### Admin (`/escalated/api/admin/`)
| Metodo | Percorso | Descrizione |
|--------|------|-------------|
| GET | `/tickets` | Elenco ticket (paginato, filtrabile) |
| POST | `/tickets` | Crea ticket |
| GET | `/tickets/{id}` | Ottieni ticket |
| PUT | `/tickets/{id}` | Aggiorna ticket |
| POST | `/tickets/{id}/assign` | Assegna ticket |
| POST | `/tickets/{id}/status` | Cambia stato |
| POST | `/tickets/{id}/snooze` | Posticipa ticket |
| POST | `/tickets/{id}/merge` | Unisci ticket |
| POST | `/tickets/{id}/split` | Dividi ticket |
| DELETE | `/tickets/{id}` | Elimina ticket |
| GET/POST | `/departments` | Gestisci dipartimenti |
| GET/POST | `/agents` | Gestisci agenti |
| GET/POST | `/webhooks` | Gestisci webhook |
| GET/POST | `/roles` | Gestisci ruoli |
| GET/POST | `/custom-fields` | Gestisci campi personalizzati |
| GET/POST | `/settings` | Gestisci impostazioni |
| GET | `/audit-logs` | Visualizza log di audit |
| POST | `/import/tickets` | Importa ticket |
| GET/POST | `/kb/categories` | Gestisci categorie KB |
| GET/POST | `/kb/articles` | Gestisci articoli KB |

### Agent (`/escalated/api/agent/`)
| Metodo | Percorso | Descrizione |
|--------|------|-------------|
| GET | `/tickets` | Elenco ticket assegnati/filtrati |
| GET | `/tickets/{id}` | Visualizza ticket |
| POST | `/tickets/{id}/replies` | Aggiungi risposta |
| POST | `/tickets/{id}/macro/{macroId}` | Applica macro |
| POST | `/tickets/{id}/side-conversations` | Crea conversazione laterale |
| POST | `/tickets/{id}/links` | Collega ticket |
| GET/POST | `/saved-views` | Gestisci viste salvate |
| GET/POST | `/canned-responses` | Gestisci risposte predefinite |

### Customer (`/escalated/api/customer/`)
| Metodo | Percorso | Descrizione |
|--------|------|-------------|
| GET | `/tickets?email=` | Elenco ticket del cliente |
| POST | `/tickets` | Crea ticket |
| POST | `/tickets/{id}/replies` | Aggiungi risposta |

### Widget (`/escalated/api/widget/`)
| Metodo | Percorso | Descrizione |
|--------|------|-------------|
| POST | `/tickets` | Crea ticket (pubblico) |
| GET | `/tickets/{token}` | Visualizza ticket tramite token ospite |
| POST | `/tickets/{token}/replies` | Rispondi tramite token ospite |
| GET | `/kb/search?query=` | Cerca nella base di conoscenza |
| POST | `/csat/{token}` | Invia valutazione soddisfazione |

### Guest (`/escalated/api/guest/`)
| Metodo | Percorso | Descrizione |
|--------|------|-------------|
| GET | `/tickets/{token}` | Visualizza ticket |
| GET | `/tickets/{token}/replies` | Visualizza risposte |
| POST | `/tickets/{token}/replies` | Aggiungi risposta |

## Architettura

```
dev.escalated/
  config/              Auto-configurazione, proprietà, configurazione WebSocket
  models/              Entità JPA con relazioni complete
  repositories/        Repository Spring Data JPA
  services/            Logica di business (transazionale)
  controllers/
    admin/             API REST amministratore
    agent/             API REST agente
    customer/          API REST cliente
    widget/            API widget pubblica
  events/              Eventi applicazione Spring + listener webhook
  security/            Filtro autenticazione token API, configurazione sicurezza, 2FA
  scheduling/          Attività @Scheduled (posticipo, SLA, automatizzazioni)
```

## Autenticazione

Gli endpoint API utilizzano l'autenticazione con token Bearer. Crea token tramite l'API amministratore:

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

La risposta include il token in testo semplice (mostrato solo una volta). Usalo nelle richieste successive:

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket (Tempo reale)

Attiva con `escalated.broadcasting.enabled=true`. Connettiti a `/escalated/ws` tramite SockJS/STOMP.

## Sviluppo

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## Licenza

Licenza MIT. Vedi [LICENSE](LICENSE) per i dettagli.
