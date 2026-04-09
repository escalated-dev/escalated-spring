<p align="center">
  <a href="README.ar.md">العربية</a> •
  <a href="README.de.md">Deutsch</a> •
  <a href="../../README.md">English</a> •
  <a href="README.es.md">Español</a> •
  <a href="README.fr.md">Français</a> •
  <a href="README.it.md">Italiano</a> •
  <a href="README.ja.md">日本語</a> •
  <a href="README.ko.md">한국어</a> •
  <a href="README.nl.md">Nederlands</a> •
  <b>Polski</b> •
  <a href="README.pt-BR.md">Português (BR)</a> •
  <a href="README.ru.md">Русский</a> •
  <a href="README.tr.md">Türkçe</a> •
  <a href="README.zh-CN.md">简体中文</a>
</p>

# Escalated Spring

Wbudowywalny system helpdesk dla aplikacji Spring Boot. Dodaj w pełni funkcjonalne biuro wsparcia do dowolnej aplikacji Java za pomocą jednej zależności.

## Funkcje

1. **Ticket CRUD** -- Pełne zarządzanie cyklem życia ze statusami, priorytetami i przypisaniami
2. **SLA Policies** -- Konfigurowalne SLA ze wsparciem godzin pracy i kalendarzami świąt
3. **Automations** -- Reguły czasowe do automatycznego zamykania rozwiązanych zgłoszeń i automatycznego przypisywania
4. **Escalation Rules** -- Automatyczna eskalacja przy naruszeniu SLA z ponownym przypisaniem i powiadomieniami
5. **Macros & Canned Responses** -- Predefiniowane akcje i szablony odpowiedzi dla agentów
6. **Custom Fields** -- Rozszerzalne dane zgłoszeń z wieloma typami pól
7. **Knowledge Base** -- Artykuły i kategorie z wyszukiwaniem, licznikiem wyświetleń i opiniami
8. **Webhooks** -- Dostarczanie webhooków podpisanych HMAC z logiką ponawiania
9. **API Tokens** -- Uwierzytelnianie tokenem hashowanym SHA-256 dla dostępu do API
10. **Roles & Permissions** -- Szczegółowa kontrola dostępu oparta na rolach
11. **Audit Logging** -- Pełny ślad audytu dla wszystkich akcji
12. **Import System** -- Masowy import zgłoszeń ze strukturyzowanych danych
13. **Side Conversations** -- Prywatne konwersacje wątkowe wewnątrz zgłoszeń
14. **Ticket Merging & Linking** -- Łączenie duplikatów zgłoszeń i wiązanie powiązanych
15. **Ticket Splitting** -- Dzielenie złożonych zgłoszeń na oddzielne problemy
16. **Ticket Snooze** -- Odkładanie zgłoszeń z automatycznym budzeniem przez `@Scheduled`
17. **Email Threading** -- Markowe szablony e-mail HTML przez Thymeleaf z prawidłowym wątkowaniem Message-ID
18. **Saved Views** -- Niestandardowe filtrowane/sortowane widoki zgłoszeń na agenta
19. **Widget API** -- Publiczne endpointy REST do osadzania widgetu wsparcia
20. **Real-time Broadcasting** -- WebSocket przez STOMP/SockJS (opcjonalnie)
21. **Capacity Management** -- Śledzenie i egzekwowanie limitów obciążenia agentów
22. **Skill-based Routing** -- Kierowanie zgłoszeń do agentów z odpowiednimi umiejętnościami
23. **CSAT Ratings** -- Ankiety satysfakcji klienta z dostępem opartym na tokenie
24. **2FA (TOTP)** -- Obsługa haseł jednorazowych opartych na czasie dla kont agentów
25. **Guest Access** -- Dostęp do zgłoszeń oparty na tokenie bez uwierzytelniania

## Wymagania

- Java 17+
- Spring Boot 3.2+
- Relacyjna baza danych (PostgreSQL, MySQL lub H2 do programowania)

## Instalacja

Dodaj zależność do pliku `build.gradle.kts`:

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

