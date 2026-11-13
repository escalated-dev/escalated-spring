<p align="center"><sub>
  <a href="README.ar.md">العربية</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="../../README.md">English</a> ·
  <a href="README.es.md">Español</a> ·
  <b>Français</b> ·
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
  Ajoutez un helpdesk complet à votre application Spring Boot.<br>
  Tickets, SLA, règles d'escalade, une base de connaissances et un widget de support, en une seule dépendance sur votre propre base de données.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>Site web</b></a> ·
  <a href="https://escalated.dev/docs"><b>Documentation</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>Journal des modifications</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="La boîte de réception des agents d'Escalated : vues enregistrées avec compteurs, comptes à rebours SLA et priorités sur chaque ticket" width="100%">
</picture>

## Ce qu'il fait

<table>
  <tr>
    <td width="40%">
      <h3>Une boîte de réception où vos agents peuvent travailler toute la journée</h3>
      Conversation, notes internes, réponses prédéfinies et minuteurs SLA sur un seul écran. Les tickets arrivent aussi par e-mail via Postmark, Mailgun ou AWS SES.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="Un ticket ouvert dans l'espace de travail de l'agent avec l'éditeur de réponse">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="La page de rapports de l'administration avec les temps de réponse, le CSAT et les dépassements de SLA">
      </picture>
    </td>
    <td width="40%">
      <h3>Des SLA et des règles d'escalade bien visibles</h3>
      Des objectifs de réponse et de résolution avec heures ouvrées et jours fériés, des règles qui escaladent ou réattribuent en cas de dépassement, et des rapports SLA.
    </td>
  </tr>
</table>

## Fonctionnalités

<table>
<tr>
<td width="33%" valign="top">

**Tickets et boîte de réception**
- Statuts, priorités et attribution
- Macros et réponses prédéfinies
- Conversations annexes sur les tickets
- Fusionner, lier et scinder des tickets
- Mise en attente avec réveil automatique
- Vues enregistrées par agent
- Champs personnalisés de plusieurs types

</td>
<td width="33%" valign="top">

**SLA et routage**
- Politiques SLA avec heures ouvrées et jours fériés
- Escalade en cas de dépassement, d'ancienneté ou d'absence de réponse
- Réattribuer, changer la priorité ou le département
- Règles de fermeture et d'attribution automatiques
- Routage par compétences
- Limites de charge par agent

</td>
<td width="33%" valign="top">

**E-mail**
- Entrant via Postmark, Mailgun et AWS SES
- Rattachement au ticket par Reply-To signé
- Réponses acceptées uniquement du demandeur
- E-mails HTML à vos couleurs avec Thymeleaf
- Fils de discussion par Message-ID

</td>
</tr>
<tr>
<td width="33%" valign="top">

**Clients**
- API publique du widget
- Accès invité aux tickets par jeton
- Enquêtes CSAT avec accès par jeton
- Base de connaissances avec recherche et avis

</td>
<td width="33%" valign="top">

**Administration et sécurité**
- Rôles et permissions
- Double authentification TOTP pour l'équipe
- Connexion SAML 2, OAuth 2 / OIDC et JWT
- Journal d'audit pour chaque action
- Jetons d'API hachés

</td>
<td width="33%" valign="top">

**Étendre et intégrer**
- Webhooks signés HMAC avec nouvelles tentatives
- Import de tickets en masse
- Plugins Java natifs et via le SDK
- WebSocket via STOMP/SockJS (optionnel)
- Interface partagée Vue 3 + Inertia (optionnelle)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">Toutes les fonctionnalités en détail</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## Fonctionnalités

```bash
git clone https://github.com/escalated-dev/escalated-spring.git
cd escalated-spring
./gradlew publishToMavenLocal
```

Ajoutez ensuite `implementation("dev.escalated:escalated-spring:0.2.13")` avec `mavenLocal()` à votre application et définissez `escalated.enabled=true`. Nécessite Java 17+ et Spring Boot 4.1. Le [guide de démarrage](../getting-started.md) couvre la configuration, la base de données et la connexion, et [les pages Inertia](../ui.md) activent les écrans partagés avec `escalated.ui.enabled=true`.

## tiquettes, pièces jointes, historique d'activité

- [Toutes les fonctionnalités](../features.md) · [Premiers pas](../getting-started.md) · [Les pages Inertia](../ui.md)
- [Personnalisation, localisation et temps réel](../customization.md) · [Architecture](../architecture.md) · [Endpoints de l'API](../api-endpoints.md)
- [Contrôle d'accès](../access-control.md) · [Accès aux tickets et routage](../ticket-access-and-routing.md) · [Champs personnalisés](../custom-fields.md)
- [E-mails entrants](../inbound-email.md) · [Accès invité](../guest-access.md) · [Intégrer le widget](../embedding-the-widget.md)
- [Intégrer l'administration](../embedding-admin.md) · [Panneau agent](../agent-panel.md) · [Plugins](../plugins.md)
- [Newsletters](../newsletters.md) · [Développement](../development.md)

Escalated est aussi disponible pour [Laravel](https://github.com/escalated-dev/escalated-laravel), [Rails](https://github.com/escalated-dev/escalated-rails), [Django](https://github.com/escalated-dev/escalated-django), [NestJS](https://github.com/escalated-dev/escalated-nestjs), [AdonisJS](https://github.com/escalated-dev/escalated-adonis), [Symfony](https://github.com/escalated-dev/escalated-symfony), [Phoenix](https://github.com/escalated-dev/escalated-phoenix), [Go](https://github.com/escalated-dev/escalated-go), [.NET](https://github.com/escalated-dev/escalated-dotnet), [WordPress](https://github.com/escalated-dev/escalated-wordpress) et [Filament](https://github.com/escalated-dev/escalated-filament). L'interface se trouve dans [escalated](https://github.com/escalated-dev/escalated).

## 

MIT. Copyright (c) 2026 Escalated.dev. Voir [LICENSE](../../LICENSE).
