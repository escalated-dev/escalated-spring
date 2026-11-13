<p align="center"><sub>
  <a href="README.ar.md">العربية</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="../../README.md">English</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.ja.md">日本語</a> ·
  <a href="README.ko.md">한국어</a> ·
  <a href="README.nl.md">Nederlands</a> ·
  <b>Polski</b> ·
  <a href="README.pt-BR.md">Português (BR)</a> ·
  <a href="README.ru.md">Русский</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <a href="README.zh-CN.md">简体中文</a>
</sub></p>

<p align="center">
  <a href="https://escalated.dev"><img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/logo.png" width="72" height="72" alt="Escalated"></a>
</p>

<h1 align="center">Escalated for Spring</h1>

<p align="center">
  Dodaj kompletny helpdesk do swojej aplikacji Spring Boot.<br>
  Zgłoszenia, SLA, reguły eskalacji, baza wiedzy i widżet wsparcia, jako jedna zależność działająca na Twojej własnej bazie danych.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>Strona</b></a> ·
  <a href="https://escalated.dev/docs"><b>Dokumentacja</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>Lista zmian</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="Skrzynka agenta w Escalated: zapisane widoki z licznikami, odliczanie SLA i priorytety przy każdym zgłoszeniu" width="100%">
</picture>

## Co robi

<table>
  <tr>
    <td width="40%">
      <h3>Skrzynka, w której Twoi agenci mogą pracować cały dzień</h3>
      Rozmowa, notatki wewnętrzne, gotowe odpowiedzi i liczniki SLA na jednym ekranie. Zgłoszenia przychodzą też e-mailem przez Postmark, Mailgun lub AWS SES.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="Zgłoszenie otwarte w obszarze roboczym agenta z edytorem odpowiedzi">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="Strona raportów w panelu administracyjnym z czasami odpowiedzi, CSAT i naruszeniami SLA">
      </picture>
    </td>
    <td width="40%">
      <h3>SLA i reguły eskalacji, które widać</h3>
      Cele czasu odpowiedzi i rozwiązania z uwzględnieniem godzin pracy i świąt, reguły, które eskalują lub ponownie przypisują zgłoszenie po naruszeniu, oraz raporty SLA.
    </td>
  </tr>
</table>

## Funkcje

<table>
<tr>
<td width="33%" valign="top">

**Zgłoszenia i skrzynka**
- Statusy, priorytety i przypisywanie
- Makra i gotowe odpowiedzi
- Rozmowy poboczne w zgłoszeniach
- Scalanie, łączenie i dzielenie zgłoszeń
- Odkładanie z automatycznym wznowieniem
- Zapisane widoki dla każdego agenta
- Pola niestandardowe różnych typów

</td>
<td width="33%" valign="top">

**SLA i routing**
- Polityki SLA z godzinami pracy i świętami
- Eskalacja po naruszeniu, po czasie lub przy braku odpowiedzi
- Ponowne przypisanie, zmiana priorytetu lub działu
- Reguły automatycznego zamykania i przypisywania
- Routing według umiejętności
- Limity obciążenia agentów

</td>
<td width="33%" valign="top">

**E-mail**
- Poczta przychodząca z Postmark, Mailgun i AWS SES
- Dopasowanie zgłoszenia przez podpisany Reply-To
- Odpowiedzi przyjmowane tylko od zgłaszającego
- E-maile HTML z Twoim brandingiem przez Thymeleaf
- Wątki według Message-ID

</td>
</tr>
<tr>
<td width="33%" valign="top">

**Klienci**
- Publiczne API widżetu
- Dostęp gości do zgłoszeń przez token
- Ankiety CSAT z dostępem przez token
- Baza wiedzy z wyszukiwaniem i opiniami

</td>
<td width="33%" valign="top">

**Administracja i bezpieczeństwo**
- Role i uprawnienia
- Uwierzytelnianie dwuskładnikowe TOTP dla zespołu
- Logowanie przez SAML 2, OAuth 2 / OIDC i JWT
- Dziennik audytu każdej akcji
- Hashowane tokeny API

</td>
<td width="33%" valign="top">

**Rozszerzenia i integracje**
- Webhooki podpisane HMAC z ponawianiem
- Masowy import zgłoszeń
- Natywne wtyczki Java i wtyczki SDK
- WebSocket przez STOMP/SockJS (opcjonalnie)
- Wspólny interfejs Vue 3 + Inertia (opcjonalnie)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">Wszystkie funkcje szczegółowo</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## Funkcje

```bash
git clone https://github.com/escalated-dev/escalated-spring.git
cd escalated-spring
./gradlew publishToMavenLocal
```

Następnie dodaj `implementation("dev.escalated:escalated-spring:0.2.13")` z `mavenLocal()` do swojej aplikacji i ustaw `escalated.enabled=true`. Wymaga Java 17+ i Spring Boot 4.1. [Przewodnik na start](../getting-started.md) opisuje konfigurację, bazę danych i logowanie, a [strony Inertia](../ui.md) włączają wspólne ekrany przez `escalated.ui.enabled=true`.

## agi, załączniki, oś czasu aktywności

- [Wszystkie funkcje](../features.md) · [Pierwsze kroki](../getting-started.md) · [Strony Inertia](../ui.md)
- [Dostosowanie, lokalizacja i czas rzeczywisty](../customization.md) · [Architektura](../architecture.md) · [Endpointy API](../api-endpoints.md)
- [Kontrola dostępu](../access-control.md) · [Dostęp do zgłoszeń i routing](../ticket-access-and-routing.md) · [Pola niestandardowe](../custom-fields.md)
- [Poczta przychodząca](../inbound-email.md) · [Dostęp dla gości](../guest-access.md) · [Osadzanie widżetu](../embedding-the-widget.md)
- [Osadzanie panelu administracyjnego](../embedding-admin.md) · [Panel agenta](../agent-panel.md) · [Wtyczki](../plugins.md)
- [Newslettery](../newsletters.md) · [Rozwój](../development.md)

Escalated jest dostępny również dla [Laravel](https://github.com/escalated-dev/escalated-laravel), [Rails](https://github.com/escalated-dev/escalated-rails), [Django](https://github.com/escalated-dev/escalated-django), [NestJS](https://github.com/escalated-dev/escalated-nestjs), [AdonisJS](https://github.com/escalated-dev/escalated-adonis), [Symfony](https://github.com/escalated-dev/escalated-symfony), [Phoenix](https://github.com/escalated-dev/escalated-phoenix), [Go](https://github.com/escalated-dev/escalated-go), [.NET](https://github.com/escalated-dev/escalated-dotnet), [WordPress](https://github.com/escalated-dev/escalated-wordpress) i [Filament](https://github.com/escalated-dev/escalated-filament). Interfejs znajduje się w [escalated](https://github.com/escalated-dev/escalated).

## 

MIT. Copyright (c) 2026 Escalated.dev. Zobacz [LICENSE](../../LICENSE).
