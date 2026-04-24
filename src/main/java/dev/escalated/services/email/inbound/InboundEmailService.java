package dev.escalated.services.email.inbound;

import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.services.TicketService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full inbound email pipeline:
 * <pre>parser output → router resolution → reply-on-existing or
 * create-new-ticket</pre>
 *
 * <p>Called from {@link dev.escalated.controllers.InboundEmailController}
 * after the parser normalizes the provider payload. Mirrors the
 * NestJS reference {@code InboundRouterService} and the .NET
 * {@code InboundEmailService}.
 *
 * <p>Attachment persistence is out of scope here: provider-hosted
 * attachments (Mailgun) carry their {@code downloadUrl} through to
 * {@link ProcessResult#pendingAttachmentDownloads()} so a follow-up
 * worker can fetch + persist out-of-band.
 */
@Service
public class InboundEmailService {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailService.class);

    private final InboundEmailRouter router;
    private final TicketService ticketService;

    public InboundEmailService(InboundEmailRouter router, TicketService ticketService) {
        this.router = router;
        this.ticketService = ticketService;
    }

    /**
     * Process a parsed inbound message. Returns a {@link ProcessResult}
     * carrying the outcome (matched + reply id, created new ticket
     * id, or skipped).
     */
    public ProcessResult process(InboundMessage message) {
        Optional<Ticket> ticketMatch = router.resolveTicket(message);

        if (ticketMatch.isPresent()) {
            Ticket ticket = ticketMatch.get();
            Reply reply = ticketService.addReply(
                    ticket.getId(),
                    message.body(),
                    message.fromName(),
                    message.fromEmail(),
                    "inbound_email",
                    false
            );
            return new ProcessResult(
                    Outcome.REPLIED_TO_EXISTING,
                    ticket.getId(),
                    reply.getId(),
                    pendingDownloads(message)
            );
        }

        if (isNoiseEmail(message)) {
            return new ProcessResult(Outcome.SKIPPED, null, null, List.of());
        }

        Ticket newTicket = ticketService.create(
                nonBlankOr(message.subject(), "(no subject)"),
                message.body(),
                message.fromName(),
                message.fromEmail(),
                TicketPriority.MEDIUM,
                null
        );
        log.info("[InboundEmailService] Created ticket #{} from inbound email", newTicket.getId());

        return new ProcessResult(
                Outcome.CREATED_NEW,
                newTicket.getId(),
                null,
                pendingDownloads(message)
        );
    }

    /**
     * Noise emails: empty body + empty subject, or from common
     * bounce/no-reply senders.
     */
    static boolean isNoiseEmail(InboundMessage message) {
        if ("no-reply@sns.amazonaws.com".equalsIgnoreCase(message.fromEmail())) {
            return true;
        }
        boolean bodyEmpty = message.body() == null || message.body().isBlank();
        boolean subjectEmpty = message.subject() == null || message.subject().isBlank();
        return bodyEmpty && subjectEmpty;
    }

    private static String nonBlankOr(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static List<PendingAttachment> pendingDownloads(InboundMessage message) {
        List<PendingAttachment> list = new ArrayList<>();
        if (message.attachments() == null) {
            return list;
        }
        for (InboundAttachment a : message.attachments()) {
            if (a.downloadUrl() != null && a.content() == null) {
                list.add(new PendingAttachment(
                        a.name(),
                        a.contentType(),
                        a.sizeBytes(),
                        a.downloadUrl()
                ));
            }
        }
        return list;
    }

    public enum Outcome {
        REPLIED_TO_EXISTING,
        CREATED_NEW,
        SKIPPED
    }

    public record ProcessResult(
            Outcome outcome,
            Long ticketId,
            Long replyId,
            List<PendingAttachment> pendingAttachmentDownloads
    ) {
    }

    public record PendingAttachment(
            String name,
            String contentType,
            Long sizeBytes,
            String downloadUrl
    ) {
    }
}
