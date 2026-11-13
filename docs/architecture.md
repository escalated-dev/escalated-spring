# Architecture

## Architecture

```
dev.escalated/
  config/              Auto-configuration, properties, WebSocket config
  models/              JPA entities with full relationships
  repositories/        Spring Data JPA repositories
  services/            Business logic (transactional)
  controllers/
    admin/             Admin REST API
    agent/             Agent REST API
    customer/          Customer REST API
    widget/            Public widget API
  events/              Spring application events + webhook listener
  security/            API token auth filter, security config, 2FA
  scheduling/          @Scheduled tasks (snooze, SLA, automations)
```
