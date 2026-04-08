package dev.escalated.services;

import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final TicketService ticketService;
    private final AuditLogService auditLogService;

    public ImportService(TicketService ticketService, AuditLogService auditLogService) {
        this.ticketService = ticketService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ImportResult importTickets(List<Map<String, String>> rows, String actorEmail) {
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            try {
                String subject = row.get("subject");
                String body = row.getOrDefault("body", "");
                String requesterName = row.getOrDefault("requester_name", "Unknown");
                String requesterEmail = row.getOrDefault("requester_email", "unknown@example.com");
                String priorityStr = row.getOrDefault("priority", "MEDIUM");

                if (subject == null || subject.isBlank()) {
                    errors.add("Row " + (i + 1) + ": missing subject");
                    skipped++;
                    continue;
                }

                TicketPriority priority;
                try {
                    priority = TicketPriority.valueOf(priorityStr.toUpperCase());
                } catch (IllegalArgumentException ex) {
                    priority = TicketPriority.MEDIUM;
                }

                ticketService.create(subject, body, requesterName, requesterEmail, priority, null);
                imported++;
            } catch (Exception ex) {
                log.error("Import error at row {}: {}", i + 1, ex.getMessage());
                errors.add("Row " + (i + 1) + ": " + ex.getMessage());
                skipped++;
            }
        }

        auditLogService.log("import", "Ticket", null, actorEmail, null,
                "Imported: " + imported + ", Skipped: " + skipped);

        return new ImportResult(imported, skipped, errors);
    }

    public record ImportResult(int imported, int skipped, List<String> errors) {
    }
}
