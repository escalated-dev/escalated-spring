package dev.escalated.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import dev.escalated.models.AuditLog;
import dev.escalated.repositories.AuditLogRepository;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REDACTED_VALUE = "[REDACTED]";
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password",
            "secret",
            "token",
            "apikey",
            "authorization",
            "credential",
            "credentials"
    );

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
        entry.setOldValues(redactSensitiveValues(oldValues));
        entry.setNewValues(redactSensitiveValues(newValues));
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

    private static String redactSensitiveValues(String values) {
        if (values == null || values.isBlank()) {
            return values;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(values);
            redactNode(root);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            return values;
        }
    }

    private static void redactNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveField(field.getKey())) {
                    objectNode.set(field.getKey(), TextNode.valueOf(REDACTED_VALUE));
                } else {
                    redactNode(field.getValue());
                }
            }
            return;
        }

        if (node instanceof ArrayNode arrayNode) {
            for (JsonNode child : arrayNode) {
                redactNode(child);
            }
        }
    }

    private static boolean isSensitiveField(String fieldName) {
        String normalized = fieldName.toLowerCase().replaceAll("[^a-z0-9]", "");
        return SENSITIVE_FIELDS.stream().anyMatch(normalized::contains);
    }
}
