package dev.escalated.services;

import dev.escalated.models.Ticket;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.TicketRepository;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@Service
public class TicketSearch {
    private final TicketRepository tickets;
    private final EntityManagerFactory factory;

    public TicketSearch(
            TicketRepository tickets,
            @Qualifier("escalatedEntityManagerFactory") EntityManagerFactory factory) {
        this.tickets = tickets;
        this.factory = factory;
    }

    public Page<Ticket> find(
            String query,
            Long agentId,
            TicketStatus status,
            Long departmentId,
            String ticketType,
            String accountType,
            String accountId,
            Pageable pageable) {
        boolean mysql =
                factory.unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect()
                        instanceof MySQLDialect;
        return tickets.findAll(
                (root, criteria, cb) -> {
                    var clauses = new ArrayList<jakarta.persistence.criteria.Predicate>();
                    if (query != null && !query.isBlank()) {
                        String pattern =
                                "%"
                                        + query.toLowerCase(java.util.Locale.ROOT)
                                                .replace("!", "!!")
                                                .replace("%", "!%")
                                                .replace("_", "!_")
                                        + "%";
                        clauses.add(
                                cb.or(
                                        cb.like(cb.lower(root.get("subject")), pattern, '!'),
                                        cb.like(cb.lower(root.get("body")), pattern, '!')));
                    }
                    if (agentId != null) {
                        clauses.add(cb.equal(root.get("assignedAgent").get("id"), agentId));
                    }
                    if (status != null) {
                        clauses.add(cb.equal(root.get("status"), status));
                    }
                    if (departmentId != null) {
                        clauses.add(cb.equal(root.get("department").get("id"), departmentId));
                    }
                    if (ticketType != null) {
                        clauses.add(cb.equal(root.get("ticketType"), ticketType));
                    }
                    if (accountType != null || accountId != null) {
                        if (accountType == null
                                || accountId == null
                                || accountType.isBlank()
                                || accountId.isBlank()) {
                            clauses.add(cb.disjunction());
                        } else if (mysql) {
                            clauses.add(
                                    cb.equal(
                                            cb.function(
                                                    "binary",
                                                    byte[].class,
                                                    root.get("accountType")),
                                            accountType.getBytes(StandardCharsets.UTF_8)));
                            clauses.add(
                                    cb.equal(
                                            cb.function(
                                                    "binary", byte[].class, root.get("accountId")),
                                            accountId.getBytes(StandardCharsets.UTF_8)));
                        } else {
                            clauses.add(cb.equal(root.get("accountType"), accountType));
                            clauses.add(cb.equal(root.get("accountId"), accountId));
                        }
                    }
                    return cb.and(clauses.toArray(jakarta.persistence.criteria.Predicate[]::new));
                },
                pageable);
    }
}
