# Ticket access and routing

All restrictions are opt-in. Existing installations keep unrestricted agent access,
unpopulated account fields and untargeted SLAs until configured otherwise.

```yaml
escalated:
  visibility: all # all | department
  accounts:
    enabled: false
    scope-agents: false
  targeted-slas: false
```

## Department visibility

Set `visibility: department`. An agent can access tickets in their primary
`AgentProfile.department` or an additional `DepartmentAgent` membership. Use
`DepartmentAgentRepository` to manage additional memberships. Tickets without a
department are hidden from non-admin agents. Administrators retain full access.
Host-owned authentication continues to use the existing agent/admin gates.

The Hibernate filter applies before pagination and aggregation, including repository
queries, ID lookups, reports, bulk updates/deletes, guest-token lookups made by an
agent, and ticket-owned replies, attachments, activities, side conversations,
links, followers and workflow records. Both ends of a link must be visible; all
present attachment parents must be visible. Preloaded writes check the persisted
identity before accepting a changed parent. Repository ID lookups also check the
scope when an entity is already in the persistence context.

The package security chains install and clear the actor around each HTTP request.
A bearer token's owner takes precedence over a session user. Anonymous guest
requests retain their existing token checks. Host code invoking package services
outside these chains should explicitly supply its actor:

```java
ticketAccess.runAs(authentication, () -> ticketService.findAll(pageable));
```

Actorless console work and background jobs remain unrestricted. The context is
not inherited by worker threads. Trusted internal code can use
`ticketAccess.withoutRestriction(...)`; never expose this bypass as a request
parameter. Native SQL and JDBC are trusted interfaces and do not acquire ORM
filters. Use package repositories for actor-facing reads, and package services for
assignment, department moves and other lifecycle operations. Do not share an
EntityManager or cached entities between users, or disable the Hibernate filter.

The package supports a separate `escalated.datasource` connection. Membership
queries only use package tables, so host user IDs can be opaque strings and no
cross-database join to a host user table is needed. Table names keep the existing
fixed `escalated_` prefix.

## Account identity

Enable `accounts.enabled` and provide a `TicketAccountResolver` bean. Return an
exact `(type, id)` pair, or `null` when no account is known:

```java
@Bean
TicketAccountResolver ticketAccountResolver() {
    return new TicketAccountResolver() {
        public TicketAccount forRequester(Ticket ticket) {
            return accountForEmail(ticket.getRequesterEmail());
        }
        public TicketAccount forSubject(Ticket ticket, String type, String id) {
            return accountForSubject(type, id);
        }
        public TicketAccount forAgent(AgentProfile agent) {
            return accountForStaff(agent.getUserId());
        }
    };
}
```

The example lookup methods belong to the host. Requester resolution runs before
SLA selection and creation events. Subject attachment fills an empty pair using
a conditional database update; it never replaces an existing account. A split
copies the account and ticket type. Client-supplied account fields are not accepted
by the ticket creation endpoint.

Set `accounts.scope-agents: true` to restrict each agent to the resolver's one
account. A missing account hides all tickets from that agent. Account and
department restrictions intersect, and admins bypass both. IDs retain case,
leading zeroes and trailing spaces: `007` and `7` are different accounts.

Agent and admin ticket lists accept `query`, `department_id`, `ticket_type`, and a
complete `account_type` / `account_id` pair. Filters only narrow visibility. Supply
`ticketType` to the extended `TicketService.create` method to set a stream before
creation workflows and SLA selection.

## Rules and SLAs

Workflows and escalation rules accept the same condition shape:

```json
{
  "all": [
    {"field": "ticket_type", "operator": "equals", "value": "delivery"},
    {"field": "account", "operator": "equals", "value": {"type": "Company", "id": "007"}}
  ]
}
```

`account` supports `equals` and `not_equals` on complete pairs. Missing or malformed
pairs never match, including negation. Scalar `account_type` and `account_id`
conditions also use exact string comparisons. The existing built-in automation
tasks accept JSON conditions through
`escalated.automation-conditions.auto-close` and `.auto-assign`; unset conditions
preserve their existing behavior. The auto-assignment task remains the package's
existing host extension point. Workflows support `set_type` and
`set_department` / `change_department` actions.

With `targeted-slas: true`, active policies of the ticket's priority are selected
by specificity: department plus type, department, type, then untargeted. The
lowest policy ID breaks ties. Targeted policies are ignored when the flag is off.
`TicketService.changeDepartment` preserves existing SLA deadlines and removes an
assignee who can no longer access the destination. Explicitly call
`SlaService.applySlaPolicy` to reselect a policy; in targeted mode its deadlines
use the ticket's creation time, including business-hour calculations.

## Assignment and delivery

Manual and workflow assignment reject ineligible agents. Skill routing filters
its candidates. Use `AgentService.findBestAvailableAgentForTicket` for a complete
ticket; the older department-only helper returns no candidate under restrictions
because it cannot evaluate account access. Chat routing validates assignment too.

Staff reply email checks the recipient's current access immediately before sending.
Host follower, mention and queued-notification delivery should call
`ticketAccess.canReceiveStaff(ticketId, agentProfileId)` at delivery time. Internal
replies are not emailed to requesters.

When restrictions are enabled, chat events are not published to the existing shared
STOMP topics. Provide an `AuthorizedTicketBroadcaster` bean to deliver them through
private, authenticated channels. Its `deliver(ticketId, destination, payload)`
implementation must authorize every recipient at delivery time, including after
membership changes. Without that bean, chat persistence and HTTP polling continue
and real-time delivery is suppressed. Unrestricted mode keeps existing broadcasts.

## Migration and verification

V12 adds nullable account/type fields, a compound account index, SLA targets,
escalation conditions and the additional-membership table. Existing rows remain
unchanged. Previously released Flyway scripts retain their original checksums.
Installs that use manual schema management must apply the new migration themselves.

Tests cover H2, PostgreSQL and MySQL, populated V11 upgrades, separate package
databases, exact account identities, bearer/session precedence, collection loads,
preloaded writes, search, reports, bulk actions, widget access and routing.
