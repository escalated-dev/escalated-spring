<p align="center"><sub>
  <a href="docs/translations/README.ar.md">العربية</a> ·
  <a href="docs/translations/README.de.md">Deutsch</a> ·
  <b>English</b> ·
  <a href="docs/translations/README.es.md">Español</a> ·
  <a href="docs/translations/README.fr.md">Français</a> ·
  <a href="docs/translations/README.it.md">Italiano</a> ·
  <a href="docs/translations/README.ja.md">日本語</a> ·
  <a href="docs/translations/README.ko.md">한국어</a> ·
  <a href="docs/translations/README.nl.md">Nederlands</a> ·
  <a href="docs/translations/README.pl.md">Polski</a> ·
  <a href="docs/translations/README.pt-BR.md">Português (BR)</a> ·
  <a href="docs/translations/README.ru.md">Русский</a> ·
  <a href="docs/translations/README.tr.md">Türkçe</a> ·
  <a href="docs/translations/README.zh-CN.md">简体中文</a>
</sub></p>

<p align="center">
  <a href="https://escalated.dev"><img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/logo.png" width="72" height="72" alt="Escalated"></a>
</p>

<h1 align="center">Escalated for Spring</h1>

<p align="center">
  Add a complete helpdesk to your Spring Boot app.<br>
  Tickets, SLAs, escalation rules, a knowledge base and a support widget, as one dependency on your own database.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>Website</b></a> ·
  <a href="https://escalated.dev/docs"><b>Docs</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="CHANGELOG.md"><b>Changelog</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="The Escalated agent inbox: saved views with counts, SLA countdowns and priorities on every ticket" width="100%">
</picture>

## What it does

<table>
  <tr>
    <td width="40%">
      <h3>An inbox your agents can work in all day</h3>
      Conversation, internal notes, canned replies and SLA timers on one screen. Tickets also arrive by email through Postmark, Mailgun or AWS SES.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="A ticket open in the agent workspace with the reply composer">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="The admin reports page with response times, CSAT and SLA breaches">
      </picture>
    </td>
    <td width="40%">
      <h3>SLAs and escalation rules you can see</h3>
      Response and resolution targets with business hours and holidays, rules that escalate or reassign on a breach, and SLA reports.
    </td>
  </tr>
</table>

## Features

<table>
<tr>
<td width="33%" valign="top">

**Tickets and inbox**
- Statuses, priorities and assignment
- Macros and canned responses
- Side conversations on tickets
- Merge, link and split tickets
- Snooze with automatic wake-up
- Saved views per agent
- Custom fields of several types

</td>
<td width="33%" valign="top">

**SLAs and routing**
- SLA policies with business hours and holidays
- Escalate on breach, age or no response
- Reassign, reprioritize or move department
- Auto-close and auto-assign rules
- Skill-based routing
- Agent workload limits

</td>
<td width="33%" valign="top">

**Email**
- Inbound from Postmark, Mailgun and AWS SES
- Signed Reply-To ticket resolution
- Replies accepted only from the requester
- Branded HTML emails with Thymeleaf
- Message-ID threading

</td>
</tr>
<tr>
<td width="33%" valign="top">

**Customers**
- Public widget API
- Guest ticket access by token
- CSAT surveys with token access
- Knowledge base with search and feedback

</td>
<td width="33%" valign="top">

**Admin and security**
- Roles and permissions
- TOTP two-factor for staff
- SAML 2, OAuth 2 / OIDC and JWT sign-in
- Audit trail for every action
- Hashed API tokens

</td>
<td width="33%" valign="top">

**Extend and integrate**
- HMAC-signed webhooks with retries
- Bulk ticket import
- Native Java and SDK plugins
- WebSocket over STOMP/SockJS (opt-in)
- Shared Vue 3 + Inertia UI (opt-in)

</td>
</tr>
</table>

<p align="center"><sub><a href="docs/features.md">Every feature in detail</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## Quick start

```bash
git clone https://github.com/escalated-dev/escalated-spring.git
cd escalated-spring
./gradlew publishToMavenLocal
```

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.escalated:escalated-spring:0.2.13")
}
```

Add that to your app's `build.gradle.kts` and set `escalated.enabled=true`. Needs Java 17+ and Spring Boot 4.1. The [getting started guide](docs/getting-started.md) covers configuration, the database and sign-in, and [the Inertia pages](docs/ui.md) turn on the shared screens with `escalated.ui.enabled=true`.

## Learn more

- [All features](docs/features.md) · [Getting started](docs/getting-started.md) · [The Inertia pages](docs/ui.md)
- [Customization, localization and real-time](docs/customization.md) · [Architecture](docs/architecture.md) · [API endpoints](docs/api-endpoints.md)
- [Access control](docs/access-control.md) · [Ticket access and routing](docs/ticket-access-and-routing.md) · [Custom fields](docs/custom-fields.md)
- [Inbound email](docs/inbound-email.md) · [Guest access](docs/guest-access.md) · [Embedding the widget](docs/embedding-the-widget.md)
- [Embedding the admin](docs/embedding-admin.md) · [Agent panel](docs/agent-panel.md) · [Plugins](docs/plugins.md)
- [Newsletters](docs/newsletters.md) · [Development](docs/development.md)

Escalated is also available for [Laravel](https://github.com/escalated-dev/escalated-laravel), [Rails](https://github.com/escalated-dev/escalated-rails), [Django](https://github.com/escalated-dev/escalated-django), [NestJS](https://github.com/escalated-dev/escalated-nestjs), [AdonisJS](https://github.com/escalated-dev/escalated-adonis), [Symfony](https://github.com/escalated-dev/escalated-symfony), [Phoenix](https://github.com/escalated-dev/escalated-phoenix), [Go](https://github.com/escalated-dev/escalated-go), [.NET](https://github.com/escalated-dev/escalated-dotnet), [WordPress](https://github.com/escalated-dev/escalated-wordpress) and [Filament](https://github.com/escalated-dev/escalated-filament). The UI lives in [escalated](https://github.com/escalated-dev/escalated).

## License

MIT. Copyright (c) 2026 Escalated.dev. See [LICENSE](LICENSE).
