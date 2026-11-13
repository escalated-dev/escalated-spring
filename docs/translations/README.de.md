<p align="center"><sub>
  <a href="README.ar.md">العربية</a> ·
  <b>Deutsch</b> ·
  <a href="../../README.md">English</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.ja.md">日本語</a> ·
  <a href="README.ko.md">한국어</a> ·
  <a href="README.nl.md">Nederlands</a> ·
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
  Füge deiner Spring-Boot-App einen vollständigen Helpdesk hinzu.<br>
  Tickets, SLAs, Eskalationsregeln, eine Wissensdatenbank und ein Support-Widget, als eine Abhängigkeit auf deiner eigenen Datenbank.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>Website</b></a> ·
  <a href="https://escalated.dev/docs"><b>Dokumentation</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>Änderungsprotokoll</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="Der Agenten-Posteingang von Escalated: gespeicherte Ansichten mit Zählern, SLA-Countdowns und Prioritäten an jedem Ticket" width="100%">
</picture>

## Was es kann

<table>
  <tr>
    <td width="40%">
      <h3>Ein Posteingang, in dem deine Agenten den ganzen Tag arbeiten können</h3>
      Konversation, interne Notizen, Textbausteine und SLA-Timer auf einem Bildschirm. Tickets kommen auch per E-Mail über Postmark, Mailgun oder AWS SES an.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="Ein geöffnetes Ticket im Agenten-Arbeitsbereich mit dem Antwort-Editor">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="Die Berichtsseite im Adminbereich mit Antwortzeiten, CSAT und SLA-Verstößen">
      </picture>
    </td>
    <td width="40%">
      <h3>SLAs und Eskalationsregeln, die du im Blick hast</h3>
      Reaktions- und Lösungsziele mit Geschäftszeiten und Feiertagen, Regeln, die bei einem Verstoß eskalieren oder neu zuweisen, und SLA-Berichte.
    </td>
  </tr>
</table>

## Funktionen

<table>
<tr>
<td width="33%" valign="top">

**Tickets und Posteingang**
- Status, Prioritäten und Zuweisung
- Makros und Textbausteine
- Nebenkonversationen an Tickets
- Tickets zusammenführen, verknüpfen und aufteilen
- Zurückstellen mit automatischem Wecken
- Gespeicherte Ansichten pro Agent
- Benutzerdefinierte Felder verschiedener Typen

</td>
<td width="33%" valign="top">

**SLAs und Routing**
- SLA-Richtlinien mit Geschäftszeiten und Feiertagen
- Eskalation bei Verstoß, Alter oder fehlender Antwort
- Neu zuweisen, Priorität ändern oder Abteilung wechseln
- Regeln für automatisches Schließen und Zuweisen
- Kompetenzbasiertes Routing
- Arbeitslastgrenzen für Agenten

</td>
<td width="33%" valign="top">

**E-Mail**
- Eingehend über Postmark, Mailgun und AWS SES
- Ticketzuordnung über signiertes Reply-To
- Antworten werden nur vom Anfragenden angenommen
- HTML-E-Mails im eigenen Design mit Thymeleaf
- Threading über Message-ID

</td>
</tr>
<tr>
<td width="33%" valign="top">

**Kunden**
- Öffentliche Widget-API
- Gastzugriff auf Tickets per Token
- CSAT-Umfragen mit Token-Zugriff
- Wissensdatenbank mit Suche und Feedback

</td>
<td width="33%" valign="top">

**Verwaltung und Sicherheit**
- Rollen und Berechtigungen
- TOTP-Zwei-Faktor für Mitarbeitende
- Anmeldung per SAML 2, OAuth 2 / OIDC und JWT
- Audit-Protokoll für jede Aktion
- Gehashte API-Tokens

</td>
<td width="33%" valign="top">

**Erweitern und integrieren**
- HMAC-signierte Webhooks mit Wiederholungen
- Massenimport von Tickets
- Native Java- und SDK-Plugins
- WebSocket über STOMP/SockJS (optional)
- Gemeinsame Vue 3 + Inertia-Oberfläche (optional)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">Alle Funktionen im Detail</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## Funktionen

```bash
git clone https://github.com/escalated-dev/escalated-spring.git
cd escalated-spring
./gradlew publishToMavenLocal
```

Füge danach `implementation("dev.escalated:escalated-spring:0.2.13")` mit `mavenLocal()` zu deiner App hinzu und setze `escalated.enabled=true`. Voraussetzungen: Java 17+ und Spring Boot 4.1. Die [Einstiegsanleitung](../getting-started.md) erklärt Konfiguration, Datenbank und Anmeldung, und [die Inertia-Seiten](../ui.md) schalten mit `escalated.ui.enabled=true` die gemeinsamen Bildschirme ein.

## ags, Anhänge, Aktivitätsverlauf

- [Alle Funktionen](../features.md) · [Erste Schritte](../getting-started.md) · [Die Inertia-Seiten](../ui.md)
- [Anpassung, Lokalisierung und Echtzeit](../customization.md) · [Architektur](../architecture.md) · [API-Endpunkte](../api-endpoints.md)
- [Zugriffskontrolle](../access-control.md) · [Ticketzugriff und Routing](../ticket-access-and-routing.md) · [Benutzerdefinierte Felder](../custom-fields.md)
- [Eingehende E-Mails](../inbound-email.md) · [Gastzugang](../guest-access.md) · [Widget einbetten](../embedding-the-widget.md)
- [Adminbereich einbetten](../embedding-admin.md) · [Agenten-Panel](../agent-panel.md) · [Plugins](../plugins.md)
- [Newsletter](../newsletters.md) · [Entwicklung](../development.md)

Escalated gibt es auch für [Laravel](https://github.com/escalated-dev/escalated-laravel), [Rails](https://github.com/escalated-dev/escalated-rails), [Django](https://github.com/escalated-dev/escalated-django), [NestJS](https://github.com/escalated-dev/escalated-nestjs), [AdonisJS](https://github.com/escalated-dev/escalated-adonis), [Symfony](https://github.com/escalated-dev/escalated-symfony), [Phoenix](https://github.com/escalated-dev/escalated-phoenix), [Go](https://github.com/escalated-dev/escalated-go), [.NET](https://github.com/escalated-dev/escalated-dotnet), [WordPress](https://github.com/escalated-dev/escalated-wordpress) und [Filament](https://github.com/escalated-dev/escalated-filament). Die Oberfläche liegt in [escalated](https://github.com/escalated-dev/escalated).

## 

MIT. Copyright (c) 2026 Escalated.dev. Siehe [LICENSE](../../LICENSE).
