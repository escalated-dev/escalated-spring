package dev.escalated.security;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.Ticket;

/** Optional host bean. Null means no account; a missing agent account fails closed. */
public interface TicketAccountResolver {
    default TicketAccount forRequester(Ticket ticket) {
        return null;
    }

    default TicketAccount forSubject(Ticket ticket, String subjectType, String subjectId) {
        return null;
    }

    default TicketAccount forAgent(AgentProfile agent) {
        return null;
    }
}
