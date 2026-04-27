package dev.escalated.services.email;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pure helpers for RFC 5322 Message-ID threading and signed Reply-To
 * addresses. Mirrors the NestJS reference
 * <c>escalated-nestjs/src/services/email/message-id.ts</c>.
 *
 * <p>Message-ID format:
 * <ul>
 *   <li><code>&lt;ticket-{ticketId}@{domain}&gt;</code> — initial ticket email</li>
 *   <li><code>&lt;ticket-{ticketId}-reply-{replyId}@{domain}&gt;</code> — agent reply</li>
 * </ul>
 *
 * <p>Signed Reply-To format:
 * <code>reply+{ticketId}.{hmac8}@{domain}</code>
 *
 * <p>The signed Reply-To carries identity even when clients strip our
 * Message-ID / In-Reply-To headers — inbound provider webhook verifies
 * the signature before routing the reply to the ticket.
 */
public final class MessageIdUtil {

    private static final Pattern TICKET_ID_IN_MSG = Pattern.compile("ticket-(\\d+)(?:-reply-\\d+)?@", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLY_LOCAL_PART = Pattern.compile("^reply\\+(\\d+)\\.([a-f0-9]{8})$", Pattern.CASE_INSENSITIVE);

    private MessageIdUtil() {
        // static helpers only
    }

    /**
     * Build an RFC 5322 Message-ID. Pass {@code null} for {@code replyId}
     * on the initial ticket email; the tail "-reply-{id}" is appended
     * only when {@code replyId} is non-null.
     */
    public static String buildMessageId(long ticketId, Long replyId, String domain) {
        String body = replyId != null ? "ticket-" + ticketId + "-reply-" + replyId : "ticket-" + ticketId;
        return "<" + body + "@" + domain + ">";
    }

    /**
     * Extract the ticket id from a Message-ID we issued. Accepts the
     * header value with or without angle brackets. Returns
     * {@link Optional#empty()} when the input doesn't match our shape.
     */
    public static Optional<Long> parseTicketIdFromMessageId(String raw) {
        if (raw == null) return Optional.empty();
        Matcher m = TICKET_ID_IN_MSG.matcher(raw);
        if (!m.find()) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(m.group(1)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /**
     * Build a signed Reply-To address. The 8-character HMAC-SHA256
     * signature over the ticket id + secret prevents tampering.
     */
    public static String buildReplyTo(long ticketId, String secret, String domain) {
        return "reply+" + ticketId + "." + sign(ticketId, secret) + "@" + domain;
    }

    /**
     * Verify a full reply-to address (local@domain) or just the local
     * part. Returns the ticket id on success.
     */
    public static Optional<Long> verifyReplyTo(String address, String secret) {
        if (address == null) return Optional.empty();
        int at = address.indexOf('@');
        String local = at > 0 ? address.substring(0, at) : address;
        Matcher m = REPLY_LOCAL_PART.matcher(local);
        if (!m.matches()) return Optional.empty();
        long ticketId;
        try {
            ticketId = Long.parseLong(m.group(1));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        String expected = sign(ticketId, secret);
        if (!expected.equalsIgnoreCase(m.group(2))) {
            return Optional.empty();
        }
        return Optional.of(ticketId);
    }

    private static String sign(long ticketId, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(Long.toString(ticketId).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }
}
