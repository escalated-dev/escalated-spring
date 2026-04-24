package dev.escalated.controllers;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.services.email.inbound.InboundEmailParser;
import dev.escalated.services.email.inbound.InboundEmailService;
import dev.escalated.services.email.inbound.InboundMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single ingress point for inbound-email webhooks. Dispatches the
 * raw payload to the matching {@link InboundEmailParser} (selected
 * via the {@code ?adapter=...} query parameter or
 * {@code X-Escalated-Adapter} header), then resolves the parsed
 * message to a ticket via {@link InboundEmailRouter}.
 *
 * <p>Guarded by a constant-time shared-secret check on the
 * {@code X-Escalated-Inbound-Secret} header — hosts configure this
 * via {@code escalated.email.inbound-secret} (reused for signed
 * Reply-To verification, so the key pair is symmetric).
 *
 * <p>Returns JSON payloads — {@code 200 OK} on successful routing,
 * {@code 401 Unauthorized} on secret mismatch, {@code 400 Bad Request}
 * for unknown adapters or malformed payloads.
 */
@RestController
@RequestMapping("/escalated/webhook/email")
public class InboundEmailController {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailController.class);

    private final EscalatedProperties properties;
    private final InboundEmailService inboundService;
    private final Map<String, InboundEmailParser> parsersByName;

    public InboundEmailController(
            EscalatedProperties properties,
            InboundEmailService inboundService,
            List<InboundEmailParser> parsers) {
        this.properties = properties;
        this.inboundService = inboundService;
        Map<String, InboundEmailParser> byName = new HashMap<>();
        for (InboundEmailParser p : parsers) {
            byName.put(p.name().toLowerCase(), p);
        }
        this.parsersByName = byName;
    }

    @PostMapping("/inbound")
    public ResponseEntity<Map<String, Object>> inbound(
            @RequestBody Map<String, Object> payload,
            @RequestParam(value = "adapter", required = false) String adapterQuery,
            @RequestHeader(value = "X-Escalated-Adapter", required = false) String adapterHeader,
            @RequestHeader(value = "X-Escalated-Inbound-Secret", required = false) String providedSecret) {

        if (!verifySecret(providedSecret)) {
            return ResponseEntity.status(401).body(Map.of("error", "missing or invalid inbound secret"));
        }

        String adapter = firstNonEmpty(adapterQuery, adapterHeader);
        if (adapter == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing adapter"));
        }
        InboundEmailParser parser = parsersByName.get(adapter.toLowerCase());
        if (parser == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown adapter: " + adapter));
        }

        InboundMessage message;
        try {
            message = parser.parse(payload);
        } catch (RuntimeException ex) {
            log.warn("[InboundEmailController] parse failed for {}: {}", adapter, ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "invalid payload"));
        }

        InboundEmailService.ProcessResult result;
        try {
            result = inboundService.process(message);
        } catch (RuntimeException ex) {
            log.error("[InboundEmailController] process failed: {}", ex.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "processing failed"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("outcome", result.outcome().name().toLowerCase());
        response.put("ticketId", result.ticketId());
        response.put("replyId", result.replyId());
        response.put("pendingAttachmentDownloads", result.pendingAttachmentDownloads());
        return ResponseEntity.accepted().body(response);
    }

    private boolean verifySecret(String provided) {
        String expected = properties.getEmail() == null
                ? ""
                : (properties.getEmail().getInboundSecret() == null ? "" : properties.getEmail().getInboundSecret());
        if (expected.isEmpty() || provided == null) {
            // Inbound signing not configured → effectively disables
            // the webhook (prevents accidental unauthenticated routing).
            return false;
        }
        byte[] e = expected.getBytes(StandardCharsets.UTF_8);
        byte[] p = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(e, p);
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }
}
