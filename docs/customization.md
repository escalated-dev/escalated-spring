# Customization

## Embedding the admin in your own admin panel (optional)

Escalated's admin is a standalone panel at `/escalated/admin` by default, and
that needs no setup. If your app already has an admin panel, you can show
Escalated's admin screens inside it instead, under your own prefix, security
chain and layout:

```properties
escalated.ui.enabled=true
escalated.ui.embed.enabled=true
escalated.ui.embed.prefix=admin/support
# Filter beans of yours to run on the embedded admin, after your security chain
escalated.ui.embed.middleware=hostAdminOnly
# Your Vue admin layout (frontend `layouts` option), or a Thymeleaf root view
escalated.ui.embed.layout=Layouts/AdminLayout
```

The admin pages then answer under `/admin/support` with the same route names
(the old URLs answer 404), Escalated's admin gate, two-factor, route
permissions and CSRF still apply, and every page shares `escalated.navigation`
for your sidebar. A Thymeleaf admin uses `escalated.ui.embed.root-view`; an
admin on another origin uses `escalated.ui.embed.mode=iframe` with
`escalated.ui.embed.frame-ancestors`. Also settable with an
`EscalatedEmbedConfigurer` bean or `ESCALATED_EMBED*` variables. Nothing
changes unless you configure it. See
[docs/embedding-admin.md](embedding-admin.md).

## Ticket subjects

A ticket has a **requester** (who raised it) and a **subject line** (free text).
Tickets can also be *about* host-app entities — a Project, Customer, asset — that
are not people. Attach them as **ticket subjects** so agents see what the ticket
concerns and can jump to the entity in your app.

Implement the `dev.escalated.contracts.TicketSubject` interface on host models and
register a `TicketSubjectResolver` bean to resolve `type`/`id` pairs for the UI:

```java
@Component
public class ProjectTicketSubjectResolver implements TicketSubjectResolver {
    private final ProjectRepository projects;

    @Override
    public TicketSubject resolve(String subjectType, String subjectId) {
        if (!"com.example.Project".equals(subjectType)) {
            return null;
        }
        return projects.findById(subjectId).orElse(null);
    }
}
```

Allow types the admin API may attach (prevents arbitrary type injection):

```yaml
escalated:
  ticket-subjects:
    types:
      - com.example.Project
      - com.example.Customer
```

Attach or detach via the admin API:

```
POST   /escalated/api/admin/tickets/{ticketId}/subjects   { "type", "id", "role"? }
DELETE /escalated/api/admin/tickets/{ticketId}/subjects/{linkId}
```

Ticket detail responses include `subjects[]` with
`{ type, id, role, title, subtitle, url, color, icon, missing }`. When no
resolver is registered or the entity is gone, `title` falls back to `type#id`
and `missing` is `true`.

Programmatic attach via `TicketSubjectService` works for any type when the
allowlist is empty; the API only accepts allowlisted types.

## Custom Ticket Actions

Host applications can add custom buttons to the agent ticket screen and handle
clicks with a Spring `@EventListener`. Register actions in `application.yml`:

```yaml
escalated:
  ticket-actions:
    - key: sync-crm
      label: Sync CRM
      variant: primary          # primary | secondary | danger
      confirmation: "Sync this ticket to the CRM?"
      metadata:
        icon: refresh-cw
```

Visible actions are exposed on the agent ticket detail response as
`custom_actions` (each with a `url` and `method`). Triggering one
(`POST /escalated/api/agent/tickets/{id}/actions/{action}`) validates the action
is visible (404) and enabled (403), records an internal note for auditability,
and publishes a `CustomActionTriggeredEvent`:

```java
@Component
public class CrmSyncListener {
    @EventListener
    public void onCustomAction(CustomActionTriggeredEvent event) {
        if (!"sync-crm".equals(event.getAction())) {
            return;
        }
        // event.getTicket(), event.getUserEmail(), event.getPayload(), event.getMetadata()
    }
}
```

## Internationalization

Translations are consumed from the central [`dev.escalated:escalated-locale`](https://github.com/escalated-dev/escalated-locale) Maven artifact, which ships bundles at `META-INF/escalated/locale/messages_{locale}.properties` on the classpath. The auto-configured `MessageSource` chains two basenames so host apps can override keys without forking the central bundle:

1. `classpath:i18n/overrides/messages` — sparse host-app overrides (first match wins)
2. `classpath:META-INF/escalated/locale/messages` — central artifact (canonical strings)

Drop a `messages_{locale}.properties` file under `src/main/resources/i18n/overrides/` to override individual keys. See [`src/main/resources/i18n/overrides/README.md`](../src/main/resources/i18n/overrides/README.md) for examples. To fix a typo or mistranslation that affects every host plugin, open a PR against the central [`escalated-locale`](https://github.com/escalated-dev/escalated-locale) repo instead.

## WebSocket (Real-time)

Enable with `escalated.broadcasting.enabled=true`. Connect to `/escalated/ws` via SockJS/STOMP.

- Origins: same-origin, plus the sites in `escalated.widget.allowed-origins`.
- Staff authenticate with the host session used for the handshake, or an Escalated API token in the STOMP `CONNECT` frame (`Authorization: Bearer <token>`). Staff may subscribe to `/topic/chat-queue` and any chat topic.
- A widget visitor may subscribe only to their own chat's `/topic/chat/{id}`, `/topic/chat/{id}/messages` and `/topic/chat/{id}/typing`, sending the chat token from `/widget/chat/start` in a `chat-token` header on `SUBSCRIBE`.
- Clients cannot `SEND` to `/topic` or `/queue` destinations.
