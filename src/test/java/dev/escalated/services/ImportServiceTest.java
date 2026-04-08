package dev.escalated.services;

import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private TicketService ticketService;
    @Mock
    private AuditLogService auditLogService;

    private ImportService importService;

    @BeforeEach
    void setUp() {
        importService = new ImportService(ticketService, auditLogService);
    }

    @Test
    void importTickets_shouldImportValidRows() {
        when(ticketService.create(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new Ticket());

        List<Map<String, String>> rows = List.of(
                Map.of("subject", "Issue 1", "body", "Description 1",
                        "requester_name", "User 1", "requester_email", "user1@test.com"),
                Map.of("subject", "Issue 2", "body", "Description 2",
                        "requester_name", "User 2", "requester_email", "user2@test.com")
        );

        ImportService.ImportResult result = importService.importTickets(rows, "admin@test.com");

        assertEquals(2, result.imported());
        assertEquals(0, result.skipped());
    }

    @Test
    void importTickets_shouldSkipRowsWithMissingSubject() {
        List<Map<String, String>> rows = List.of(
                Map.of("body", "No subject here", "requester_email", "user@test.com")
        );

        ImportService.ImportResult result = importService.importTickets(rows, "admin@test.com");

        assertEquals(0, result.imported());
        assertEquals(1, result.skipped());
        assertEquals(1, result.errors().size());
    }
}
