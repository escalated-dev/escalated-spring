package dev.escalated.security;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.AuditLog;
import dev.escalated.models.BaseEntity;
import dev.escalated.models.ChatSession;
import dev.escalated.models.DeferredWorkflowJob;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketFollower;
import dev.escalated.repositories.AgentProfileRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityNotFoundException;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Component
public class TicketAccess {
    private final EscalatedProperties properties;
    private final EscalatedAuthorization authorization;
    private final AgentProfileRepository agents;
    private final ObjectProvider<TicketAccountResolver> resolvers;
    private final ObjectProvider<EntityManagerFactory> factories;

    public TicketAccess(
            EscalatedProperties properties,
            EscalatedAuthorization authorization,
            AgentProfileRepository agents,
            ObjectProvider<TicketAccountResolver> resolvers,
            @Qualifier("escalatedEntityManagerFactory")
                    ObjectProvider<EntityManagerFactory> factories) {
        this.properties = properties;
        this.authorization = authorization;
        this.agents = agents;
        this.resolvers = resolvers;
        this.factories = factories;
    }

    public boolean enabled() {
        return "department".equals(properties.getVisibility()) || accountScope();
    }

    private boolean accountScope() {
        return properties.getAccounts().isEnabled() && properties.getAccounts().isScopeAgents();
    }

    public Scope open(Authentication actor) {
        TicketAccessContext.State previous = TicketAccessContext.CURRENT.get();
        TicketAccessContext.CURRENT.remove();
        try {
            boolean restricted =
                    enabled() && authorization.isAgent(actor) && !authorization.isAdmin(actor);
            AgentProfile profile =
                    restricted ? agents.findByEmail(actor.getName()).orElse(null) : null;
            TicketAccountResolver resolver =
                    restricted && accountScope() ? resolvers.getIfAvailable() : null;
            TicketAccount account =
                    restricted && accountScope() && resolver != null && profile != null
                            ? resolver.forAgent(profile)
                            : null;
            TicketAccessContext.CURRENT.set(
                    new TicketAccessContext.State(
                            this,
                            restricted && "department".equals(properties.getVisibility()),
                            restricted && accountScope(),
                            profile == null ? -1L : profile.getId(),
                            account));
            return new Scope(previous);
        } catch (RuntimeException ex) {
            restore(previous);
            throw ex;
        }
    }

    public <T> T runAs(Authentication actor, Supplier<T> action) {
        try (Scope ignored = open(actor)) {
            return action.get();
        }
    }

