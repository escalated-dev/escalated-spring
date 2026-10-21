package dev.escalated.services;

/**
 * Delivers a guest verification code. The default sends a plain email through
 * the host's {@code JavaMailSender}; a host can supply its own bean.
 */
public interface GuestCodeMailer {

    /**
     * Sends {@code code} to {@code email}. {@code purpose} is {@code ticket},
     * {@code chat} or {@code lookup}; the code expires in
     * {@code expiresInMinutes}.
     */
    void send(String email, String code, String purpose, int expiresInMinutes);
}
