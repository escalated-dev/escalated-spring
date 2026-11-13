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
  <a href="README.pl.md">Polski</a> ·
  <a href="README.pt-BR.md">Português (BR)</a> ·
  <b>Русский</b> ·
  <a href="README.tr.md">Türkçe</a> ·
  <a href="README.zh-CN.md">简体中文</a>
</sub></p>

<p align="center">
  <a href="https://escalated.dev"><img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/logo.png" width="72" height="72" alt="Escalated"></a>
</p>

<h1 align="center">Escalated for Spring</h1>

<p align="center">
  Добавьте полноценный хелпдеск в ваше приложение на Spring Boot.<br>
  Тикеты, SLA, правила эскалации, база знаний и виджет поддержки, одной зависимостью на вашей собственной базе данных.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>Сайт</b></a> ·
  <a href="https://escalated.dev/docs"><b>Документация</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>Список изменений</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="Входящие агента в Escalated: сохранённые представления со счётчиками, обратный отсчёт SLA и приоритет у каждого тикета" width="100%">
</picture>

## Что он делает

<table>
  <tr>
    <td width="40%">
      <h3>Входящие, в которых агентам удобно работать весь день</h3>
      Переписка, внутренние заметки, шаблоны ответов и таймеры SLA на одном экране. Тикеты также приходят по электронной почте через Postmark, Mailgun или AWS SES.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="Тикет, открытый в рабочем пространстве агента, с редактором ответа">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="Страница отчётов в админке со временем ответа, CSAT и нарушениями SLA">
      </picture>
    </td>
    <td width="40%">
      <h3>SLA и правила эскалации у вас на виду</h3>
      Цели по времени ответа и решения с учётом рабочих часов и праздников, правила, которые эскалируют или переназначают тикет при нарушении, и отчёты по SLA.
    </td>
  </tr>
</table>

## Возможности

<table>
<tr>
<td width="33%" valign="top">

**Тикеты и входящие**
- Статусы, приоритеты и назначение
- Макросы и шаблоны ответов
- Побочные переписки в тикетах
- Объединение, связывание и разделение тикетов
- Откладывание с автоматическим возвратом
- Сохранённые представления для каждого агента
- Пользовательские поля нескольких типов

</td>
<td width="33%" valign="top">

**SLA и маршрутизация**
- Политики SLA с рабочими часами и праздниками
- Эскалация при нарушении, по возрасту тикета или без ответа
- Переназначение, смена приоритета или отдела
- Правила автозакрытия и автоназначения
- Маршрутизация по навыкам
- Ограничения нагрузки на агентов

</td>
<td width="33%" valign="top">

**Почта**
- Входящая почта из Postmark, Mailgun и AWS SES
- Привязка к тикету через подписанный Reply-To
- Ответы принимаются только от автора обращения
- Брендированные HTML-письма на Thymeleaf
- Цепочки писем по Message-ID

</td>
</tr>
<tr>
<td width="33%" valign="top">

**Клиенты**
- Публичный API виджета
- Гостевой доступ к тикетам по токену
- Опросы CSAT с доступом по токену
- База знаний с поиском и отзывами

</td>
<td width="33%" valign="top">

**Администрирование и безопасность**
- Роли и права доступа
- Двухфакторная аутентификация TOTP для сотрудников
- Вход через SAML 2, OAuth 2 / OIDC и JWT
- Журнал аудита для каждого действия
- Хешированные API-токены

</td>
<td width="33%" valign="top">

**Расширение и интеграции**
- Вебхуки с подписью HMAC и повторными попытками
- Массовый импорт тикетов
- Нативные плагины на Java и плагины SDK
- WebSocket через STOMP/SockJS (по желанию)
- Общий интерфейс Vue 3 + Inertia (по желанию)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">Все возможности подробно</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## Возможности

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

Затем добавьте `implementation("dev.escalated:escalated-spring:0.2.13")` вместе с `mavenLocal()` в ваше приложение и задайте `escalated.enabled=true`. Требуются Java 17+ и Spring Boot 4.1. В [руководстве по началу работы](../getting-started.md) описаны настройка, база данных и вход, а [страницы Inertia](../ui.md) включают общие экраны через `escalated.ui.enabled=true`.

## еги, вложения, история действий

- [Все возможности](../features.md) · [Начало работы](../getting-started.md) · [Страницы Inertia](../ui.md)
- [Настройка, локализация и работа в реальном времени](../customization.md) · [Архитектура](../architecture.md) · [API-эндпоинты](../api-endpoints.md)
- [Управление доступом](../access-control.md) · [Доступ к тикетам и маршрутизация](../ticket-access-and-routing.md) · [Пользовательские поля](../custom-fields.md)
- [Входящая почта](../inbound-email.md) · [Гостевой доступ](../guest-access.md) · [Встраивание виджета](../embedding-the-widget.md)
- [Встраивание админки](../embedding-admin.md) · [Панель агента](../agent-panel.md) · [Плагины](../plugins.md)
- [Рассылки](../newsletters.md) · [Разработка](../development.md)

Escalated также доступен для [Laravel](https://github.com/escalated-dev/escalated-laravel), [Rails](https://github.com/escalated-dev/escalated-rails), [Django](https://github.com/escalated-dev/escalated-django), [NestJS](https://github.com/escalated-dev/escalated-nestjs), [AdonisJS](https://github.com/escalated-dev/escalated-adonis), [Symfony](https://github.com/escalated-dev/escalated-symfony), [Phoenix](https://github.com/escalated-dev/escalated-phoenix), [Go](https://github.com/escalated-dev/escalated-go), [.NET](https://github.com/escalated-dev/escalated-dotnet), [WordPress](https://github.com/escalated-dev/escalated-wordpress) и [Filament](https://github.com/escalated-dev/escalated-filament). Интерфейс находится в [escalated](https://github.com/escalated-dev/escalated).

## 

MIT. Copyright (c) 2026 Escalated.dev. См. [LICENSE](../../LICENSE).
