package dev.escalated;

import dev.escalated.services.GuestVerificationService;

/**
 * Obtains a mailbox proof the way the widget does, for tests that create
 * guest tickets or chats: a code is requested, and the code the email would
 * carry is read from the service's answer.
 */
public final class GuestProofs {

    private GuestProofs() {
    }

    /**
     * {@code json} (an object literal) with {@code verification_id} and
     * {@code verification_code} for {@code email} and {@code purpose} added.
     */
    public static String with(GuestVerificationService verification, String json, String email, String purpose) {
        GuestVerificationService.Challenge challenge = verification.request(email, purpose, "127.0.0.1");
        String trimmed = json.trim();
        String fields = "\"verification_id\":\"" + challenge.verificationId() + "\",\"verification_code\":\""
                + challenge.code() + "\"";
        return trimmed.equals("{}")
                ? "{" + fields + "}"
                : trimmed.substring(0, trimmed.length() - 1) + "," + fields + "}";
    }
}
