<p align="center">
  <a href="README.ar.md">العربية</a> •
  <a href="README.de.md">Deutsch</a> •
  <a href="../../README.md">English</a> •
  <b>Español</b> •
  <a href="README.fr.md">Français</a> •
  <a href="README.it.md">Italiano</a> •
  <a href="README.ja.md">日本語</a> •
  <a href="README.ko.md">한국어</a> •
  <a href="README.nl.md">Nederlands</a> •
  <a href="README.pl.md">Polski</a> •
  <a href="README.pt-BR.md">Português (BR)</a> •
  <a href="README.ru.md">Русский</a> •
  <a href="README.tr.md">Türkçe</a> •
  <a href="README.zh-CN.md">简体中文</a>
</p>

# Escalated Spring

Un sistema de mesa de ayuda integrable para aplicaciones Spring Boot. Agregue un escritorio de soporte completo a cualquier aplicación Java con una sola dependencia.

## Características

1. **Ticket CRUD** -- Gestión completa del ciclo de vida con estados, prioridades y asignaciones
2. **SLA Policies** -- SLAs configurables con soporte de horario comercial y calendarios de días festivos
3. **Automations** -- Reglas basadas en tiempo para cierre automático de tickets resueltos y asignación automática
4. **Escalation Rules** -- Escalamiento automático por incumplimiento de SLA con reasignación y notificaciones
5. **Macros & Canned Responses** -- Acciones predefinidas y plantillas de respuesta para agentes
6. **Custom Fields** -- Datos de tickets extensibles con múltiples tipos de campos
7. **Knowledge Base** -- Artículos y categorías con búsqueda, conteo de vistas y comentarios
8. **Webhooks** -- Entrega de webhooks firmados con HMAC con lógica de reintentos
9. **API Tokens** -- Autenticación con token hasheado SHA-256 para acceso a la API
10. **Roles & Permissions** -- Control de acceso granular basado en roles
11. **Audit Logging** -- Registro de auditoría completo para todas las acciones
12. **Import System** -- Importación masiva de tickets desde datos estructurados
13. **Side Conversations** -- Conversaciones privadas con hilos dentro de los tickets
14. **Ticket Merging & Linking** -- Fusionar tickets duplicados y vincular los relacionados
15. **Ticket Splitting** -- Dividir tickets complejos en problemas separados
16. **Ticket Snooze** -- Posponer tickets con despertar automático mediante `@Scheduled`
17. **Email Threading** -- Plantillas de correo HTML con marca mediante Thymeleaf con encadenamiento correcto de Message-ID
18. **Saved Views** -- Vistas de tickets personalizadas filtradas/ordenadas por agente
19. **Widget API** -- Endpoints REST públicos para incrustar un widget de soporte
20. **Real-time Broadcasting** -- WebSocket mediante STOMP/SockJS (opcional)
21. **Capacity Management** -- Seguimiento y aplicación de límites de carga de trabajo de agentes
22. **Skill-based Routing** -- Dirigir tickets a agentes con habilidades coincidentes
23. **CSAT Ratings** -- Encuestas de satisfacción del cliente con acceso basado en token
24. **2FA (TOTP)** -- Soporte de contraseña de un solo uso basada en tiempo para cuentas de agentes
25. **Guest Access** -- Acceso a tickets basado en token sin autenticación

## Requisitos

- Java 17+
- Spring Boot 3.2+
- Una base de datos relacional (PostgreSQL, MySQL o H2 para desarrollo)

## Instalación

Agregue la dependencia a su `build.gradle.kts`:

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

