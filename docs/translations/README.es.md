<p align="center"><sub>
  <a href="README.ar.md">العربية</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="../../README.md">English</a> ·
  <b>Español</b> ·
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
  Añade un helpdesk completo a tu aplicación Spring Boot.<br>
  Tickets, SLA, reglas de escalado, una base de conocimiento y un widget de soporte, como una sola dependencia sobre tu propia base de datos.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>Sitio web</b></a> ·
  <a href="https://escalated.dev/docs"><b>Documentación</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>Registro de cambios</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="La bandeja de entrada de agentes de Escalated: vistas guardadas con contadores, cuentas atrás de SLA y prioridades en cada ticket" width="100%">
</picture>

## Qué hace

<table>
  <tr>
    <td width="40%">
      <h3>Una bandeja de entrada en la que tus agentes pueden trabajar todo el día</h3>
      Conversación, notas internas, respuestas predefinidas y temporizadores de SLA en una sola pantalla. Los tickets también llegan por correo electrónico a través de Postmark, Mailgun o AWS SES.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="Un ticket abierto en el espacio de trabajo del agente con el editor de respuestas">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="La página de informes de administración con tiempos de respuesta, CSAT e incumplimientos de SLA">
      </picture>
    </td>
    <td width="40%">
      <h3>SLA y reglas de escalado que puedes ver</h3>
      Objetivos de respuesta y resolución con horario laboral y días festivos, reglas que escalan o reasignan cuando se incumple un objetivo, e informes de SLA.
    </td>
  </tr>
</table>

## Funciones

<table>
<tr>
<td width="33%" valign="top">

**Tickets y bandeja de entrada**
- Estados, prioridades y asignación
- Macros y respuestas predefinidas
- Conversaciones paralelas en los tickets
- Fusionar, vincular y dividir tickets
- Posponer con reactivación automática
- Vistas guardadas por agente
- Campos personalizados de varios tipos

</td>
<td width="33%" valign="top">

**SLA y enrutamiento**
- Políticas de SLA con horario laboral y días festivos
- Escalado por incumplimiento, antigüedad o falta de respuesta
- Reasignar, cambiar la prioridad o mover de departamento
- Reglas de cierre y asignación automáticos
- Enrutamiento por habilidades
- Límites de carga de trabajo por agente

</td>
<td width="33%" valign="top">

**Correo electrónico**
- Entrada desde Postmark, Mailgun y AWS SES
- Identificación del ticket mediante Reply-To firmado
- Respuestas aceptadas solo del solicitante
- Correos HTML con tu marca usando Thymeleaf
- Hilos mediante Message-ID

</td>
</tr>
<tr>
<td width="33%" valign="top">

**Clientes**
- API pública del widget
- Acceso de invitados a tickets mediante token
- Encuestas CSAT con acceso por token
- Base de conocimiento con búsqueda y valoraciones

</td>
<td width="33%" valign="top">

**Administración y seguridad**
- Roles y permisos
- Doble factor TOTP para el personal
- Inicio de sesión con SAML 2, OAuth 2 / OIDC y JWT
- Registro de auditoría de cada acción
- Tokens de API con hash

</td>
<td width="33%" valign="top">

**Ampliar e integrar**
- Webhooks firmados con HMAC y con reintentos
- Importación masiva de tickets
- Plugins nativos en Java y con el SDK
- WebSocket sobre STOMP/SockJS (opcional)
- Interfaz compartida Vue 3 + Inertia (opcional)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">Todas las funciones en detalle</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## Funciones

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

Después añade `implementation("dev.escalated:escalated-spring:0.2.13")` con `mavenLocal()` a tu aplicación y define `escalated.enabled=true`. Requiere Java 17+ y Spring Boot 4.1. La [guía de primeros pasos](../getting-started.md) explica la configuración, la base de datos y el inicio de sesión, y [las páginas de Inertia](../ui.md) activan las pantallas compartidas con `escalated.ui.enabled=true`.

## tiquetas, adjuntos, cronología de actividad

- [Todas las funciones](../features.md) · [Primeros pasos](../getting-started.md) · [Las páginas de Inertia](../ui.md)
- [Personalización, localización y tiempo real](../customization.md) · [Arquitectura](../architecture.md) · [Endpoints de la API](../api-endpoints.md)
- [Control de acceso](../access-control.md) · [Acceso a tickets y enrutamiento](../ticket-access-and-routing.md) · [Campos personalizados](../custom-fields.md)
- [Correo entrante](../inbound-email.md) · [Acceso de invitados](../guest-access.md) · [Insertar el widget](../embedding-the-widget.md)
- [Insertar la administración](../embedding-admin.md) · [Panel del agente](../agent-panel.md) · [Plugins](../plugins.md)
- [Boletines](../newsletters.md) · [Desarrollo](../development.md)

Escalated también está disponible para [Laravel](https://github.com/escalated-dev/escalated-laravel), [Rails](https://github.com/escalated-dev/escalated-rails), [Django](https://github.com/escalated-dev/escalated-django), [NestJS](https://github.com/escalated-dev/escalated-nestjs), [AdonisJS](https://github.com/escalated-dev/escalated-adonis), [Symfony](https://github.com/escalated-dev/escalated-symfony), [Phoenix](https://github.com/escalated-dev/escalated-phoenix), [Go](https://github.com/escalated-dev/escalated-go), [.NET](https://github.com/escalated-dev/escalated-dotnet), [WordPress](https://github.com/escalated-dev/escalated-wordpress) y [Filament](https://github.com/escalated-dev/escalated-filament). La interfaz está en [escalated](https://github.com/escalated-dev/escalated).

## 

MIT. Copyright (c) 2026 Escalated.dev. Consulta [LICENSE](../../LICENSE).
