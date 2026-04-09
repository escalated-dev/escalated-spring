<p align="center">
  <a href="README.ar.md">العربية</a> •
  <a href="README.de.md">Deutsch</a> •
  <a href="../../README.md">English</a> •
  <a href="README.es.md">Español</a> •
  <a href="README.fr.md">Français</a> •
  <a href="README.it.md">Italiano</a> •
  <a href="README.ja.md">日本語</a> •
  <a href="README.ko.md">한국어</a> •
  <a href="README.nl.md">Nederlands</a> •
  <a href="README.pl.md">Polski</a> •
  <b>Português (BR)</b> •
  <a href="README.ru.md">Русский</a> •
  <a href="README.tr.md">Türkçe</a> •
  <a href="README.zh-CN.md">简体中文</a>
</p>

# Escalated Spring

Um sistema de helpdesk incorporável para aplicações Spring Boot. Adicione um suporte completo a qualquer aplicação Java com uma única dependência.

## Funcionalidades

1. **Ticket CRUD** -- Gerenciamento completo do ciclo de vida com status, prioridades e atribuições
2. **SLA Policies** -- SLAs configuráveis com suporte a horário comercial e calendários de feriados
3. **Automations** -- Regras baseadas em tempo para fechamento automático de tickets resolvidos e atribuição automática
4. **Escalation Rules** -- Escalonamento automático por violação de SLA com reatribuição e notificações
5. **Macros & Canned Responses** -- Ações predefinidas e modelos de resposta para agentes
6. **Custom Fields** -- Dados de tickets extensíveis com múltiplos tipos de campos
7. **Knowledge Base** -- Artigos e categorias com busca, contagem de visualizações e feedback
8. **Webhooks** -- Entrega de webhooks assinados com HMAC com lógica de repetição
9. **API Tokens** -- Autenticação por token hasheado com SHA-256 para acesso à API
10. **Roles & Permissions** -- Controle de acesso granular baseado em funções
11. **Audit Logging** -- Trilha de auditoria completa para todas as ações
12. **Import System** -- Importação em massa de tickets a partir de dados estruturados
13. **Side Conversations** -- Conversas privadas com threads dentro dos tickets
14. **Ticket Merging & Linking** -- Mesclar tickets duplicados e vincular relacionados
15. **Ticket Splitting** -- Dividir tickets complexos em problemas separados
16. **Ticket Snooze** -- Adiar tickets com despertar automático via `@Scheduled`
17. **Email Threading** -- Modelos de e-mail HTML personalizados via Thymeleaf com encadeamento correto de Message-ID
18. **Saved Views** -- Visualizações de tickets personalizadas filtradas/ordenadas por agente
19. **Widget API** -- Endpoints REST públicos para incorporar um widget de suporte
20. **Real-time Broadcasting** -- WebSocket via STOMP/SockJS (opcional)
21. **Capacity Management** -- Rastreamento e aplicação de limites de carga de trabalho de agentes
22. **Skill-based Routing** -- Roteamento de tickets para agentes com habilidades correspondentes
23. **CSAT Ratings** -- Pesquisas de satisfação do cliente com acesso baseado em token
24. **2FA (TOTP)** -- Suporte a senha de uso único baseada em tempo para contas de agentes
25. **Guest Access** -- Acesso a tickets baseado em token sem autenticação

## Requisitos

- Java 17+
- Spring Boot 3.2+
- Um banco de dados relacional (PostgreSQL, MySQL ou H2 para desenvolvimento)

## Instalação

Adicione a dependência ao seu `build.gradle.kts`:

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

Ou `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Configuração

Adicione ao seu `application.properties` ou `application.yml`:

```properties
# Enable/disable the helpdesk
escalated.enabled=true

# Route prefix (default: escalated)
escalated.route-prefix=escalated

# Feature toggles
escalated.knowledge-base.enabled=true
escalated.broadcasting.enabled=false
escalated.two-factor.enabled=true
escalated.widget.enabled=true
escalated.guest-access.enabled=true

# SLA checking interval
escalated.sla.check-interval-seconds=60

# Snooze wake-up interval
escalated.snooze.check-interval-seconds=60

# Webhook settings
escalated.webhook.max-retries=3

# Database (example for PostgreSQL)
spring.datasource.url=jdbc:postgresql://localhost:5432/myapp
spring.datasource.username=user
spring.datasource.password=secret
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

## Configuração do banco de dados

