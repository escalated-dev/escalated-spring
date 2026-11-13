<p align="center"><sub>
  <a href="README.ar.md">العربية</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="../../README.md">English</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.ja.md">日本語</a> ·
  <a href="README.ko.md">한국어</a> ·
  <b>Nederlands</b> ·
  <a href="README.pl.md">Polski</a> ·
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
  Voeg een complete helpdesk toe aan je Spring Boot-app.<br>
  Tickets, SLA's, escalatieregels, een kennisbank en een supportwidget, als één dependency op je eigen database.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>Website</b></a> ·
  <a href="https://escalated.dev/docs"><b>Documentatie</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>Wijzigingslog</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="De agent-inbox van Escalated: opgeslagen weergaven met aantallen, SLA-afteltimers en prioriteiten op elk ticket" width="100%">
</picture>

## Wat het doet

<table>
  <tr>
    <td width="40%">
      <h3>Een inbox waarin je agents de hele dag kunnen werken</h3>
      Gesprek, interne notities, standaardantwoorden en SLA-timers op één scherm. Tickets komen ook binnen per e-mail via Postmark, Mailgun of AWS SES.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="Een geopend ticket in de werkruimte van de agent met de antwoordeditor">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="De rapportagepagina voor beheerders met reactietijden, CSAT en SLA-overschrijdingen">
      </picture>
    </td>
    <td width="40%">
      <h3>SLA's en escalatieregels die je kunt zien</h3>
      Reactie- en oplosdoelen met kantooruren en feestdagen, regels die escaleren of opnieuw toewijzen bij een overschrijding, en SLA-rapporten.
    </td>
  </tr>
</table>

## Functies

<table>
<tr>
<td width="33%" valign="top">

**Tickets en inbox**
- Statussen, prioriteiten en toewijzing
- Macro's en standaardantwoorden
- Zijgesprekken bij tickets
- Tickets samenvoegen, koppelen en splitsen
- Uitstellen met automatisch terugkomen
- Opgeslagen weergaven per agent
- Aangepaste velden van verschillende typen

</td>
<td width="33%" valign="top">

**SLA's en routering**
- SLA-beleid met kantooruren en feestdagen
- Escaleren bij overschrijding, leeftijd of geen reactie
- Opnieuw toewijzen, prioriteit wijzigen of naar een andere afdeling
- Regels voor automatisch sluiten en toewijzen
- Routering op basis van vaardigheden
- Werklastlimieten per agent

</td>
<td width="33%" valign="top">

**E-mail**
- Inkomend via Postmark, Mailgun en AWS SES
- Ticketkoppeling via ondertekend Reply-To
- Antwoorden alleen geaccepteerd van de aanvrager
- HTML-e-mails in eigen huisstijl met Thymeleaf
- Threading via Message-ID

</td>
</tr>
<tr>
<td width="33%" valign="top">

**Klanten**
- Publieke widget-API
- Gasttoegang tot tickets via token
- CSAT-enquêtes met toegang via token
- Kennisbank met zoeken en feedback

</td>
<td width="33%" valign="top">

**Beheer en beveiliging**
- Rollen en rechten
- TOTP-tweestapsverificatie voor medewerkers
- Inloggen met SAML 2, OAuth 2 / OIDC en JWT
- Auditlog voor elke actie
- Gehashte API-tokens

</td>
<td width="33%" valign="top">

**Uitbreiden en integreren**
- HMAC-ondertekende webhooks met nieuwe pogingen
- Tickets in bulk importeren
- Native Java- en SDK-plugins
- WebSocket via STOMP/SockJS (optioneel)
- Gedeelde Vue 3 + Inertia-UI (optioneel)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">Alle functies in detail</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## Functies

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

Voeg daarna `implementation("dev.escalated:escalated-spring:0.2.13")` met `mavenLocal()` toe aan je app en stel `escalated.enabled=true` in. Vereist Java 17+ en Spring Boot 4.1. De [handleiding om te beginnen](../getting-started.md) behandelt configuratie, de database en inloggen, en [de Inertia-pagina's](../ui.md) zetten de gedeelde schermen aan met `escalated.ui.enabled=true`.

## ags, bijlagen, activiteitentijdlijn

- [Alle functies](../features.md) · [Aan de slag](../getting-started.md) · [De Inertia-pagina's](../ui.md)
- [Aanpassing, lokalisatie en realtime](../customization.md) · [Architectuur](../architecture.md) · [API-endpoints](../api-endpoints.md)
- [Toegangsbeheer](../access-control.md) · [Tickettoegang en routering](../ticket-access-and-routing.md) · [Aangepaste velden](../custom-fields.md)
- [Inkomende e-mail](../inbound-email.md) · [Gasttoegang](../guest-access.md) · [De widget insluiten](../embedding-the-widget.md)
- [De beheeromgeving insluiten](../embedding-admin.md) · [Agentpaneel](../agent-panel.md) · [Plugins](../plugins.md)
- [Nieuwsbrieven](../newsletters.md) · [Ontwikkeling](../development.md)

Escalated is ook beschikbaar voor [Laravel](https://github.com/escalated-dev/escalated-laravel), [Rails](https://github.com/escalated-dev/escalated-rails), [Django](https://github.com/escalated-dev/escalated-django), [NestJS](https://github.com/escalated-dev/escalated-nestjs), [AdonisJS](https://github.com/escalated-dev/escalated-adonis), [Symfony](https://github.com/escalated-dev/escalated-symfony), [Phoenix](https://github.com/escalated-dev/escalated-phoenix), [Go](https://github.com/escalated-dev/escalated-go), [.NET](https://github.com/escalated-dev/escalated-dotnet), [WordPress](https://github.com/escalated-dev/escalated-wordpress) en [Filament](https://github.com/escalated-dev/escalated-filament). De UI staat in [escalated](https://github.com/escalated-dev/escalated).

## 

MIT. Copyright (c) 2026 Escalated.dev. Zie [LICENSE](../../LICENSE).
