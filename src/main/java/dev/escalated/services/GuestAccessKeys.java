package dev.escalated.services;

import dev.escalated.config.EscalatedProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The key behind guest code digests and guest grants:
 * {@code escalated.guest-access.secret}, else
 * {@code escalated.email.inbound-secret}, else a random key for this process
 * (logged, since grants then do not survive a restart or reach other nodes).
 */
@Component
public class GuestAccessKeys {

    private static final Logger log = LoggerFactory.getLogger(GuestAccessKeys.class);

    private final byte[] secret;

    public GuestAccessKeys(EscalatedProperties properties) {
        String configured = properties.getGuestAccess().getSecret();
        if (configured == null || configured.isBlank()) {
            configured = properties.getEmail() == null ? null : properties.getEmail().getInboundSecret();
        }
        if (configured == null || configured.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            log.warn("escalated.guest-access.secret is not set; guest grants use a per-process key and will not"
                    + " survive a restart or work across nodes.");
        } else {
            this.secret = configured.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** HMAC-SHA256 (hex) of {@code value} under a key derived for {@code purpose}. */
    public String digest(String purpose, String value) {
        return HexFormat.of().formatHex(hmac(derive(purpose), value.getBytes(StandardCharsets.UTF_8)));
    }

    /** The digest of a normalised email address. */
    public String emailDigest(String email) {
        return digest("email", normaliseEmail(email));
    }

    /** A 32-byte key for {@code purpose}. */
    public byte[] derive(String purpose) {
        return hmac(secret, ("escalated-guest-access:" + purpose).getBytes(StandardCharsets.UTF_8));
    }

    public static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
