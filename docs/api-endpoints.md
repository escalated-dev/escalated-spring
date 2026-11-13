# API endpoints

## API Endpoints

### Admin (`/escalated/api/admin/`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/tickets` | List tickets (paginated, filterable) |
| POST | `/tickets` | Create ticket |
| GET | `/tickets/{id}` | Get ticket |
| PUT | `/tickets/{id}` | Update ticket |
| POST | `/tickets/{id}/assign` | Assign ticket |
| POST | `/tickets/{id}/status` | Change status |
| POST | `/tickets/{id}/snooze` | Snooze ticket |
| POST | `/tickets/{id}/merge` | Merge tickets |
| POST | `/tickets/{id}/split` | Split ticket |
| POST | `/tickets/{id}/department` | Change department (`department_id`) |
| POST | `/tickets/{id}/tags` | Add a tag (`tag`) |
| DELETE | `/tickets/{id}/tags/{tagId}` | Remove a tag |
| DELETE | `/tickets/{id}` | Delete ticket |
| GET/POST | `/departments` | CRUD departments |
| GET/POST | `/agents` | CRUD agents |
| GET/POST | `/webhooks` | CRUD webhooks |
| GET/POST | `/workflows` | List / create workflows (the shared builder's body, see the [workflow admin contract](https://github.com/escalated-dev/escalated-developer-context/blob/main/domain-model/workflow-admin-contract.md)) |
| GET | `/workflows/options` | Builder options: `trigger_events`, `action_types`, `operators`, `condition_fields` |
| GET/PUT/DELETE | `/workflows/{id}` | Show / update / delete a workflow |
| POST | `/workflows/{id}/toggle` | Enable or disable |
| POST | `/workflows/reorder` | `{"workflow_ids": [3, 1, 2]}` |
| GET | `/workflows/{id}/logs` | Run log |
| GET/POST | `/automations` | List / create time-based automations (`name`, `conditions`, `actions`, `active`, `repeat_after_hours`) |
| GET | `/automations/options` | Condition fields, operators and action types the runner understands |
| GET/PUT/DELETE | `/automations/{id}` | Show / update / delete an automation |
| POST | `/automations/{id}/toggle` | Enable or disable |
| GET/POST | `/roles` | CRUD roles |
| GET/POST | `/custom-fields` | CRUD custom fields; `POST /custom-fields/reorder` (see [docs/custom-fields.md](custom-fields.md)) |
| GET/POST | `/custom-objects` | CRUD custom objects; `/custom-objects/{id}/records` for their records |
| GET/POST | `/settings` | Manage settings |
| GET/PUT | `/settings/public-tickets` | Runtime guest-policy mode (unassigned / guest_user / prompt_signup). See [docs.escalated.dev/public-tickets](https://docs.escalated.dev/public-tickets). |
| GET | `/audit-logs` | View audit logs |
| POST | `/import/tickets` | Import tickets |
| GET/POST | `/kb/categories` | Manage KB categories |
| GET/POST | `/kb/articles` | Manage KB articles |

### Agent (`/escalated/api/agent/`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/tickets` | List assigned/filtered tickets |
| GET | `/tickets/{id}` | View ticket |
| POST | `/tickets/{id}/replies` | Add reply |
| POST | `/tickets/{id}/macro/{macroId}` | Apply macro |
| POST | `/tickets/{id}/side-conversations` | Create side conversation |
| POST | `/tickets/{id}/links` | Link tickets |
| GET/POST | `/saved-views` | Manage saved views |
| GET/POST | `/canned-responses` | Manage canned responses |

### Customer (`/escalated/api/customer/`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/tickets?email=` | List customer tickets |
| POST | `/tickets` | Create ticket |
| POST | `/tickets/{id}/replies` | Add reply |

### Widget (`/escalated/api/widget/`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/config` | Widget configuration |
| GET | `/embed.js` | Standalone widget script |
| POST | `/verification` | Email a verification code (`email`, `purpose`) |
| POST | `/tickets` | Create ticket (public; needs a `ticket` code; optional `metadata` object) |
| POST | `/lookup` | Find a ticket by reference and verified email; answers fresh grants |
| GET | `/tickets/{reference}` | Ticket status, with `Authorization: Bearer <grant>` (or a permanent token in the path while `legacy-tokens` is on) |
| POST | `/tickets/{reference}/replies` | Reply, with a grant or a permanent token |
| GET | `/kb/search?query=` | Search knowledge base |
| POST | `/csat/{token}` | Submit satisfaction rating |

### Guest (`/escalated/api/guest/`)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/verification`, `/lookup` | As on the widget |
| GET | `/tickets/{reference}` | View ticket (grant or permanent token, as on the widget) |
| GET | `/tickets/{reference}/replies` | View replies |
| POST | `/tickets/{reference}/replies` | Add reply |

### Guest endpoint rate limits

Guest ticket creation (`POST /escalated/api/widget/tickets`) and widget chat starts
(`POST /escalated/api/widget/chat/start`, which files a ticket too) share a limit of
5 per client IP per minute, and guest replies (`POST .../tickets/{token}/replies` under
both `/widget` and `/guest`) to 10, each with its own counter. A request over the
limit gets `429` with `Retry-After`. Replies are counted before the guest token is
looked up, so wrong-token guesses count too. Tune or switch it off with
`escalated.guest-rate-limit.*`; turn it off only when you already throttle these
routes upstream.

The client IP is `HttpServletRequest#getRemoteAddr()`. **Behind a reverse proxy
or load balancer, set `server.forward-headers-strategy` (and your container's
trusted proxies)**, or every guest shares the proxy's address. Counters are kept
in memory per process; a multi-instance deployment should define a shared
`dev.escalated.services.ratelimit.GuestRateLimitStore` bean (e.g. Redis-backed).