O `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Configuración

Agregue a su `application.properties` o `application.yml`:

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

## Configuración de base de datos

Las migraciones de Flyway están incluidas y se ejecutan automáticamente. La migración crea todas las tablas con el prefijo `escalated_` y establece roles y permisos predeterminados.

## Endpoints de API

### Admin (`/escalated/api/admin/`)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/tickets` | Listar tickets (paginado, filtrable) |
| POST | `/tickets` | Crear ticket |
| GET | `/tickets/{id}` | Obtener ticket |
| PUT | `/tickets/{id}` | Actualizar ticket |
| POST | `/tickets/{id}/assign` | Asignar ticket |
| POST | `/tickets/{id}/status` | Cambiar estado |
| POST | `/tickets/{id}/snooze` | Posponer ticket |
| POST | `/tickets/{id}/merge` | Fusionar tickets |
| POST | `/tickets/{id}/split` | Dividir ticket |
| DELETE | `/tickets/{id}` | Eliminar ticket |
| GET/POST | `/departments` | Gestionar departamentos |
| GET/POST | `/agents` | Gestionar agentes |
| GET/POST | `/webhooks` | Gestionar webhooks |
| GET/POST | `/roles` | Gestionar roles |
| GET/POST | `/custom-fields` | Gestionar campos personalizados |
| GET/POST | `/settings` | Gestionar configuraciones |
| GET | `/audit-logs` | Ver registros de auditoría |
| POST | `/import/tickets` | Importar tickets |
| GET/POST | `/kb/categories` | Gestionar categorías de KB |
| GET/POST | `/kb/articles` | Gestionar artículos de KB |

### Agent (`/escalated/api/agent/`)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/tickets` | Listar tickets asignados/filtrados |
| GET | `/tickets/{id}` | Ver ticket |
| POST | `/tickets/{id}/replies` | Agregar respuesta |
| POST | `/tickets/{id}/macro/{macroId}` | Aplicar macro |
| POST | `/tickets/{id}/side-conversations` | Crear conversación lateral |
| POST | `/tickets/{id}/links` | Vincular tickets |
| GET/POST | `/saved-views` | Gestionar vistas guardadas |
| GET/POST | `/canned-responses` | Gestionar respuestas predefinidas |

### Customer (`/escalated/api/customer/`)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/tickets?email=` | Listar tickets del cliente |
| POST | `/tickets` | Crear ticket |
| POST | `/tickets/{id}/replies` | Agregar respuesta |

### Widget (`/escalated/api/widget/`)
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/tickets` | Crear ticket (público) |
| GET | `/tickets/{token}` | Ver ticket por token de invitado |
| POST | `/tickets/{token}/replies` | Responder mediante token de invitado |
| GET | `/kb/search?query=` | Buscar en la base de conocimientos |
| POST | `/csat/{token}` | Enviar calificación de satisfacción |

### Guest (`/escalated/api/guest/`)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/tickets/{token}` | Ver ticket |
| GET | `/tickets/{token}/replies` | Ver respuestas |
| POST | `/tickets/{token}/replies` | Agregar respuesta |

## Arquitectura

```
dev.escalated/
  config/              Auto-configuración, propiedades, configuración de WebSocket
  models/              Entidades JPA con relaciones completas
  repositories/        Repositorios Spring Data JPA
  services/            Lógica de negocio (transaccional)
  controllers/
    admin/             API REST de administrador
    agent/             API REST de agente
    customer/          API REST de cliente
    widget/            API pública del widget
  events/              Eventos de aplicación Spring + listener de webhooks
  security/            Filtro de autenticación de token API, configuración de seguridad, 2FA
  scheduling/          Tareas @Scheduled (posponer, SLA, automatizaciones)
```

## Autenticación

Los endpoints de API usan autenticación con token Bearer. Cree tokens a través de la API de administrador:

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

La respuesta incluye el token en texto plano (se muestra solo una vez). Úselo en solicitudes posteriores:

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket (Tiempo real)

Habilitar con `escalated.broadcasting.enabled=true`. Conéctese a `/escalated/ws` mediante SockJS/STOMP.

## Desarrollo

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## Licencia

Licencia MIT. Consulte [LICENSE](LICENSE) para más detalles.
