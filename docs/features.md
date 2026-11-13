# Features

## Features

1. **Ticket CRUD** -- Full lifecycle management with statuses, priorities, and assignments
2. **SLA Policies** -- Configurable SLAs with business hours support and holiday calendars
3. **Automations** -- Time-based rules for auto-closing resolved tickets and auto-assignment
4. **Escalation Rules** -- Condition-based escalation (SLA breach, age, no response) with reassignment, priority and department changes
5. **Macros & Canned Responses** -- Pre-defined actions and response templates for agents
6. **Custom Fields** -- Extensible ticket data with multiple field types
7. **Knowledge Base** -- Articles and categories with search, view counts, and feedback
8. **Webhooks** -- HMAC-signed webhook delivery with retry logic
9. **API Tokens** -- SHA-256 hashed token authentication for API access
10. **Roles & Permissions** -- Granular role-based access control, enforced behind the install-level `ESCALATED_ROLES_PERMISSIONS` switch ([access control](access-control.md))
11. **Audit Logging** -- Complete audit trail for all actions
12. **Import System** -- Bulk ticket import from structured data
13. **Side Conversations** -- Private threaded conversations within tickets
14. **Ticket Merging & Linking** -- Merge duplicate tickets and link related ones
15. **Ticket Splitting** -- Split complex tickets into separate issues
16. **Ticket Snooze** -- Snooze tickets with automatic wake-up via `@Scheduled`
17. **Email Threading** -- Branded HTML email templates via Thymeleaf with proper Message-ID threading
18. **Saved Views** -- Custom filtered/sorted ticket views per agent
19. **Widget API** -- Public REST endpoints for embedding a support widget
20. **Real-time Broadcasting** -- WebSocket via STOMP/SockJS (opt-in)
21. **Capacity Management** -- Track and enforce agent workload limits
22. **Skill-based Routing** -- Route tickets to agents with matching skills
23. **CSAT Ratings** -- Customer satisfaction surveys with token-based access
24. **2FA (TOTP)** -- Time-based one-time passwords for staff, with a challenge after sign-in, behind the install-level `ESCALATED_TWO_FACTOR` switch ([access control](access-control.md))
25. **Guest Access** -- Token-based ticket access without authentication
26. **Inbound Email** -- Single webhook endpoint with Postmark + Mailgun + AWS SES parsers, signed Reply-To ticket resolution, and replies accepted only from the ticket's requester
27. **Plugins** -- Native Java plugins and SDK plugins (through the Node plugin runtime), with Admin > Plugins, plugin pages, endpoints, webhooks and domain events ([docs](plugins.md))
28. **Single Sign-On** -- SAML 2, OAuth 2 / OIDC and JWT sign-in behind the install-level `ESCALATED_SSO` switch ([access control](access-control.md))
