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

## Differences from Laravel

- There is no browser guest portal or mobile API in this package. The
  `/verification` and `/lookup` routes live on the widget and guest prefixes.
- Chats keep their server-issued session token rather than an expiring chat
  grant.
- Lookup matches the ticket reference only; there is no `external_reference`.
- Claim-and-release replaces Laravel's single transaction around the code and
  the ticket.
