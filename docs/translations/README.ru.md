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
  <b>Русский</b> •
  <a href="README.tr.md">Türkçe</a> •
  <a href="README.zh-CN.md">简体中文</a>
</p>

# Escalated Spring

Встраиваемая система helpdesk для приложений Spring Boot. Добавьте полнофункциональную службу поддержки в любое Java-приложение с помощью одной зависимости.

## Возможности

1. **Ticket CRUD** -- Полное управление жизненным циклом со статусами, приоритетами и назначениями
2. **SLA Policies** -- Настраиваемые SLA с поддержкой рабочего времени и календарями праздников
3. **Automations** -- Временные правила для автоматического закрытия решённых заявок и автоназначения
4. **Escalation Rules** -- Автоматическая эскалация при нарушении SLA с переназначением и уведомлениями
5. **Macros & Canned Responses** -- Предопределённые действия и шаблоны ответов для агентов
6. **Custom Fields** -- Расширяемые данные заявок с несколькими типами полей
7. **Knowledge Base** -- Статьи и категории с поиском, счётчиком просмотров и обратной связью
8. **Webhooks** -- HMAC-подписанная доставка webhook с логикой повторных попыток
9. **API Tokens** -- SHA-256 хэшированная аутентификация токенов для доступа к API
10. **Roles & Permissions** -- Детальное управление доступом на основе ролей
11. **Audit Logging** -- Полный журнал аудита для всех действий
12. **Import System** -- Массовый импорт заявок из структурированных данных
13. **Side Conversations** -- Приватные цепочки разговоров внутри заявок
14. **Ticket Merging & Linking** -- Объединение дублирующих заявок и связывание родственных
15. **Ticket Splitting** -- Разделение сложных заявок на отдельные проблемы
16. **Ticket Snooze** -- Откладывание заявок с автоматическим пробуждением через `@Scheduled`
17. **Email Threading** -- Брендированные HTML-шаблоны электронной почты через Thymeleaf с правильной цепочкой Message-ID
18. **Saved Views** -- Пользовательские фильтрованные/сортированные представления заявок для каждого агента
19. **Widget API** -- Публичные REST-эндпоинты для встраивания виджета поддержки
20. **Real-time Broadcasting** -- WebSocket через STOMP/SockJS (опционально)
21. **Capacity Management** -- Отслеживание и применение ограничений рабочей нагрузки агентов
22. **Skill-based Routing** -- Маршрутизация заявок к агентам с подходящими навыками
23. **CSAT Ratings** -- Опросы удовлетворённости клиентов с доступом по токену
24. **2FA (TOTP)** -- Поддержка одноразовых паролей на основе времени для учётных записей агентов
25. **Guest Access** -- Доступ к заявкам по токену без аутентификации

## Требования

- Java 17+
- Spring Boot 3.2+
- Реляционная база данных (PostgreSQL, MySQL или H2 для разработки)

## Установка

Добавьте зависимость в файл `build.gradle.kts`:

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

Или `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Конфигурация

Добавьте в файл `application.properties` или `application.yml`:

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

## Настройка базы данных

Миграции Flyway включены и выполняются автоматически. Миграция создаёт все таблицы с префиксом `escalated_` и заполняет роли и разрешения по умолчанию.

## Эндпоинты API

### Admin (`/escalated/api/admin/`)
| Метод | Путь | Описание |
|--------|------|-------------|
| GET | `/tickets` | Список заявок (с пагинацией, фильтрами) |
| POST | `/tickets` | Создать заявку |
| GET | `/tickets/{id}` | Получить заявку |
| PUT | `/tickets/{id}` | Обновить заявку |
| POST | `/tickets/{id}/assign` | Назначить заявку |
| POST | `/tickets/{id}/status` | Изменить статус |
| POST | `/tickets/{id}/snooze` | Отложить заявку |
| POST | `/tickets/{id}/merge` | Объединить заявки |
| POST | `/tickets/{id}/split` | Разделить заявку |
| DELETE | `/tickets/{id}` | Удалить заявку |
| GET/POST | `/departments` | Управление отделами |
| GET/POST | `/agents` | Управление агентами |
| GET/POST | `/webhooks` | Управление вебхуками |
| GET/POST | `/roles` | Управление ролями |
| GET/POST | `/custom-fields` | Управление пользовательскими полями |
| GET/POST | `/settings` | Управление настройками |
| GET | `/audit-logs` | Просмотр журналов аудита |
| POST | `/import/tickets` | Импорт заявок |
| GET/POST | `/kb/categories` | Управление категориями базы знаний |
| GET/POST | `/kb/articles` | Управление статьями базы знаний |

### Agent (`/escalated/api/agent/`)
| Метод | Путь | Описание |
|--------|------|-------------|
| GET | `/tickets` | Список назначенных/отфильтрованных заявок |
| GET | `/tickets/{id}` | Просмотр заявки |
| POST | `/tickets/{id}/replies` | Добавить ответ |
| POST | `/tickets/{id}/macro/{macroId}` | Применить макрос |
| POST | `/tickets/{id}/side-conversations` | Создать побочную беседу |
| POST | `/tickets/{id}/links` | Связать заявки |
| GET/POST | `/saved-views` | Управление сохранёнными представлениями |
| GET/POST | `/canned-responses` | Управление шаблонами ответов |

### Customer (`/escalated/api/customer/`)
| Метод | Путь | Описание |
|--------|------|-------------|
| GET | `/tickets?email=` | Список заявок клиента |
| POST | `/tickets` | Создать заявку |
| POST | `/tickets/{id}/replies` | Добавить ответ |

### Widget (`/escalated/api/widget/`)
| Метод | Путь | Описание |
|--------|------|-------------|
| POST | `/tickets` | Создать заявку (публичную) |
| GET | `/tickets/{token}` | Просмотр заявки по гостевому токену |
| POST | `/tickets/{token}/replies` | Ответ через гостевой токен |
| GET | `/kb/search?query=` | Поиск по базе знаний |
| POST | `/csat/{token}` | Отправить оценку удовлетворённости |

### Guest (`/escalated/api/guest/`)
| Метод | Путь | Описание |
|--------|------|-------------|
| GET | `/tickets/{token}` | Просмотр заявки |
| GET | `/tickets/{token}/replies` | Просмотр ответов |
| POST | `/tickets/{token}/replies` | Добавить ответ |

## Архитектура

```
dev.escalated/
  config/              Автоконфигурация, свойства, конфигурация WebSocket
  models/              JPA-сущности с полными связями
  repositories/        Репозитории Spring Data JPA
  services/            Бизнес-логика (транзакционная)
  controllers/
    admin/             REST API администратора
    agent/             REST API агента
    customer/          REST API клиента
    widget/            Публичное API виджета
  events/              События приложения Spring + слушатель вебхуков
  security/            Фильтр аутентификации API-токенов, конфигурация безопасности, 2FA
  scheduling/          Задачи @Scheduled (откладывание, SLA, автоматизации)
```

## Аутентификация

Эндпоинты API используют аутентификацию Bearer-токенами. Создавайте токены через API администратора:

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

Ответ содержит токен в открытом виде (показывается только один раз). Используйте его в последующих запросах:

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket (Реальное время)

Активируйте с помощью `escalated.broadcasting.enabled=true`. Подключитесь к `/escalated/ws` через SockJS/STOMP.

## Разработка

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## Лицензия

Лицензия MIT. Подробности см. в [LICENSE](LICENSE).
