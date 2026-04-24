package dev.escalated.services.email.inbound;

/**
 * Inbound attachment. Providers either inline the content (small
 * attachments) or supply a URL to download it from (larger
 * provider-hosted attachments).
 */
public record InboundAttachment(
        String name,
        String contentType,
        Long sizeBytes,
        byte[] content,
        String downloadUrl
) {
}
