# Verified guest access

Guests prove they can read a mailbox before they create a ticket, start a chat
or look a ticket up, as in escalated-laravel's `docs/guest-access.md`. The
shared widget (`@escalated-dev/escalated` 0.12.x) does this when `/config`
answers `guest_verification_required: true`, which this backend now does.

## Email codes

`POST /escalated/api/widget/verification` (or `/escalated/api/guest/verification`)
with `{"email": "...", "purpose": "ticket"}`. `purpose` is `ticket`, `chat` or
`lookup`. The answer is `202 {verification_id, expires_in: 600, message}`. Only
the email carries the 8-digit code.

- A code is bound to the normalised address and the purpose. It lasts ten
  minutes, allows five guesses and is spent once. A wrong guess stays counted.
  If the operation it was spent on fails, the code is handed back.
- Neither the code nor the address is stored: both are HMAC digests
  (`escalated_guest_verifications`, migration `V17`).
- Codes per mailbox are limited to 3 an hour from one client IP and 10 an hour
  in total (`escalated.guest-access.codes-per-client-per-hour`,
  `codes-per-mailbox-per-hour`). Over budget is `429` with `Retry-After`. Both
  routes also count against the per-IP guest reply limit.
- The email goes through the host's `JavaMailSender`, from
  `escalated.guest-access.from-address` when set. A host can replace it with its
  own `GuestCodeMailer` bean.

Send `verification_id` and `verification_code` with the operation:

| Route | Purpose | Answer |
|---|---|---|
| `POST /widget/tickets` | `ticket` | `201 {message, reference, guest_access_token, expires_at}` |
| `POST /widget/chat/start` | `chat` | as before; `email` is now required |
| `POST /widget/lookup`, `POST /guest/lookup` | `lookup` | `{data: [{reference, subject, guest_access_token, expires_at}]}` |

Lookup takes `email` and `reference` (the ticket number). It matches only
tickets requested by that address, at most 20, possibly none. The proof is spent
either way.

## Grants

`guest_access_token` is an expiring grant: an opaque bearer token (AES-256-GCM
over the ticket id, a random nonce, the expiry and a digest of the requester's
email). Do not parse it. The ticket keeps only the SHA-256 of the current nonce
and the expiry (`guest_access_hash`, `guest_access_expires_at`).

- Lifetime: `escalated.guest-access.ttl-minutes`, default 1440 (24 hours),
  clamped to 5 minutes through 7 days.
- A new grant (from creation or lookup) revokes the earlier ones. A changed
  requester email revokes them too. `GuestAccessGrants.revoke(ticketId)` is there
  for host code.
- Send it as `Authorization: Bearer <grant>` with the ticket reference in the
  path:
  - `GET /widget/tickets/{reference}` answers what the widget's Check Status
    tab reads: `reference, subject, status, status_label, status_color,
    created_at, department` and the 20 latest public `replies`.
  - `GET /guest/tickets/{reference}` answers the guest ticket view.
  - `GET` and `POST .../tickets/{reference}/replies` work on both prefixes.
  A grant for another ticket, a forged or expired grant, or a revoked grant is a
  403.
- Guest answers carry `Cache-Control: no-store, private` and
  `Referrer-Policy: no-referrer`.
- The key is `escalated.guest-access.secret`, else
  `escalated.email.inbound-secret`. Without either, a random key per process is
  used (a warning is logged), and grants then do not survive a restart or work
  across nodes. Set the same secret on every node.

## Permanent tokens (transition)

Tickets created before this release, and tickets created by agents, inbound
email or the customer API, carry a permanent guest token. While
`escalated.guest-access.legacy-tokens` is `true` (the default), that token keeps
opening its ticket on the token routes when no bearer is sent, until a grant is
issued for the ticket, which retires it. Set it to `false` once old links have
aged out. Then only grants open tickets, as in Laravel.

## Mobile guest API

