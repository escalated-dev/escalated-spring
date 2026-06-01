package dev.escalated.services;

import dev.escalated.models.AuditLog;
import dev.escalated.repositories.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository);
    }

    @Test
    void log_shouldCreateEntry() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog al = inv.getArgument(0);
            al.setId(1L);
            return al;
        });

        AuditLog result = auditLogService.log("create", "Ticket", 1L, "admin@test.com", null, null);

        assertNotNull(result);
        assertEquals("create", result.getAction());
        assertEquals("Ticket", result.getEntityType());
        assertEquals(1L, result.getEntityId());
    }

    @Test
    void log_shouldRedactSensitiveJsonValues() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        String oldValues = "{\"email\":\"agent@test.com\",\"password\":\"old-password\"}";
        String newValues = "{\"api_key\":\"key-123\",\"profile\":{\"remember_token\":\"token-123\",\"name\":\"Agent\"}}";

        AuditLog result = auditLogService.log("update", "User", 1L, "admin@test.com", oldValues, newValues);

        assertEquals("{\"email\":\"agent@test.com\",\"password\":\"[REDACTED]\"}", result.getOldValues());
        assertEquals(
                "{\"api_key\":\"[REDACTED]\",\"profile\":{\"remember_token\":\"[REDACTED]\",\"name\":\"Agent\"}}",
                result.getNewValues());
    }

    @Test
    void log_shouldRedactSensitiveValuesInArrays() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        String newValues = "{\"webhooks\":[{\"secret\":\"hook-secret\",\"url\":\"https://example.test\"}]}";

        AuditLog result = auditLogService.log("update", "Webhook", 1L, "admin@test.com", null, newValues);

        assertEquals(
                "{\"webhooks\":[{\"secret\":\"[REDACTED]\",\"url\":\"https://example.test\"}]}",
                result.getNewValues());
    }

    @Test
    void log_shouldPreserveNonJsonAuditValues() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog result = auditLogService.log("update", "Ticket", 1L, "admin@test.com", "status changed", null);

        assertEquals("status changed", result.getOldValues());
    }

    @Test
    void logWithIp_shouldIncludeIpAddress() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog al = inv.getArgument(0);
            al.setId(1L);
            return al;
        });

        AuditLog result = auditLogService.logWithIp("update", "Ticket", 1L,
                "admin@test.com", "192.168.1.1", null, null);

        assertEquals("192.168.1.1", result.getActorIp());
    }
}
