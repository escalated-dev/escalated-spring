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

### Giving users roles from Admin > Users

While the switch is on, **Admin > Users** shows each user's role and an admin
with `role.manage` can change it. The screen saves with `PUT C:/Program Files/Git/escalated/admin/users/{user}/roles` and
`{ "role_ids": [...] }`, as escalated-laravel. The ids must be existing roles
(422 otherwise), and you cannot take away your own `role.manage` ("You cannot
remove your own role management."). While the switch is off the route answers
404 and the screen shows no roles.

A user holds **one** Escalated role in this package (the role on their agent
profile), where Laravel keeps a set. The screen sends the user's whole set with
one role ticked or unticked, so: `[]` clears the role; one id sets it; the
current role plus one other sets the other (ticking a new role replaces the old
one); two roles that are both new are refused with a 422 on `role_ids`.

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

## Two-factor authentication

With `ESCALATED_TWO_FACTOR` on (and `escalated.two-factor.encryption-key` /
`ESCALATED_TWO_FACTOR_SECRET` set), staff sign in with the host's password
login and then pass a TOTP challenge before any admin or agent page.

- **Enrolment** is Admin > Settings > Two-factor
  (`/escalated/admin/settings/two-factor`), open to every staff user, not only
  admins, and needing no permission. `setup` flashes `two_factor_setup`
  (`qr_uri`, `recovery_codes`), `confirm` takes a 6-digit `code` and flashes
  `two_factor_confirmed`, `disable` removes the enrolment. Enrolments live in
  `escalated_two_factors` (Flyway `V26`); the secret and the recovery codes are
  encrypted with Spring Security's AES-GCM `Encryptors.delux` under the key
  and never sent in props. Keep the key stable: with another key, existing
  enrolments cannot be read and their challenges fail.
- **TOTP** is RFC 6238 (SHA-1, 6 digits, 30 s) through
  `com.eatthepath:java-otp`, one step of drift either side. The time step of an
  accepted code is recorded, and that step or any earlier one is refused, so a
  code works once.
- **The challenge.** A staff user with a confirmed enrolment who has not
  passed it in this session is redirected from every admin and agent page to
  `GET /escalated/two-factor/challenge` (`Auth/TwoFactorChallenge`), which
  posts `code` or `recovery_code` back to the same path. Success changes the
  session ID, marks the session for that user and returns to the page they
  asked for. Failures are errors on the field sent; 5 attempts per user per
  minute, then 429. JSON clients get `403 {"two_factor_required": true}`.
  API token requests are not challenged.
- **Every new login is challenged.** An interactive sign-in
  (`InteractiveAuthenticationSuccessEvent`: form login, remember-me, OAuth 2
  login) and a logout clear the mark. A host whose login publishes no such
  event calls `TwoFactorSession.forget(request)` on login.
- **Recovery codes.** Eight single-use codes are issued at setup.
- **Required.** `escalated.two-factor.required`
  (`ESCALATED_TWO_FACTOR_REQUIRED`, default false), or the
  `two_factor_required` setting (the "Require for all staff" switch on the
  two-factor page posts only that key to Admin > Settings; the setting wins
  when present). Staff without an enrolment are then sent to enrol and reach
  nothing else. Shared as `escalated.two_factor_required`.
- **Lost device.** `TwoFactorService.reset(email)` removes a user's enrolment.

With the switch off nobody is challenged, the enrolment and challenge routes
answer 404, and stored enrolments are kept.

## Single sign-on

With `ESCALATED_SSO` on, staff (and customers) can sign in through the provider
chosen in Admin > Settings > SSO (`sso_provider`: `none`, `saml`, `oauth`,
`jwt`). The page (`Admin/Settings/SsoSettings`) shows secrets masked as
`********` (posting the mask back keeps the stored value) and lists the
endpoints to give the identity provider. With `none`, `/escalated/sso/login`
answers 404; a provider's routes answer 404 while another provider is chosen.

| Route | Use |
|---|---|
| `GET /escalated/sso/login` | Start sign-in with the configured provider |
| `GET /escalated/sso/saml/metadata` | SP metadata XML; its URL is the default SP entity ID |
| `POST /escalated/sso/saml/acs` | SAML assertion consumer (HTTP-POST binding) |
| `GET /escalated/sso/oauth/callback` | OAuth 2 / OIDC redirect URI |
| `GET\|POST /escalated/sso/jwt/callback` | JWT hand-off (`jwt` parameter) |

- **SAML** (XML signatures through Apache Santuario): the IdP certificate
  (`sso_certificate`) must be configured; the single assertion must carry a
  signature by it over the whole assertion (a signed Response alone, a key in
  `KeyInfo`, `EncryptedAssertion` and duplicate IDs are refused); `Issuer` must
  be `sso_entity_id`; `Audience` must name the SP entity ID; `Destination` and
  the bearer `Recipient` must be the ACS URL; the validity windows must hold
  (`escalated.sso.clock-skew`, default 120 s, at most 300); `InResponseTo` must
  be an AuthnRequest issued in the last 10 minutes (IdP-initiated responses
  only with `escalated.sso.allow-idp-initiated`); and the assertion ID is
  single-use. Issued and used IDs live in `escalated_sso_message_ids`
  (Flyway `V27`).
- **OAuth 2 / OIDC** (Nimbus OAuth 2.0 SDK): authorization code with a
  single-use `state` kept in the session and PKCE (`S256`); the code is
  exchanged at `sso_oauth_token_url` and the profile read from
  `sso_oauth_userinfo_url`. `email_verified: false` is refused.
- **JWT** (Nimbus JOSE): `alg` must be `sso_jwt_algorithm` (HS* with
  `sso_jwt_secret`, at least 32 bytes; RS* with the PEM public key or
  certificate in `sso_jwt_secret`); `exp` is required; `nbf`/`iat` hold within
  the skew; `aud` must be `escalated.sso.jwt-audience` (default the install's
  base URL); `jti` is required and single-use.
- **Users.** The email comes from `sso_attr_email` (SAML falls back to an
  email-shaped NameID), the name from `sso_attr_name`, the role from
  `sso_attr_role`. The user is found through `EscalatedSsoUsers`: by default
  the host's `UserDetailsService` (or, without one, Escalated's agent profiles
  by email). An unknown email is refused unless `escalated.sso.create-users`
  is on and the host has a `UserDetailsManager`, which then gets a user with
  an unusable random password. A host replaces this by defining an
  `EscalatedSsoUsers` bean.
- **Roles.** With roles & permissions on and `escalated.sso.sync-roles` on, a
  role attribute naming an existing Escalated role becomes the user's role.
  An unknown value changes nothing. SSO never grants the admin or agent gate.
- On success the user is saved in the HTTP session under a new session ID,
  the two-factor mark is cleared (SSO does not skip the challenge), and the
  browser goes to `escalated.sso.redirect-to` (default `/escalated/agent`).
  Every refusal answers a generic 403 and logs the reason.
- Install-level properties (each with an `ESCALATED_SSO_*` env fallback):
  `escalated.sso.sp-entity-id`, `allow-idp-initiated`, `jwt-audience`,
  `clock-skew`, `create-users`, `sync-roles`, `redirect-to`, `base-url`.

With the switch off every `/escalated/sso/*` route and the settings page
answer 404. Escalated does not turn off the host's own password login.