The JSON routes a mobile app uses for guests, under
`/escalated/api/v1/mobile/guest`. The canonical contract is
[domain-model/mobile-guest-api.md](https://github.com/escalated-dev/escalated-developer-context/blob/main/domain-model/mobile-guest-api.md);
escalated-laravel is the port of record.

| Method | Path | Guest budgets (per IP, per minute) |
|---|---|---|
| POST | `/verification` | requests + tickets |
| POST | `/lookup` | requests + replies |
| POST | `/tickets` | requests + tickets |
| GET | `/tickets/{token}` | requests |
| POST | `/tickets/{token}/replies` | requests + replies |

- `verification` and `lookup` are the widget/guest handlers above, mounted
  again: same request, same answer, same codes. A `ticket` or `lookup` code, and
  lookup itself, answer 403 while the `guest_tickets_enabled` setting is off; a
  `chat` code answers 403 while the widget is off.
- `POST /tickets` takes JSON `{name, email, subject, description,
  verification_id, verification_code, priority?, department_id?}`. Checks run
  in order: guest tickets off (403), field validation (422), the `ticket` code
  (422 on `verification_code`; nothing is created). The ticket is filed on
  channel `web` with the lower-cased email, and answers
  `201 {data: <mobile ticket>, message: "Ticket created."}` with a grant in
  `data.guest_access_token` and `data.guest_access_expires_at`. Status,
  assignee, metadata and external reference cannot be set; unknown fields are
  ignored. If filing fails, the code is handed back.
- `{token}` is the grant itself, not the ticket reference. Any grant from
  ticket creation or lookup, on any surface, works here, and a mobile grant
  works on the widget and guest routes (as `Authorization: Bearer`). A forged,
  malformed, over-long, expired, rotated or revoked grant, a permanent legacy
  token and a chat token are all a **404**.
- `GET /tickets/{token}` answers `{data: <mobile ticket>}` with the public
  replies, newest first.
- `POST /tickets/{token}/replies` takes `{email, body}`: 404 for a bad grant,
  422 `{"message": "This ticket is closed."}` on a closed ticket, 422 for
  validation, 403 when `email` (trimmed, any case) is not the ticket's guest
  email, else a public reply from the requester and `201 {"message": "Reply
  sent."}`.
- The mobile ticket object is allow-listed (`MobileGuestTickets`): `metadata` is
  always `{}`, tags, activities and followers are empty, staff appear as
  `{id: 0, name, email: ""}`, internal notes never appear.
- `requests` is `escalated.guest-rate-limit.requests-per-minute` (default 60);
  `tickets` and `replies` are the existing budgets, shared with the widget and
  guest routes. Over budget is `429` with `Retry-After`.
- Every answer, errors and 429s included, carries `Cache-Control: private,
  no-store` and `Referrer-Policy: no-referrer`. Hosts should keep the `{token}`
  path segment out of their access logs.

## Differences from Laravel

- There is no browser guest portal in this package. The `/verification` and
  `/lookup` routes live on the widget, guest and mobile guest prefixes.
- This package has no REST API on/off switch, so the mobile guest routes are
  always mounted next to `/escalated/api/v1/auth/*`. There is no API-layer
  limiter either (no `X-RateLimit-*` headers); the guest budgets apply. The
  replies budget defaults to 10 per minute (Laravel 30).
- The mobile routes take JSON only: no `attachments[]` (multipart is not
  accepted), and reply attachments are listed with `url: null`, because there
  is no guest-authorized download route yet.
- Priorities are `low`, `medium`, `high` and `urgent`; this package has no
  `critical`, which is a 422. Status labels are this package's statuses
  (Open, Pending, In Progress, Resolved, Closed, Snoozed, Merged).
- The verification answer's `message` is this package's existing wording.
- The guest identity policy settings are not applied to guest tickets here, on
  any surface.
- The chat token is a random token rather than a sealed grant. Like Laravel's
  chat grant it expires `escalated.guest-access.ttl-minutes` after the chat
  starts and only its SHA-256 is stored; a ticket link does not rotate it.
- Lookup matches the ticket reference only; there is no `external_reference`.
- Claim-and-release replaces Laravel's single transaction around the code and
  the ticket.
