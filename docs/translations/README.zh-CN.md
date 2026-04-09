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
  <a href="README.pt-BR.md">Português (BR)</a> •
  <a href="README.ru.md">Русский</a> •
  <a href="README.tr.md">Türkçe</a> •
  <b>简体中文</b>
</p>

# Escalated Spring

适用于 Spring Boot 应用程序的可嵌入式帮助台系统。通过单个依赖项为任何 Java 应用程序添加功能齐全的支持台。

## 功能

1. **Ticket CRUD** -- 通过状态、优先级和分配实现完整的生命周期管理
2. **SLA Policies** -- 可配置的 SLA，支持工作时间和假日日历
3. **Automations** -- 基于时间的规则，用于自动关闭已解决的工单和自动分配
4. **Escalation Rules** -- SLA 违规时自动升级，包含重新分配和通知
5. **Macros & Canned Responses** -- 为客服人员提供预定义操作和响应模板
6. **Custom Fields** -- 支持多种字段类型的可扩展工单数据
7. **Knowledge Base** -- 包含搜索、浏览计数和反馈的文章和分类
8. **Webhooks** -- 带重试逻辑的 HMAC 签名 Webhook 交付
9. **API Tokens** -- 用于 API 访问的 SHA-256 哈希令牌认证
10. **Roles & Permissions** -- 细粒度的基于角色的访问控制
11. **Audit Logging** -- 所有操作的完整审计跟踪
12. **Import System** -- 从结构化数据批量导入工单
13. **Side Conversations** -- 工单内的私密线程对话
14. **Ticket Merging & Linking** -- 合并重复工单并链接相关工单
15. **Ticket Splitting** -- 将复杂工单拆分为独立问题
16. **Ticket Snooze** -- 通过 `@Scheduled` 自动唤醒的工单休眠
17. **Email Threading** -- 通过 Thymeleaf 实现品牌化 HTML 邮件模板和正确的 Message-ID 线程
18. **Saved Views** -- 每个客服人员的自定义过滤/排序工单视图
19. **Widget API** -- 用于嵌入支持小部件的公共 REST 端点
20. **Real-time Broadcasting** -- 通过 STOMP/SockJS 的 WebSocket（可选启用）
21. **Capacity Management** -- 跟踪和执行客服人员工作负载限制
22. **Skill-based Routing** -- 将工单路由到具有匹配技能的客服人员
23. **CSAT Ratings** -- 基于令牌访问的客户满意度调查
24. **2FA (TOTP)** -- 客服人员账户的基于时间的一次性密码支持
25. **Guest Access** -- 无需认证的基于令牌的工单访问

## 要求

- Java 17+
- Spring Boot 3.2+
- 关系型数据库（PostgreSQL、MySQL 或用于开发的 H2）

## 安装

将依赖项添加到 `build.gradle.kts`：

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

或 `pom.xml`：

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## 配置

添加到 `application.properties` 或 `application.yml`：

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

## 数据库设置

Flyway 迁移已包含并自动运行。迁移会创建所有带有 `escalated_` 前缀的表，并初始化默认角色和权限。

## API 端点

### Admin (`/escalated/api/admin/`)
| 方法 | 路径 | 描述 |
|--------|------|-------------|
| GET | `/tickets` | 列出工单（分页、可过滤） |
| POST | `/tickets` | 创建工单 |
| GET | `/tickets/{id}` | 获取工单 |
| PUT | `/tickets/{id}` | 更新工单 |
| POST | `/tickets/{id}/assign` | 分配工单 |
| POST | `/tickets/{id}/status` | 更改状态 |
| POST | `/tickets/{id}/snooze` | 休眠工单 |
| POST | `/tickets/{id}/merge` | 合并工单 |
| POST | `/tickets/{id}/split` | 拆分工单 |
| DELETE | `/tickets/{id}` | 删除工单 |
| GET/POST | `/departments` | 管理部门 |
| GET/POST | `/agents` | 管理客服人员 |
| GET/POST | `/webhooks` | 管理 Webhooks |
| GET/POST | `/roles` | 管理角色 |
| GET/POST | `/custom-fields` | 管理自定义字段 |
| GET/POST | `/settings` | 管理设置 |
| GET | `/audit-logs` | 查看审计日志 |
| POST | `/import/tickets` | 导入工单 |
| GET/POST | `/kb/categories` | 管理知识库分类 |
| GET/POST | `/kb/articles` | 管理知识库文章 |

### Agent (`/escalated/api/agent/`)
| 方法 | 路径 | 描述 |
|--------|------|-------------|
| GET | `/tickets` | 列出已分配/已过滤的工单 |
| GET | `/tickets/{id}` | 查看工单 |
| POST | `/tickets/{id}/replies` | 添加回复 |
| POST | `/tickets/{id}/macro/{macroId}` | 应用宏 |
| POST | `/tickets/{id}/side-conversations` | 创建侧边对话 |
| POST | `/tickets/{id}/links` | 链接工单 |
| GET/POST | `/saved-views` | 管理已保存的视图 |
| GET/POST | `/canned-responses` | 管理预设回复 |

### Customer (`/escalated/api/customer/`)
| 方法 | 路径 | 描述 |
|--------|------|-------------|
| GET | `/tickets?email=` | 列出客户工单 |
| POST | `/tickets` | 创建工单 |
| POST | `/tickets/{id}/replies` | 添加回复 |

### Widget (`/escalated/api/widget/`)
| 方法 | 路径 | 描述 |
|--------|------|-------------|
| POST | `/tickets` | 创建工单（公开） |
| GET | `/tickets/{token}` | 通过访客令牌查看工单 |
| POST | `/tickets/{token}/replies` | 通过访客令牌回复 |
| GET | `/kb/search?query=` | 搜索知识库 |
| POST | `/csat/{token}` | 提交满意度评分 |

### Guest (`/escalated/api/guest/`)
| 方法 | 路径 | 描述 |
|--------|------|-------------|
| GET | `/tickets/{token}` | 查看工单 |
| GET | `/tickets/{token}/replies` | 查看回复 |
| POST | `/tickets/{token}/replies` | 添加回复 |

## 架构

```
dev.escalated/
  config/              自动配置、属性、WebSocket 配置
  models/              具有完整关系的 JPA 实体
  repositories/        Spring Data JPA 仓库
  services/            业务逻辑（事务性）
  controllers/
    admin/             管理员 REST API
    agent/             客服人员 REST API
    customer/          客户 REST API
    widget/            公共小部件 API
  events/              Spring 应用程序事件 + Webhook 监听器
  security/            API 令牌认证过滤器、安全配置、2FA
  scheduling/          @Scheduled 任务（休眠、SLA、自动化）
```

## 认证

API 端点使用 Bearer 令牌认证。通过管理员 API 创建令牌：

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

响应包含明文令牌（仅显示一次）。在后续请求中使用：

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket（实时）

使用 `escalated.broadcasting.enabled=true` 启用。通过 SockJS/STOMP 连接到 `/escalated/ws`。

## 开发

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## 许可证

MIT 许可证。详情请参阅 [LICENSE](LICENSE)。
