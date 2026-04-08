package dev.escalated.services;

import dev.escalated.models.AuditLog;
import dev.escalated.repositories.AuditLogRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog log(String action, String entityType, Long entityId,
                        String actorEmail, String oldValues, String newValues) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setActorEmail(actorEmail);
        entry.setOldValues(oldValues);
        entry.setNewValues(newValues);
        return auditLogRepository.save(entry);
    }

    @Transactional
    public AuditLog logWithIp(String action, String entityType, Long entityId,
                              String actorEmail, String actorIp, String oldValues, String newValues) {
        AuditLog entry = log(action, entityType, entityId, actorEmail, oldValues, newValues);
        entry.setActorIp(actorIp);
        return auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findAll(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findByEntity(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findByActor(String actorEmail, Pageable pageable) {
        return auditLogRepository.findByActorEmailOrderByCreatedAtDesc(actorEmail, pageable);
    }
}
