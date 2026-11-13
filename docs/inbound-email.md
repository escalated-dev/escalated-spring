# Inbound email

## Inbound email

Point your Postmark, Mailgun, or AWS SES (via SNS HTTP subscription) inbound webhook at:

```
POST /escalated/webhook/email/inbound?adapter=postmark
POST /escalated/webhook/email/inbound?adapter=mailgun
POST /escalated/webhook/email/inbound?adapter=ses
```

The adapter can be selected via the query parameter or the `X-Escalated-Adapter` header. Your provider must attach the shared secret as an `X-Escalated-Inbound-Secret` header, which is compared with `MessageDigest.isEqual` (timing-safe).

Because the webhook requires `escalated.email.inbound-secret`, outbound mail carries the signed `Reply-To` address (`reply+{id}.{hmac8}@domain`), and that address is the only thing that links an inbound message to an existing ticket. When the service is called without a secret configured, it falls back to the canonical `Message-ID` headers and subject-reference tags (e.g. `[ESC-00001]`).

A matched message becomes a reply only when its `From` address is the ticket's requester (compared case-insensitively). The reply is posted as the requester, never as whoever the `From` header names, and it reopens a resolved or closed ticket. Agents reply in the app, not by email. Mail from anyone else, including an address that belongs to an agent, opens a new ticket of its own and leaves the matched ticket untouched. Unmatched messages with real content create a new ticket; SNS subscription confirmations and empty body+subject messages are skipped.

`From` can still be forged, so also have your inbound provider enforce SPF, DKIM and DMARC.

See the [inbound email docs](https://docs.escalated.dev/inbound-email) for provider setup, the response shape, and a ready-to-paste curl test recipe.
