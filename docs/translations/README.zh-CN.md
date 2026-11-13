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
  <a href="README.ru.md">Русский</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <b>简体中文</b>
</sub></p>

<p align="center">
  <a href="https://escalated.dev"><img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/logo.png" width="72" height="72" alt="Escalated"></a>
</p>

<h1 align="center">Escalated for Spring</h1>

<p align="center">
  为你的 Spring Boot 应用添加一套完整的帮助台。<br>
  工单、SLA、升级规则、知识库和支持小部件，作为一个依赖运行在你自己的数据库上。
</p>

<p align="center">
  <a href="https://escalated.dev"><b>官网</b></a> ·
  <a href="https://escalated.dev/docs"><b>文档</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>更新日志</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="Escalated 客服收件箱：带计数的已保存视图，每个工单上都有 SLA 倒计时和优先级" width="100%">
</picture>

## 它能做什么

<table>
  <tr>
    <td width="40%">
      <h3>客服可以整天使用的收件箱</h3>
      对话、内部备注、预设回复和 SLA 计时器都在同一个界面上。工单也可以通过 Postmark、Mailgun 或 AWS SES 以邮件形式进入。
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="在客服工作区中打开的工单和回复编辑器">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="管理后台的报表页面，显示响应时间、CSAT 和 SLA 违约情况">
      </picture>
    </td>
    <td width="40%">
      <h3>清晰可见的 SLA 和升级规则</h3>
      支持工作时间和节假日的响应与解决目标，违约时升级或重新分配的规则，以及 SLA 报表。
    </td>
  </tr>
</table>

## 功能

<table>
<tr>
<td width="33%" valign="top">

**工单与收件箱**
- 状态、优先级和分配
- 宏和预设回复
- 工单内的旁路对话
- 合并、关联和拆分工单
- 暂缓并自动唤醒
- 每个客服的已保存视图
- 多种类型的自定义字段

</td>
<td width="33%" valign="top">

**SLA 与路由**
- 支持工作时间和节假日的 SLA 策略
- 按违约、时长或无回复进行升级
- 重新分配、调整优先级或转移部门
- 自动关闭和自动分配规则
- 基于技能的路由
- 客服工作量上限

</td>
<td width="33%" valign="top">

**邮件**
- 通过 Postmark、Mailgun 和 AWS SES 接收邮件
- 通过签名的 Reply-To 识别工单
- 只接受请求人的回复
- 使用 Thymeleaf 的品牌化 HTML 邮件
- 基于 Message-ID 的邮件串联

</td>
</tr>
<tr>
<td width="33%" valign="top">

**客户**
- 公开的小部件 API
- 通过令牌访问访客工单
- 通过令牌访问的 CSAT 调查
- 支持搜索和反馈的知识库

</td>
<td width="33%" valign="top">

**管理与安全**
- 角色和权限
- 面向员工的 TOTP 双因素认证
- SAML 2、OAuth 2 / OIDC 和 JWT 登录
- 每个操作的审计记录
- 哈希存储的 API 令牌

</td>
<td width="33%" valign="top">

**扩展与集成**
- 带重试的 HMAC 签名 Webhook
- 批量导入工单
- 原生 Java 插件和 SDK 插件
- 基于 STOMP/SockJS 的 WebSocket（可选）
- 共享的 Vue 3 + Inertia 界面（可选）

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">查看全部功能详情</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## 功能列表

```bash
git clone https://github.com/escalated-dev/escalated-spring.git
cd escalated-spring
./gradlew publishToMavenLocal
```

然后在你的应用中添加 `implementation("dev.escalated:escalated-spring:0.2.13")` 和 `mavenLocal()`，并设置 `escalated.enabled=true`。需要 Java 17+ 和 Spring Boot 4.1。[入门指南](../getting-started.md)介绍了配置、数据库和登录，[Inertia 页面](../ui.md)说明如何用 `escalated.ui.enabled=true` 启用共享界面。

## 签、附件、活动时间线

- [全部功能](../features.md) · [入门](../getting-started.md) · [Inertia 页面](../ui.md)
- [自定义、本地化与实时更新](../customization.md) · [架构](../architecture.md) · [API 端点](../api-endpoints.md)
- [访问控制](../access-control.md) · [工单访问与路由](../ticket-access-and-routing.md) · [自定义字段](../custom-fields.md)
- [入站邮件](../inbound-email.md) · [访客访问](../guest-access.md) · [嵌入小部件](../embedding-the-widget.md)
- [嵌入管理后台](../embedding-admin.md) · [客服面板](../agent-panel.md) · [插件](../plugins.md)
- [邮件通讯](../newsletters.md) · [开发](../development.md)

Escalated 也提供 [Laravel](https://github.com/escalated-dev/escalated-laravel)、[Rails](https://github.com/escalated-dev/escalated-rails)、[Django](https://github.com/escalated-dev/escalated-django)、[NestJS](https://github.com/escalated-dev/escalated-nestjs)、[AdonisJS](https://github.com/escalated-dev/escalated-adonis)、[Symfony](https://github.com/escalated-dev/escalated-symfony)、[Phoenix](https://github.com/escalated-dev/escalated-phoenix)、[Go](https://github.com/escalated-dev/escalated-go)、[.NET](https://github.com/escalated-dev/escalated-dotnet)、[WordPress](https://github.com/escalated-dev/escalated-wordpress) 和 [Filament](https://github.com/escalated-dev/escalated-filament) 版本。界面代码位于 [escalated](https://github.com/escalated-dev/escalated)。

## 

MIT。Copyright (c) 2026 Escalated.dev。详见 [LICENSE](../../LICENSE)。
