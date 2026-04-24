package dev.escalated.services.email.inbound;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.email.MessageIdUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves an inbound email to an existing ticket via canonical
 * Message-ID parsing + signed Reply-To verification.
 *
 * <p>Resolution order (first match wins):
 * <ol>
 *   <li>{@code In-Reply-To} parsed via {@link MessageIdUtil#parseTicketIdFromMessageId}
 *       — cold-start path, no DB lookup on the header value required
 *       (we know our own Message-ID format).</li>
 *   <li>{@code References} parsed via {@link MessageIdUtil#parseTicketIdFromMessageId},
 *       each id in order.</li>
 *   <li>Signed Reply-To on {@link InboundMessage#toEmail()}
 *       ({@code reply+{id}.{hmac8}@...}) verified via
 *       {@link MessageIdUtil#verifyReplyTo}. Survives clients that
 *       strip our threading headers; forged signatures are rejected
 *       with a timing-safe HMAC comparison.</li>
 *   <li>Subject line reference tag (e.g. {@code [ESC-00001]}) — legacy.</li>
 *   <li>Audit-log lookup — not implemented in this PR; the row is
 *       inserted by the inbound controller once the service lands.</li>
 * </ol>
 *
 * <p>Mirrors the NestJS {@code InboundRouterService} resolution order
 * and the Laravel/Rails/Django/Adonis/WordPress/.NET ports.
 */
@Service
public class InboundEmailRouter {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailRouter.class);

    private final TicketRepository ticketRepository;
    private final EscalatedProperties properties;

    public InboundEmailRouter(TicketRepository ticketRepository, EscalatedProperties properties) {
        this.ticketRepository = ticketRepository;
        this.properties = properties;
    }

    /**
     * Resolve the inbound email to an existing ticket, or
     * {@link Optional#empty()} when no match (caller should create a
     * new ticket).
     */
    public Optional<Ticket> resolveTicket(InboundMessage message) {
        if (message == null) {
            return Optional.empty();
        }

        List<String> headerIds = candidateHeaderMessageIds(message);

        // 1 + 2. Parse canonical Message-IDs out of our own headers.
        for (String raw : headerIds) {
            Optional<Long> ticketId = MessageIdUtil.parseTicketIdFromMessageId(raw);
            if (ticketId.isPresent()) {
                Optional<Ticket> ticket = ticketRepository.findById(ticketId.get());
                if (ticket.isPresent()) {
                    return ticket;
                }
            }
        }

        // 3. Signed Reply-To on the recipient address.
        String secret = properties.getEmail() == null ? null : properties.getEmail().getInboundSecret();
        if (secret != null && !secret.isBlank() && message.toEmail() != null) {
            Optional<Long> verified = MessageIdUtil.verifyReplyTo(message.toEmail(), secret);
            if (verified.isPresent()) {
                Optional<Ticket> ticket = ticketRepository.findById(verified.get());
                if (ticket.isPresent()) {
                    return ticket;
                }
                log.debug("[InboundEmailRouter] Reply-To verified but ticket #{} not found", verified.get());
            }
        }

        // 4. Subject line reference tag.
        if (message.subject() != null) {
            Matcher m = SUBJECT_REF_PATTERN.matcher(message.subject());
            if (m.find()) {
                // Ticket reference is stored in the ticket_number column.
                Optional<Ticket> ticket = ticketRepository.findByTicketNumber(m.group(1));
                if (ticket.isPresent()) {
                    return ticket;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Return every candidate Message-ID from the inbound headers in
     * the order the mail client sent them. Caller iterates the
     * result and stops at the first resolvable id.
     */
    public static List<String> candidateHeaderMessageIds(InboundMessage message) {
        List<String> ids = new ArrayList<>();
        if (message.inReplyTo() != null && !message.inReplyTo().isBlank()) {
            ids.add(message.inReplyTo().trim());
        }
        if (message.references() != null && !message.references().isBlank()) {
            for (String raw : message.references().trim().split("\\s+")) {
                if (!raw.isBlank()) {
                    ids.add(raw);
                }
            }
        }
        return ids;
    }

    private static final Pattern SUBJECT_REF_PATTERN = Pattern.compile("\\[([A-Z]+-[0-9A-Z-]+)\\]");
}
