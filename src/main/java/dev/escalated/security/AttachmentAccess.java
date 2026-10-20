package dev.escalated.security;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.Attachment;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AttachmentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may download an attachment. The rules of the Laravel reference's
 * {@code AttachmentAccess}:
 *
 * <ul>
 *   <li>Staff (agent or admin) may download an attachment on any ticket they
 *       can see. Department and account visibility apply, through the same
 *       {@link TicketAccess} scope as every other staff read.</li>
 *   <li>Anyone else may download only attachments of a ticket they requested
 *       (the principal name is matched against the requester email, as on
 *       the customer ticket routes), and never one on an internal note.</li>
 * </ul>
 *
 * <p>Every refusal is a 404, so a caller cannot tell "not yours" from "no such
 * attachment" and sequential ids reveal nothing.
 */
@Component
public class AttachmentAccess {

    private final AttachmentRepository attachments;
    private final EscalatedAuthorization authorization;

    public AttachmentAccess(AttachmentRepository attachments, EscalatedAuthorization authorization) {
        this.attachments = attachments;
        this.authorization = authorization;
    }

    /** What the controller needs to stream the file, read while the session is open. */
    public record Download(String filePath, String fileName, String mimeType) {
    }

    /**
     * The attachment the actor may download.
     *
     * @throws EntityNotFoundException when there is no such attachment or the
     *     actor may not have it
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Download authorize(Long id, Authentication actor) {
        Attachment attachment = attachments.findById(id).orElseThrow(AttachmentAccess::notFound);
        Reply reply = attachment.getReply();
        Ticket ticket = attachment.getTicket() != null
                ? attachment.getTicket()
                : reply != null ? reply.getTicket() : null;
        if (ticket == null) {
            throw notFound();
        }

        if (authorization.isAgent(actor)) {
            // Restricted agents: the ticket must be one their scope lets them see.
            TicketAccess.assertVisible(ticket);
        } else {
            String principal = actor == null ? null : actor.getName();
            boolean requester = principal != null && !principal.isBlank()
                    && ticket.getRequesterEmail() != null
                    && ticket.getRequesterEmail().equalsIgnoreCase(principal.trim());
            boolean internal = reply != null && reply.isInternal();
            if (!requester || internal) {
                throw notFound();
            }
        }
        return new Download(attachment.getFilePath(), attachment.getFileName(), attachment.getMimeType());
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException("Attachment not found");
    }
}