Lub `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Konfiguracja

Dodaj do pliku `application.properties` lub `application.yml`:

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

## Konfiguracja bazy danych

Migracje Flyway są dołączone i uruchamiają się automatycznie. Migracja tworzy wszystkie tabele z prefiksem `escalated_` i inicjalizuje domyślne role i uprawnienia.

## Endpointy API

### Admin (`/escalated/api/admin/`)
| Metoda | Ścieżka | Opis |
|--------|------|-------------|
| GET | `/tickets` | Lista zgłoszeń (paginowana, z filtrami) |
| POST | `/tickets` | Utwórz zgłoszenie |
| GET | `/tickets/{id}` | Pobierz zgłoszenie |
| PUT | `/tickets/{id}` | Aktualizuj zgłoszenie |
| POST | `/tickets/{id}/assign` | Przypisz zgłoszenie |
| POST | `/tickets/{id}/status` | Zmień status |
| POST | `/tickets/{id}/snooze` | Odłóż zgłoszenie |
| POST | `/tickets/{id}/merge` | Scal zgłoszenia |
| POST | `/tickets/{id}/split` | Podziel zgłoszenie |
| DELETE | `/tickets/{id}` | Usuń zgłoszenie |
| GET/POST | `/departments` | Zarządzaj działami |
| GET/POST | `/agents` | Zarządzaj agentami |
| GET/POST | `/webhooks` | Zarządzaj webhookami |
| GET/POST | `/roles` | Zarządzaj rolami |
| GET/POST | `/custom-fields` | Zarządzaj polami niestandardowymi |
| GET/POST | `/settings` | Zarządzaj ustawieniami |
| GET | `/audit-logs` | Wyświetl dzienniki audytu |
| POST | `/import/tickets` | Importuj zgłoszenia |
| GET/POST | `/kb/categories` | Zarządzaj kategoriami KB |
| GET/POST | `/kb/articles` | Zarządzaj artykułami KB |

### Agent (`/escalated/api/agent/`)
| Metoda | Ścieżka | Opis |
|--------|------|-------------|
| GET | `/tickets` | Lista przypisanych/przefiltrowanych zgłoszeń |
| GET | `/tickets/{id}` | Wyświetl zgłoszenie |
| POST | `/tickets/{id}/replies` | Dodaj odpowiedź |
| POST | `/tickets/{id}/macro/{macroId}` | Zastosuj makro |
| POST | `/tickets/{id}/side-conversations` | Utwórz konwersację poboczną |
| POST | `/tickets/{id}/links` | Powiąż zgłoszenia |
| GET/POST | `/saved-views` | Zarządzaj zapisanymi widokami |
| GET/POST | `/canned-responses` | Zarządzaj szablonami odpowiedzi |

### Customer (`/escalated/api/customer/`)
| Metoda | Ścieżka | Opis |
|--------|------|-------------|
| GET | `/tickets?email=` | Lista zgłoszeń klienta |
| POST | `/tickets` | Utwórz zgłoszenie |
| POST | `/tickets/{id}/replies` | Dodaj odpowiedź |

### Widget (`/escalated/api/widget/`)
| Metoda | Ścieżka | Opis |
|--------|------|-------------|
| POST | `/tickets` | Utwórz zgłoszenie (publiczne) |
| GET | `/tickets/{token}` | Wyświetl zgłoszenie tokenem gościa |
| POST | `/tickets/{token}/replies` | Odpowiedz tokenem gościa |
| GET | `/kb/search?query=` | Przeszukaj bazę wiedzy |
| POST | `/csat/{token}` | Prześlij ocenę satysfakcji |

### Guest (`/escalated/api/guest/`)
| Metoda | Ścieżka | Opis |
|--------|------|-------------|
| GET | `/tickets/{token}` | Wyświetl zgłoszenie |
| GET | `/tickets/{token}/replies` | Wyświetl odpowiedzi |
| POST | `/tickets/{token}/replies` | Dodaj odpowiedź |

## Architektura

```
dev.escalated/
  config/              Auto-konfiguracja, właściwości, konfiguracja WebSocket
  models/              Encje JPA z pełnymi relacjami
  repositories/        Repozytoria Spring Data JPA
  services/            Logika biznesowa (transakcyjna)
  controllers/
    admin/             Administracyjne REST API
    agent/             REST API agenta
    customer/          REST API klienta
    widget/            Publiczne API widgetu
  events/              Zdarzenia aplikacji Spring + listener webhooków
  security/            Filtr uwierzytelniania tokenem API, konfiguracja bezpieczeństwa, 2FA
  scheduling/          Zadania @Scheduled (odkładanie, SLA, automatyzacje)
```

## Uwierzytelnianie

Endpointy API używają uwierzytelniania tokenem Bearer. Utwórz tokeny przez API administracyjne:

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

Odpowiedź zawiera token w postaci jawnej (wyświetlany tylko raz). Użyj go w kolejnych żądaniach:

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket (Czas rzeczywisty)

Aktywuj za pomocą `escalated.broadcasting.enabled=true`. Połącz się z `/escalated/ws` przez SockJS/STOMP.

## Programowanie

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## Licencja

Licencja MIT. Zobacz [LICENSE](LICENSE), aby uzyskać szczegóły.
