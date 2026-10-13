package dev.escalated.security;

import jakarta.persistence.EntityManager;

import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public class TicketAccessRepository<T, ID> extends SimpleJpaRepository<T, ID> {
    private final JpaEntityInformation<T, ?> info;
    private final EntityManager manager;

    public TicketAccessRepository(JpaEntityInformation<T, ?> info, EntityManager manager) {
        super(info, manager);
        this.info = info;
        this.manager = manager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<T> findById(ID id) {
        if (!TicketAccessContext.restricted()
                || !TicketAccessSql.TABLES.containsKey(info.getJavaType().getSimpleName())) {
            return super.findById(id);
        }
        var builder = manager.getCriteriaBuilder();
        var query = builder.createQuery(info.getJavaType());
        var root = query.from(info.getJavaType());
        query.select(root).where(builder.equal(root.get(info.getIdAttribute().getName()), id));
        return manager.createQuery(query).getResultStream().findFirst();
    }
}
