# Access control: roles & permissions, two-factor, SSO

Escalated's roles & permissions, two-factor authentication and single sign-on
are **install-level switches**, off by default
([ADR 2026-10-29](https://github.com/escalated-dev/escalated-developer-context/blob/main/decisions/2026-10-29-configurable-access-features.md)).
While a switch is off its screens and routes answer 404 and nothing is
enforced: the admin and agent gates (`is_admin` / `is_agent` on the agent
profile, or `ROLE_ESCALATED_ADMIN` / `ROLE_ESCALATED_AGENT`) decide who may do
what, exactly as before. While it is on, the feature is enforced.

The switches are read from Spring configuration, else the environment. No
admin screen or stored setting changes them.

| Feature | Property | Env var |
|---|---|---|
| Roles & permissions | `escalated.access.roles-permissions` | `ESCALATED_ROLES_PERMISSIONS` |
| Two-factor authentication | `escalated.access.two-factor` | `ESCALATED_TWO_FACTOR` |
| Single sign-on | `escalated.access.sso` | `ESCALATED_SSO` |

Every page shares them as `escalated.features.roles_permissions`,
`escalated.features.two_factor` and `escalated.features.sso`, and the shared
frontend hides the navigation entries of a feature that is off.

## Roles & permissions

### What is enforced

With the switch on, every admin, agent and staff API route
(`/escalated/admin/**`, `/escalated/agent/**`, `/escalated/api/admin/**`,
`/escalated/api/agent/**`, `/escalated/api/plugins/**`) needs a permission
**in addition to** the gate for that area. A user's permissions are those of
the role on their agent profile. Nothing else grants one: passing the admin
gate, `is_admin` or `ROLE_ESCALATED_ADMIN` gives no permissions by itself.
Give users who should have everything the `admin` role.

- The route to permission map is `dev.escalated.security.RoutePermissions`
  (escalated-laravel's `RoutePermissions`, keyed by method and path). A staff
  route missing from it is refused, and a test checks that every staff route
  the application registers is in it. Reads need `*.view`, writes `*.manage`
  (or the finer `create`/`edit`/`delete`/`publish` slugs where they exist).
- A status change needs `ticket.edit`, plus `ticket.close` when the new status
  is `resolved`, `closed`, `reopened` or a custom status in the solved
  category.
- Bulk actions need the permission of the action: `assign` needs
  `ticket.assign`, `delete` needs `ticket.delete`, the rest `ticket.edit`.
- An internal reply through the agent API needs `reply.create_internal`.
- Applying a macro needs `macro.view` and `ticket.edit`.
- API tokens act as their owner, so a token request is checked against the
  owner's permissions as well as the token's abilities.
- The newsletter screens check `newsletters.manage` and `newsletters.send`,
  and plugin pages and endpoints the capability they declare, against the
  user's role. With the switch off, any admin may use the newsletter screens.

Failing a permission answers 403. `escalated.permissions` is the user's slugs
while the switch is on, and `['*']` for staff (`[]` for anyone else) while it
is off.

### Seeded roles

escalated-laravel's permission set and its system roles are seeded by Flyway
`V25` on PostgreSQL and MySQL, and by `dev.escalated.services.PermissionSeeder`
at startup whenever the switch is on (so a schema Hibernate builds has them
too). Seeding only adds what is missing.

| Role | Permissions |
|---|---|
| `admin` | every permission |
| `agent` | `ticket.*`, `reply.*`, `kb.view`, `report.view`, `macro.view`, `macro.create`, `tag.view`, `custom_field.view`, `audit.view` |
| `light_agent` | `ticket.view`, `reply.create`, `reply.create_internal`, `kb.view`, `macro.view`, `tag.view` |

The permission names earlier releases seeded (`tickets.view`, `agents.manage`,
...) are kept but grant nothing.

### Turning it on

1. Give every staff user a role. This port keeps one role per agent profile
   (`role_id`); set it through the agents API (`role_id`) or, to bootstrap the
   first admin, call `PermissionSeeder.assignRole("you@example.com", "admin")`
   (escalated-laravel's `escalated:roles:assign`), for example from a
   `CommandLineRunner`.
2. Set `ESCALATED_ROLES_PERMISSIONS=true` (or
   `escalated.access.roles-permissions=true`).

Users with no role have no permissions: turning the switch on without
assigning roles locks staff out of every admin and agent screen.