    /** Explicit trusted bypass. Never expose this through request parameters. */
    public <T> T withoutRestriction(Supplier<T> action) {
        TicketAccessContext.State previous = TicketAccessContext.CURRENT.get();
        TicketAccessContext.CURRENT.remove();
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(TicketAccessContext.State previous) {
        if (previous == null) {
            TicketAccessContext.CURRENT.remove();
        } else {
            TicketAccessContext.CURRENT.set(previous);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final TicketAccessContext.State previous;

        private Scope(TicketAccessContext.State previous) {
            this.previous = previous;
        }

        public void close() {
            restore(previous);
        }
    }

    public boolean canAssign(Ticket ticket, AgentProfile candidate) {
        if (!enabled()) {
            return true;
        }
        if (candidate == null || candidate.getId() == null) {
            return false;
        }
        return readCurrent(
                session ->
                        eligible(
                                ticket,
                                session.get(AgentProfile.class, candidate.getId()),
                                session));
    }

    private boolean eligible(
            Ticket ticket, AgentProfile agent, org.hibernate.StatelessSession session) {
        if (agent == null || !agent.isActive() || (!agent.isAgent() && !agent.isAdmin())) {
            return false;
        }
        if (agent.isAdmin()) {
            return true;
        }
        if ("department".equals(properties.getVisibility())) {
            Long department =
                    ticket.getDepartment() == null ? null : ticket.getDepartment().getId();
            if (department == null) {
                return false;
            }
            boolean primary =
                    agent.getDepartment() != null
                            && department.equals(agent.getDepartment().getId());
            if (!primary
                    && session.createQuery(
                                            "SELECT count(m) FROM DepartmentAgent m WHERE"
                                                + " m.departmentId = :department AND m.agentId ="
                                                + " :agent",
                                            Long.class)
                                    .setParameter("department", department)
                                    .setParameter("agent", agent.getId())
                                    .getSingleResult()
                            == 0) {
                return false;
            }
        }
        if (accountScope()) {
            TicketAccountResolver resolver = resolvers.getIfAvailable();
            TicketAccount account = resolver == null ? null : resolver.forAgent(agent);
            return account != null
                    && account.type().equals(ticket.getAccountType())
                    && account.id().equals(ticket.getAccountId());
        }
        return true;
    }

    /** Reload both identities immediately before notification or private broadcast delivery. */
    public boolean canReceiveStaff(Long ticketId, Long agentId) {
        if (ticketId == null || agentId == null) {
            return false;
        }
        return readCurrent(
                session -> {
                    Ticket ticket = session.get(Ticket.class, ticketId);
                    AgentProfile agent = session.get(AgentProfile.class, agentId);
                    return ticket != null
                            && (enabled() ? eligible(ticket, agent, session) : agent != null);
                });
    }

    /**
     * Read using the current transaction's connection, with no identity cache or auto-flush. A
     * recipient check must not flush pending actor writes while temporarily unrestricted.
     */
    private <T> T readCurrent(
            java.util.function.Function<org.hibernate.StatelessSession, T> action) {
        EntityManagerFactory factory = factories.getObject();
        EntityManager manager = EntityManagerFactoryUtils.getTransactionalEntityManager(factory);
        boolean owned = manager == null;
        if (owned) {
            manager = factory.createEntityManager();
        }
        try {
            return manager.unwrap(Session.class)
                    .doReturningWork(
                            connection -> {
                                try (var session =
                                        factory.unwrap(org.hibernate.SessionFactory.class)
                                                .withStatelessOptions()
                                                .connection(connection)
                                                .initialCacheMode(org.hibernate.CacheMode.IGNORE)
                                                .readOnly(true)
                                                .openStatelessSession()) {
                                    return withoutRestriction(() -> action.apply(session));
                                }
                            });
        } finally {
            if (owned) {
                manager.close();
            }
        }
    }

    public void assertAssignable(Ticket ticket, AgentProfile agent) {
        if (agent != null && !canAssign(ticket, agent)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Agent cannot access this ticket");
        }
    }

    public static void assertVisible(BaseEntity entity) {
        TicketAccessContext.State state = TicketAccessContext.CURRENT.get();
        if (!TicketAccessContext.restricted() || entity == null || entity.getId() == null) {
            return;
        }
        String name = Hibernate.getClass(entity).getSimpleName();
        if (!TicketAccessSql.TABLES.containsKey(name)) {
            return;
        }
        if (!state.access().storedVisible(name, entity.getId())) {
            throw new EntityNotFoundException("Ticket resource not found");
        }
    }

    public static void assertWrite(BaseEntity entity) {
        if (!TicketAccessContext.restricted()) {
            return;
        }
        assertVisible(entity);
        if (entity instanceof Ticket ticket) {
            TicketAccessContext.CURRENT
                    .get()
                    .access()
                    .assertAssignable(ticket, ticket.getAssignedAgent());
            if (ticket.getId() == null
                    && !TicketAccessContext.CURRENT.get().access().newTicketVisible(ticket)) {
                throw new EntityNotFoundException("Ticket resource not found");
            }
        } else {
            // Check every parent, including both sides of links and optional attachment parents.
            for (var method : Hibernate.getClass(entity).getMethods()) {
                if (method.getParameterCount() == 0
                        && method.getName().startsWith("get")
                        && BaseEntity.class.isAssignableFrom(method.getReturnType())) {
                    try {
                        assertVisible((BaseEntity) method.invoke(entity));
                    } catch (ReflectiveOperationException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
            }
            Long ticketId =
                    entity instanceof TicketFollower f
                            ? f.getTicketId()
                            : entity instanceof ChatSession c
                                    ? c.getTicketId()
                                    : entity instanceof DeferredWorkflowJob d
                                            ? d.getTicketId()
                                            : entity instanceof AuditLog a
                                                            && "Ticket".equals(a.getEntityType())
                                                    ? a.getEntityId()
                                                    : null;
            if (ticketId != null
                    && !TicketAccessContext.CURRENT
                            .get()
                            .access()
                            .storedVisible("Ticket", ticketId)) {
                throw new EntityNotFoundException("Ticket resource not found");
            }
        }
    }

    /** Also protects collections that were loaded before entering an actor's scope. */
    public static <T extends BaseEntity> java.util.List<T> visibleCollection(
            java.util.List<T> entities) {
        if (!TicketAccessContext.restricted()
                || entities == null
                || !Hibernate.isInitialized(entities)) {
            return entities;
        }
        return entities.stream()
                .filter(
                        entity -> {
                            try {
                                assertVisible(entity);
                                return true;
                            } catch (EntityNotFoundException ex) {
                                return false;
                            }
                        })
                .toList();
    }

    private boolean newTicketVisible(Ticket ticket) {
        long id = new TicketAccessContext.AgentId().get();
        AgentProfile agent = agents.findById(id).orElse(null);
        return agent != null && canAssign(ticket, agent);
    }

    private boolean storedVisible(String entity, Long id) {
        EntityManagerFactory factory = factories.getObject();
        EntityManager manager = EntityManagerFactoryUtils.getTransactionalEntityManager(factory);
        boolean owned = manager == null;
        if (owned) {
            manager = factory.createEntityManager();
        }
        try {
            return manager.unwrap(Session.class)
                    .doReturningWork(
                            connection -> {
                                boolean mysql =
                                        connection
                                                .getMetaData()
                                                .getDatabaseProductName()
                                                .contains("MySQL");
                                String condition =
                                        (mysql ? TicketAccessSql.MYSQL : TicketAccessSql.STANDARD)
                                                .get(entity)
                                                .replace("{row}", "v");
                                var matcher = Pattern.compile(":([A-Za-z]+)").matcher(condition);
                                var values = new ArrayList<Object>();
                                StringBuffer bound = new StringBuffer();
                                while (matcher.find()) {
                                    values.add(TicketAccessContext.parameter(matcher.group(1)));
                                    matcher.appendReplacement(bound, "?");
                                }
                                matcher.appendTail(bound);
                                String sql =
                                        "select 1 from "
                                                + TicketAccessSql.TABLES.get(entity)
                                                + " v where v.id = ? and ("
                                                + bound
                                                + ")";
                                try (var statement = connection.prepareStatement(sql)) {
                                    statement.setLong(1, id);
                                    for (int i = 0; i < values.size(); i++) {
                                        statement.setObject(i + 2, values.get(i));
                                    }
                                    try (var result = statement.executeQuery()) {
                                        return result.next();
                                    }
                                }
                            });
        } finally {
            if (owned) {
                manager.close();
            }
        }
    }
}
