package dev.escalated.services;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.TwoFactor;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.TwoFactorRepository;
import dev.escalated.security.AccessFeatures;
import jakarta.persistence.EntityNotFoundException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-factor authentication (ADR 2026-10-29-configurable-access-features): TOTP as RFC 6238 (SHA-1,
 * 6 digits, 30 s steps) through {@code com.eatthepath:java-otp}, one step of drift either side, each
 * time step accepted once; eight single-use recovery codes. Secrets and recovery codes are encrypted
 * with Spring Security's AES-GCM {@code Encryptors.delux} under
 * {@code escalated.two-factor.encryption-key} ({@code ESCALATED_TWO_FACTOR_SECRET}).
 */
@Service
public class TwoFactorService {

    /** Steps of clock drift accepted either side of now. */
    public static final int WINDOW = 1;

    static final Duration STEP = Duration.ofSeconds(30);

    /** A fixed salt: the key is the secret; every value gets its own random IV. */
    private static final String SALT = HexFormat.of().formatHex("escalated-2fa".getBytes(StandardCharsets.UTF_8));

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TwoFactorRepository enrolments;
    private final AgentProfileRepository agents;
    private final AccessFeatures features;
    private final EscalatedProperties properties;
    private final SettingsService settings;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();
    private final TimeBasedOneTimePasswordGenerator totp = new TimeBasedOneTimePasswordGenerator(STEP, 6);

    public TwoFactorService(TwoFactorRepository enrolments, AgentProfileRepository agents, AccessFeatures features,
                            EscalatedProperties properties, SettingsService settings, Environment environment) {
        this.enrolments = enrolments;
        this.agents = agents;
        this.features = features;
        this.properties = properties;
        this.settings = settings;
        this.environment = environment;
    }

    public boolean enabled() {
        return features.twoFactor();
    }

    /**
     * Whether staff must enrol: the {@code two_factor_required} setting when an admin has saved it,
     * else {@code escalated.two-factor.required} / {@code ESCALATED_TWO_FACTOR_REQUIRED}. Only while
     * two-factor is on.
     */
    public boolean required() {
        if (!enabled()) {
            return false;
        }
        try {
            Optional<String> stored = settings.get("two_factor_required");
            if (stored.isPresent() && !stored.get().isBlank()) {
                return truthy(stored.get());
            }
        } catch (RuntimeException unavailable) {
            // Fall back to configuration.
        }
        Boolean configured = properties.getTwoFactor().getRequired();
        return configured != null ? configured : truthy(environment.getProperty("ESCALATED_TWO_FACTOR_REQUIRED"));
    }

