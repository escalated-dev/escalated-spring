package dev.escalated.services.email.inbound;

/**
 * Transport-specific parser that normalizes a provider's webhook
 * payload into an {@link InboundMessage}. Implementations register
 * themselves as Spring beans and are resolved by {@link #name()}
 * (matches the adapter label on the inbound webhook request).
 *
 * <p>Add a new provider by implementing this interface; the inbound
 * controller will pick it up via DI when the request's adapter label
 * matches {@link #name()}.
 */
public interface InboundEmailParser {

    /**
     * Short provider name. Must match the value in the
     * {@code ?adapter=...} query parameter or the
     * {@code X-Escalated-Adapter} header on the inbound webhook.
     */
    String name();

    /**
     * Parse a raw webhook payload (e.g. a provider-specific JSON
     * object) into an {@link InboundMessage}.
     */
    InboundMessage parse(Object rawPayload);
}
