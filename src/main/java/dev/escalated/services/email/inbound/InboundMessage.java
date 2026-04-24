package dev.escalated.services.email.inbound;

import java.util.List;
import java.util.Map;

/**
 * Transport-agnostic representation of an inbound email, independent
 * of the source adapter (Postmark, Mailgun, SES, IMAP, etc.).
 *
 * <p>Adapters normalize their webhook payload into this shape; the
 * {@link InboundEmailRouter} then resolves it to a ticket by parsing
 * canonical Message-IDs out of {@code inReplyTo} / {@code references}
 * and verifying the signed Reply-To on {@code toEmail}.
 *
 * <p>Mirrors the NestJS reference and the per-framework ports.
 */
public record InboundMessage(
        String fromEmail,
        String fromName,
        String toEmail,
        String subject,
        String bodyText,
        String bodyHtml,
        String messageId,
        String inReplyTo,
        String references,
        Map<String, String> headers,
        List<InboundAttachment> attachments
) {

    public InboundMessage {
        headers = headers == null ? Map.of() : headers;
        attachments = attachments == null ? List.of() : attachments;
    }

    /**
     * Best body content (plain text preferred, HTML fallback).
     */
    public String body() {
        if (bodyText != null && !bodyText.isEmpty()) {
            return bodyText;
        }
        return bodyHtml != null ? bodyHtml : "";
    }
}
