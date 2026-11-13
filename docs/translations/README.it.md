<p align="center"><sub>
  <a href="README.ar.md">العربية</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="../../README.md">English</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.fr.md">Français</a> ·
  <b>Italiano</b> ·
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
  Aggiungi un helpdesk completo alla tua app Spring Boot.<br>
  Ticket, SLA, regole di escalation, una knowledge base e un widget di supporto, come un'unica dipendenza sul tuo database.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>Sito web</b></a> ·
  <a href="https://escalated.dev/docs"><b>Documentazione</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>Registro delle modifiche</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="La inbox degli agenti di Escalated: viste salvate con conteggi, conti alla rovescia SLA e priorità su ogni ticket" width="100%">
</picture>

## Cosa fa

<table>
  <tr>
    <td width="40%">
      <h3>Una inbox in cui i tuoi agenti possono lavorare tutto il giorno</h3>
      Conversazione, note interne, risposte predefinite e timer SLA in un'unica schermata. I ticket arrivano anche via email tramite Postmark, Mailgun o AWS SES.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="Un ticket aperto nell'area di lavoro dell'agente con l'editor di risposta">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="La pagina dei report di amministrazione con tempi di risposta, CSAT e violazioni degli SLA">
      </picture>
    </td>
    <td width="40%">
      <h3>SLA e regole di escalation sempre visibili</h3>
      Obiettivi di risposta e risoluzione con orari lavorativi e festività, regole che eseguono l'escalation o riassegnano in caso di violazione, e report SLA.
    </td>
  </tr>
</table>

## Funzionalità

<table>
<tr>
<td width="33%" valign="top">

**Ticket e inbox**
- Stati, priorità e assegnazione
- Macro e risposte predefinite
- Conversazioni laterali sui ticket
- Unire, collegare e dividere i ticket
- Posticipo con riattivazione automatica
- Viste salvate per agente
- Campi personalizzati di diversi tipi

</td>
<td width="33%" valign="top">

**SLA e instradamento**
- Policy SLA con orari lavorativi e festività
- Escalation per violazione, anzianità o mancata risposta
- Riassegnare, cambiare priorità o reparto
- Regole di chiusura e assegnazione automatiche
- Instradamento in base alle competenze
- Limiti di carico per agente

</td>
<td width="33%" valign="top">

**Email**
- In entrata da Postmark, Mailgun e AWS SES
- Abbinamento al ticket tramite Reply-To firmato
- Risposte accettate solo dal richiedente
- Email HTML con il tuo marchio tramite Thymeleaf
- Thread tramite Message-ID

</td>
</tr>
<tr>
<td width="33%" valign="top">

**Clienti**
- API pubblica del widget
- Accesso ospite ai ticket tramite token
- Sondaggi CSAT con accesso tramite token
- Knowledge base con ricerca e feedback

</td>
<td width="33%" valign="top">

**Amministrazione e sicurezza**
- Ruoli e permessi
- Autenticazione a due fattori TOTP per lo staff
- Accesso con SAML 2, OAuth 2 / OIDC e JWT
- Registro di audit per ogni azione
- Token API con hash

</td>
<td width="33%" valign="top">

**Estendere e integrare**
- Webhook firmati HMAC con nuovi tentativi
- Importazione massiva di ticket
- Plugin Java nativi e tramite SDK
- WebSocket su STOMP/SockJS (opzionale)
- Interfaccia condivisa Vue 3 + Inertia (opzionale)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">Tutte le funzionalità nel dettaglio</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## Funzionalità

```bash
git clone https://github.com/escalated-dev/escalated-spring.git
cd escalated-spring
./gradlew publishToMavenLocal
```

Poi aggiungi `implementation("dev.escalated:escalated-spring:0.2.13")` con `mavenLocal()` alla tua app e imposta `escalated.enabled=true`. Richiede Java 17+ e Spring Boot 4.1. La [guida introduttiva](../getting-started.md) spiega configurazione, database e accesso, e [le pagine Inertia](../ui.md) attivano le schermate condivise con `escalated.ui.enabled=true`.

## ag, allegati, cronologia delle attività

- [Tutte le funzionalità](../features.md) · [Per iniziare](../getting-started.md) · [Le pagine Inertia](../ui.md)
- [Personalizzazione, localizzazione e tempo reale](../customization.md) · [Architettura](../architecture.md) · [Endpoint API](../api-endpoints.md)
- [Controllo degli accessi](../access-control.md) · [Accesso ai ticket e instradamento](../ticket-access-and-routing.md) · [Campi personalizzati](../custom-fields.md)
- [Email in entrata](../inbound-email.md) · [Accesso ospite](../guest-access.md) · [Incorporare il widget](../embedding-the-widget.md)
- [Incorporare l'amministrazione](../embedding-admin.md) · [Pannello agente](../agent-panel.md) · [Plugin](../plugins.md)
- [Newsletter](../newsletters.md) · [Sviluppo](../development.md)

Escalated è disponibile anche per [Laravel](https://github.com/escalated-dev/escalated-laravel), [Rails](https://github.com/escalated-dev/escalated-rails), [Django](https://github.com/escalated-dev/escalated-django), [NestJS](https://github.com/escalated-dev/escalated-nestjs), [AdonisJS](https://github.com/escalated-dev/escalated-adonis), [Symfony](https://github.com/escalated-dev/escalated-symfony), [Phoenix](https://github.com/escalated-dev/escalated-phoenix), [Go](https://github.com/escalated-dev/escalated-go), [.NET](https://github.com/escalated-dev/escalated-dotnet), [WordPress](https://github.com/escalated-dev/escalated-wordpress) e [Filament](https://github.com/escalated-dev/escalated-filament). L'interfaccia si trova in [escalated](https://github.com/escalated-dev/escalated).

## 

MIT. Copyright (c) 2026 Escalated.dev. Vedi [LICENSE](../../LICENSE).
