package dev.escalated.services;

import dev.escalated.contracts.TicketSubject;

/**
 * Host-provided bean that resolves a polymorphic subject type/id pair to a
 * {@link TicketSubject} for API serialization, or returns {@code null} when
 * the entity is missing or not presentable.
 */
@FunctionalInterface
public interface TicketSubjectResolver {

    TicketSubject resolve(String subjectType, String subjectId);
}