As migrações Flyway estão incluídas e são executadas automaticamente. A migração cria todas as tabelas com o prefixo `escalated_` e inicializa funções e permissões padrão.

## Endpoints da API

### Admin (`/escalated/api/admin/`)
| Método | Caminho | Descrição |
|--------|------|-------------|
| GET | `/tickets` | Listar tickets (paginado, filtrável) |
| POST | `/tickets` | Criar ticket |
| GET | `/tickets/{id}` | Obter ticket |
| PUT | `/tickets/{id}` | Atualizar ticket |
| POST | `/tickets/{id}/assign` | Atribuir ticket |
| POST | `/tickets/{id}/status` | Alterar status |
| POST | `/tickets/{id}/snooze` | Adiar ticket |
| POST | `/tickets/{id}/merge` | Mesclar tickets |
| POST | `/tickets/{id}/split` | Dividir ticket |
| DELETE | `/tickets/{id}` | Excluir ticket |
| GET/POST | `/departments` | Gerenciar departamentos |
| GET/POST | `/agents` | Gerenciar agentes |
| GET/POST | `/webhooks` | Gerenciar webhooks |
| GET/POST | `/roles` | Gerenciar funções |
| GET/POST | `/custom-fields` | Gerenciar campos personalizados |
| GET/POST | `/settings` | Gerenciar configurações |
| GET | `/audit-logs` | Ver logs de auditoria |
| POST | `/import/tickets` | Importar tickets |
| GET/POST | `/kb/categories` | Gerenciar categorias da KB |
| GET/POST | `/kb/articles` | Gerenciar artigos da KB |

### Agent (`/escalated/api/agent/`)
| Método | Caminho | Descrição |
|--------|------|-------------|
| GET | `/tickets` | Listar tickets atribuídos/filtrados |
| GET | `/tickets/{id}` | Visualizar ticket |
| POST | `/tickets/{id}/replies` | Adicionar resposta |
| POST | `/tickets/{id}/macro/{macroId}` | Aplicar macro |
| POST | `/tickets/{id}/side-conversations` | Criar conversa lateral |
| POST | `/tickets/{id}/links` | Vincular tickets |
| GET/POST | `/saved-views` | Gerenciar visualizações salvas |
| GET/POST | `/canned-responses` | Gerenciar respostas predefinidas |

### Customer (`/escalated/api/customer/`)
| Método | Caminho | Descrição |
|--------|------|-------------|
| GET | `/tickets?email=` | Listar tickets do cliente |
| POST | `/tickets` | Criar ticket |
| POST | `/tickets/{id}/replies` | Adicionar resposta |

### Widget (`/escalated/api/widget/`)
| Método | Caminho | Descrição |
|--------|------|-------------|
| POST | `/tickets` | Criar ticket (público) |
| GET | `/tickets/{token}` | Visualizar ticket por token de convidado |
| POST | `/tickets/{token}/replies` | Responder via token de convidado |
| GET | `/kb/search?query=` | Pesquisar base de conhecimento |
| POST | `/csat/{token}` | Enviar avaliação de satisfação |

### Guest (`/escalated/api/guest/`)
| Método | Caminho | Descrição |
|--------|------|-------------|
| GET | `/tickets/{token}` | Visualizar ticket |
| GET | `/tickets/{token}/replies` | Visualizar respostas |
| POST | `/tickets/{token}/replies` | Adicionar resposta |

## Arquitetura

```
dev.escalated/
  config/              Auto-configuração, propriedades, configuração WebSocket
  models/              Entidades JPA com relacionamentos completos
  repositories/        Repositórios Spring Data JPA
  services/            Lógica de negócios (transacional)
  controllers/
    admin/             API REST do administrador
    agent/             API REST do agente
    customer/          API REST do cliente
    widget/            API pública do widget
  events/              Eventos de aplicação Spring + listener de webhooks
  security/            Filtro de autenticação de token API, configuração de segurança, 2FA
  scheduling/          Tarefas @Scheduled (adiamento, SLA, automações)
```

## Autenticação

Os endpoints da API usam autenticação por token Bearer. Crie tokens pela API de administrador:

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

A resposta inclui o token em texto simples (exibido apenas uma vez). Use-o em solicitações subsequentes:

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket (Tempo real)

Ative com `escalated.broadcasting.enabled=true`. Conecte-se a `/escalated/ws` via SockJS/STOMP.

## Desenvolvimento

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## Licença

Licença MIT. Veja [LICENSE](LICENSE) para detalhes.