    /** The agent profile of the user signed in as {@code email}, if any. */
    public Optional<AgentProfile> profile(String email) {
        return email == null ? Optional.empty() : agents.findByEmail(email);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Optional<TwoFactor> enrolment(Long agentId) {
        return agentId == null ? Optional.empty() : enrolments.findByAgentId(agentId);
    }

    /** The confirmed enrolment of {@code agentId}, if any. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Optional<TwoFactor> confirmedFor(Long agentId) {
        return enrolment(agentId).filter(TwoFactor::isConfirmed);
    }

    /** Whether the user signed in as {@code email} has a confirmed enrolment. */
    public boolean confirmedFor(String email) {
        return profile(email).flatMap(profile -> confirmedFor(profile.getId())).isPresent();
    }

    /** What enrolling shows once: {@code qr_uri} and {@code recovery_codes}. */
    public record Setup(String qrUri, List<String> recoveryCodes) {
    }

    /** Replaces any pending enrolment of {@code agent} with a new one. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Setup setup(AgentProfile agent) {
        TextEncryptor box = encryptor();
        byte[] key = new byte[20];
        random.nextBytes(key);
        String secret = new Base32().encodeToString(key).replace("=", "");
        List<String> codes = recoveryCodes();
        TwoFactor enrolment = enrolments.findByAgentId(agent.getId()).orElseGet(TwoFactor::new);
        enrolment.setAgentId(agent.getId());
        enrolment.setSecret(box.encrypt(secret));
        enrolment.setRecoveryCodes(box.encrypt(json(codes)));
        enrolment.setConfirmedAt(null);
        enrolment.setLastUsedStep(null);
        enrolments.save(enrolment);
        return new Setup(qrUri(secret, agent.getEmail()), codes);
    }

    /**
     * Confirms a pending enrolment with a valid code: its recovery codes, or empty when there is no
     * pending enrolment or the code is wrong.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Optional<List<String>> confirm(Long agentId, String code) {
        Optional<TwoFactor> pending = enrolment(agentId).filter(enrolment -> !enrolment.isConfirmed());
        if (pending.isEmpty() || !verifyAndConsume(pending.get(), code)) {
            return Optional.empty();
        }
        TwoFactor enrolment = enrolments.findById(pending.get().getId()).orElseThrow();
        enrolment.setConfirmedAt(Instant.now());
        enrolments.save(enrolment);
        return Optional.of(recoveryCodesOf(enrolment));
    }

    /** Removes {@code agentId}'s enrolment, pending or confirmed. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void disable(Long agentId) {
        enrolments.findByAgentId(agentId).ifPresent(enrolments::delete);
    }

    /**
     * Clears the enrolment of the user with {@code email} (a lost device), as Laravel's
     * {@code escalated:two-factor:reset}.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void reset(String email) {
        AgentProfile agent = agents.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("No agent profile for " + email));
        disable(agent.getId());
    }

    /**
     * Whether {@code code} is valid for the enrolment now, recording its time step so that step and
     * every earlier one are refused afterwards (a code works once).
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public boolean verifyAndConsume(TwoFactor enrolment, String code) {
        String digits = code == null ? "" : code.replaceAll("\\s+", "");
        if (!digits.matches("\\d{6}")) {
            return false;
        }
        String secret;
        try {
            secret = encryptor().decrypt(enrolment.getSecret());
        } catch (RuntimeException unreadable) {
            return false;
        }
        SecretKeySpec key = new SecretKeySpec(new Base32().decode(secret), totp.getAlgorithm());
        long now = Instant.now().getEpochSecond() / STEP.getSeconds();
        for (long step = now - WINDOW; step <= now + WINDOW; step++) {
            if (enrolment.getLastUsedStep() != null && step <= enrolment.getLastUsedStep()) {
                continue;
            }
            String expected;
            try {
                expected = String.format(Locale.ROOT, "%06d",
                        totp.generateOneTimePassword(key, Instant.ofEpochSecond(step * STEP.getSeconds())));
            } catch (InvalidKeyException invalid) {
                return false;
            }
            if (java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    digits.getBytes(StandardCharsets.US_ASCII))) {
                if (enrolments.consumeStep(enrolment.getId(), step) == 1) {
                    enrolment.setLastUsedStep(step);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    /** Uses a recovery code of the confirmed enrolment: true, and the code is gone, when it matches. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public boolean useRecoveryCode(TwoFactor given, String code) {
        String wanted = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return false;
        }
        TwoFactor enrolment = enrolments.findById(given.getId()).orElse(null);
        if (enrolment == null) {
            return false;
        }
        List<String> codes;
        try {
            codes = new ArrayList<>(recoveryCodesOf(enrolment));
        } catch (RuntimeException unreadable) {
            return false;
        }
        for (String candidate : codes) {
            if (java.security.MessageDigest.isEqual(candidate.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                    wanted.getBytes(StandardCharsets.UTF_8))) {
                codes.remove(candidate);
                enrolment.setRecoveryCodes(encryptor().encrypt(json(codes)));
                enrolments.save(enrolment);
                return true;
            }
        }
        return false;
    }

    /** The otpauth:// URI an authenticator app reads from a QR code. */
    public String qrUri(String secret, String email) {
        String issuer = properties.getTwoFactor().getIssuer();
        String label = encode(issuer) + ":" + encode(email);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    /** The current code for a base32 secret; for tests and tools. */
    public String codeAt(String secret, Instant when) {
        try {
            return String.format(Locale.ROOT, "%06d", totp.generateOneTimePassword(
                    new SecretKeySpec(new Base32().decode(secret), totp.getAlgorithm()), when));
        } catch (InvalidKeyException invalid) {
            throw new IllegalArgumentException(invalid);
        }
    }

    /** The base32 secret of an enrolment, decrypted; for tests and tools. */
    public String secretOf(TwoFactor enrolment) {
        return encryptor().decrypt(enrolment.getSecret());
    }

    private List<String> recoveryCodesOf(TwoFactor enrolment) {
        if (enrolment.getRecoveryCodes() == null) {
            return List.of();
        }
        try {
            return JSON.readValue(encryptor().decrypt(enrolment.getRecoveryCodes()), new TypeReference<List<String>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            return List.of();
        }
    }

    private List<String> recoveryCodes() {
        HexFormat hex = HexFormat.of().withUpperCase();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            byte[] left = new byte[4];
            byte[] right = new byte[4];
            random.nextBytes(left);
            random.nextBytes(right);
            codes.add(hex.formatHex(left) + "-" + hex.formatHex(right));
        }
        return codes;
    }

    private TextEncryptor encryptor() {
        String key = properties.getTwoFactor().getEncryptionKey();
        if (key == null || key.isBlank()) {
            key = environment.getProperty("ESCALATED_TWO_FACTOR_SECRET");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Two-factor authentication needs an encryption key:"
                    + " set escalated.two-factor.encryption-key (ESCALATED_TWO_FACTOR_SECRET).");
        }
        return Encryptors.delux(key, SALT);
    }

    private static String json(List<String> codes) {
        try {
            return JSON.writeValueAsString(codes);
        } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean truthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }
}
