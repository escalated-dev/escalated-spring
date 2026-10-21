package dev.escalated.services;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.TicketRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expiring guest grants for tickets (escalated-laravel
 * {@code docs/guest-access.md}).
 *
 * <p>A grant is an opaque bearer token: AES-256-GCM over the ticket id, a
 * random nonce, the expiry and a digest of the requester's email. The ticket
 * stores only the SHA-256 of the current nonce and the expiry, so issuing a
 * grant revokes every earlier one, and a changed requester email revokes them
 * too. Lifetime: {@code escalated.guest-access.ttl-minutes} (default 24 hours,
 * clamped to 5 minutes through 7 days).
 *
 * <p>While {@code escalated.guest-access.legacy-tokens} is on, a ticket's
 * permanent guest token still opens it until a grant is issued for it, which
 * retires the permanent token.
 */
@Service
public class GuestAccessGrants {

    /** Every grant starts with this, so it is never mistaken for an API token. */
    public static final String PREFIX = "eg1.";

    private static final int NONCE_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TicketRepository tickets;
    private final GuestAccessKeys keys;
    private final EscalatedProperties properties;

    public GuestAccessGrants(TicketRepository tickets, GuestAccessKeys keys, EscalatedProperties properties) {
        this.tickets = tickets;
        this.keys = keys;
        this.properties = properties;
    }

    /** A grant and when it expires. */
    public record Grant(String token, Instant expiresAt) {
    }

    /** Issues a grant for the ticket, revoking earlier grants and retiring its permanent token. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Grant issue(Long ticketId) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow();
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        String nonceText = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        Instant expiresAt = Instant.now().plus(ttl()).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        ticket.setGuestAccessHash(GuestAccessKeys.sha256(nonceText));
        ticket.setGuestAccessExpiresAt(expiresAt);
        ticket.setGuestAccessToken(null);
        tickets.save(ticket);

        String claims = ticket.getId() + "|" + nonceText + "|" + expiresAt.getEpochSecond() + "|"
                + keys.emailDigest(ticket.getRequesterEmail());
        return new Grant(PREFIX + seal(claims), expiresAt);
    }

    /** Revokes every grant for the ticket. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void revoke(Long ticketId) {
        tickets.findById(ticketId).ifPresent(ticket -> {
            ticket.setGuestAccessHash(null);
            ticket.setGuestAccessExpiresAt(null);
            tickets.save(ticket);
        });
    }

    /**
     * The ticket a grant opens, if the grant is genuine, current and for the
     * ticket with reference {@code reference}.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Optional<Ticket> verify(String token, String reference) {
        if (token == null || !token.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String claims = open(token.substring(PREFIX.length()));
        if (claims == null) {
            return Optional.empty();
        }
        String[] parts = claims.split("\\|", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }
        long ticketId;
        long expiry;
        try {
            ticketId = Long.parseLong(parts[0]);
            expiry = Long.parseLong(parts[2]);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (Instant.ofEpochSecond(expiry).isBefore(now)) {
            return Optional.empty();
        }
        return tickets.findById(ticketId).filter(ticket ->
                (reference == null || reference.equals(ticket.getTicketNumber()))
                        && ticket.getGuestAccessHash() != null
                        && constantTimeEquals(ticket.getGuestAccessHash(), GuestAccessKeys.sha256(parts[1]))
                        && ticket.getGuestAccessExpiresAt() != null
                        && ticket.getGuestAccessExpiresAt().isAfter(now)
                        && constantTimeEquals(keys.emailDigest(ticket.getRequesterEmail()), parts[3]));
    }

    /**
     * The ticket a guest request is for: a grant in {@code Authorization:
     * Bearer} naming the ticket {@code pathValue} refers to, or, while legacy
     * tokens are on and no bearer is sent, {@code pathValue} as a permanent
     * guest token.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Optional<Ticket> resolve(String pathValue, String authorization) {
        String bearer = bearer(authorization);
        if (bearer != null) {
            return verify(bearer, pathValue);
        }
        if (!properties.getGuestAccess().isLegacyTokens() || pathValue == null || pathValue.isBlank()) {
            return Optional.empty();
        }
        return tickets.findByGuestAccessToken(pathValue);
    }

    public static String bearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private Duration ttl() {
        int minutes = properties.getGuestAccess().getTtlMinutes();
        return Duration.ofMinutes(Math.max(5, Math.min(minutes, 7 * 24 * 60)));
    }

    private String seal(String claims) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys.derive("grant"), "AES"),
                    new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(claims.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String open(String sealed) {
        try {
            byte[] raw = Base64.getUrlDecoder().decode(sealed);
            if (raw.length <= IV_BYTES + 16) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys.derive("grant"), "AES"),
                    new GCMParameterSpec(128, raw, 0, IV_BYTES));
            return new String(cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception forgedOrTruncated) {
            return null;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
