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
  <b>Português (BR)</b> ·
  <a href="README.ru.md">Русский</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <a href="README.zh-CN.md">简体中文</a>
</sub></p>

<p align="center">
  <a href="https://escalated.dev"><img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/logo.png" width="72" height="72" alt="Escalated"></a>
</p>

<h1 align="center">Escalated for Spring</h1>

<p align="center">
  Adicione um helpdesk completo ao seu app Spring Boot.<br>
  Tickets, SLAs, regras de escalonamento, uma base de conhecimento e um widget de suporte, como uma única dependência no seu próprio banco de dados.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>Site</b></a> ·
  <a href="https://escalated.dev/docs"><b>Documentação</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>Histórico de mudanças</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="A caixa de entrada de agentes do Escalated: visualizações salvas com contagens, contagem regressiva de SLA e prioridades em cada ticket" width="100%">
</picture>

## O que ele faz

<table>
  <tr>
    <td width="40%">
      <h3>Uma caixa de entrada em que seus agentes conseguem trabalhar o dia todo</h3>
      Conversa, notas internas, respostas prontas e timers de SLA em uma única tela. Os tickets também chegam por e-mail via Postmark, Mailgun ou AWS SES.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="Um ticket aberto no espaço de trabalho do agente com o editor de resposta">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="A página de relatórios do admin com tempos de resposta, CSAT e violações de SLA">
      </picture>
    </td>
    <td width="40%">
      <h3>SLAs e regras de escalonamento que você consegue ver</h3>
      Metas de resposta e resolução com horário comercial e feriados, regras que escalonam ou reatribuem quando há violação e relatórios de SLA.
    </td>
  </tr>
</table>

## Recursos

<table>
<tr>
<td width="33%" valign="top">

**Tickets e caixa de entrada**
- Status, prioridades e atribuição
- Macros e respostas prontas
- Conversas paralelas nos tickets
- Mesclar, vincular e dividir tickets
- Adiar com reativação automática
- Visualizações salvas por agente
- Campos personalizados de vários tipos

</td>
<td width="33%" valign="top">

**SLAs e roteamento**
- Políticas de SLA com horário comercial e feriados
- Escalonamento por violação, idade ou falta de resposta
- Reatribuir, mudar a prioridade ou o departamento
- Regras de fechamento e atribuição automáticos
- Roteamento por habilidades
- Limites de carga de trabalho por agente

</td>
<td width="33%" valign="top">

**E-mail**
- Entrada via Postmark, Mailgun e AWS SES
- Identificação do ticket por Reply-To assinado
- Respostas aceitas apenas do solicitante
- E-mails HTML com a sua marca usando Thymeleaf
- Threads por Message-ID

</td>
</tr>
<tr>
<td width="33%" valign="top">

**Clientes**
- API pública do widget
- Acesso de convidados ao ticket por token
- Pesquisas de CSAT com acesso por token
- Base de conhecimento com busca e feedback

</td>
<td width="33%" valign="top">

**Admin e segurança**
- Papéis e permissões
- Autenticação em dois fatores TOTP para a equipe
- Login com SAML 2, OAuth 2 / OIDC e JWT
- Trilha de auditoria de cada ação
- Tokens de API com hash

</td>
<td width="33%" valign="top">

**Estender e integrar**
- Webhooks assinados com HMAC e novas tentativas
- Importação de tickets em massa
- Plugins nativos em Java e via SDK
- WebSocket sobre STOMP/SockJS (opcional)
- Interface compartilhada Vue 3 + Inertia (opcional)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">Todos os recursos em detalhes</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## Recursos

```bash
git clone https://github.com/escalated-dev/escalated-spring.git
cd escalated-spring
./gradlew publishToMavenLocal
```

Depois adicione `implementation("dev.escalated:escalated-spring:0.2.13")` com `mavenLocal()` ao seu app e defina `escalated.enabled=true`. Requer Java 17+ e Spring Boot 4.1. O [guia de primeiros passos](../getting-started.md) explica a configuração, o banco de dados e o login, e [as páginas Inertia](../ui.md) ativam as telas compartilhadas com `escalated.ui.enabled=true`.

## ags, anexos, linha do tempo de atividades

- [Todos os recursos](../features.md) · [Primeiros passos](../getting-started.md) · [As páginas Inertia](../ui.md)
- [Personalização, localização e tempo real](../customization.md) · [Arquitetura](../architecture.md) · [Endpoints da API](../api-endpoints.md)
- [Controle de acesso](../access-control.md) · [Acesso a tickets e roteamento](../ticket-access-and-routing.md) · [Campos personalizados](../custom-fields.md)
- [E-mail de entrada](../inbound-email.md) · [Acesso de convidados](../guest-access.md) · [Incorporando o widget](../embedding-the-widget.md)
- [Incorporando o admin](../embedding-admin.md) · [Painel do agente](../agent-panel.md) · [Plugins](../plugins.md)
- [Newsletters](../newsletters.md) · [Desenvolvimento](../development.md)

O Escalated também está disponível para [Laravel](https://github.com/escalated-dev/escalated-laravel), [Rails](https://github.com/escalated-dev/escalated-rails), [Django](https://github.com/escalated-dev/escalated-django), [NestJS](https://github.com/escalated-dev/escalated-nestjs), [AdonisJS](https://github.com/escalated-dev/escalated-adonis), [Symfony](https://github.com/escalated-dev/escalated-symfony), [Phoenix](https://github.com/escalated-dev/escalated-phoenix), [Go](https://github.com/escalated-dev/escalated-go), [.NET](https://github.com/escalated-dev/escalated-dotnet), [WordPress](https://github.com/escalated-dev/escalated-wordpress) e [Filament](https://github.com/escalated-dev/escalated-filament). A interface fica em [escalated](https://github.com/escalated-dev/escalated).

## 

MIT. Copyright (c) 2026 Escalated.dev. Veja [LICENSE](../../LICENSE).
