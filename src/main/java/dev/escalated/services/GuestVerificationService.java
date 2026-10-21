package dev.escalated.services;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.GuestVerification;
import dev.escalated.repositories.GuestVerificationRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mailbox proof for guests (escalated-laravel {@code docs/guest-access.md}).
 *
 * <p>A guest asks for a code for an email address and a purpose
 * ({@code ticket}, {@code chat} or {@code lookup}). The 8-digit code goes only
 * to that mailbox; the answer carries an opaque {@code verification_id}. The
 * code lasts ten minutes, allows five guesses and can be spent once, for that
 * address and purpose. Codes per mailbox are budgeted per client IP and in
 * total, per hour.
 *
 * <p>Spending is claim-and-release: {@link #claim} marks the code spent (and
 * counts a wrong guess) in a transaction of its own, so a wrong guess stays
 * counted whatever happens next; if the operation the code was for then fails,
 * {@link #release} hands the code back so the guest can retry.
 */
@Service
public class GuestVerificationService {

    public static final List<String> PURPOSES = List.of("ticket", "chat", "lookup");
    public static final int EXPIRES_IN_SECONDS = 600;
    public static final int MAX_ATTEMPTS = 5;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CODE = Pattern.compile("^[0-9]{8}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GuestVerificationRepository verifications;
    private final GuestAccessKeys keys;
    private final List<GuestCodeMailer> mailers;
    private final EscalatedProperties properties;
    private final TransactionTemplate ownTransaction;

    public GuestVerificationService(GuestVerificationRepository verifications,
                                    GuestAccessKeys keys,
                                    List<GuestCodeMailer> mailers,
                                    EscalatedProperties properties,
                                    @Qualifier(EscalatedTransactionManagers.ESCALATED)
                                    PlatformTransactionManager transactionManager) {
        this.verifications = verifications;
        this.keys = keys;
        this.mailers = mailers;
        this.properties = properties;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** What {@link #request} made: the public id, and the code (which only the email carries). */
    public record Challenge(String verificationId, String code, int expiresIn) {
    }

    /**
     * Makes a code for {@code email} and {@code purpose} and emails it.
     *
     * @throws ValidationFailedException for a bad email or purpose
     * @throws GuestBudgetExceededException when the mailbox has had too many codes this hour
     */
    public Challenge request(String email, String purpose, String clientIp) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        String address = GuestAccessKeys.normaliseEmail(email);
        if (address.isEmpty()) {
            errors.put("email", List.of("The email field is required."));
        } else if (address.length() > 255 || !EMAIL.matcher(address).matches()) {
            errors.put("email", List.of("The email must be a valid email address."));
        }
        if (purpose == null || !PURPOSES.contains(purpose)) {
            errors.put("purpose", List.of("The purpose must be ticket, chat or lookup."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        String emailDigest = keys.emailDigest(address);
        String clientDigest = keys.digest("client", clientIp == null ? "" : clientIp);
        Instant now = Instant.now();
        Instant hourAgo = now.minus(Duration.ofHours(1));

        String code = String.format("%08d", RANDOM.nextInt(100_000_000));
        String publicId = randomId();
        ownTransaction.executeWithoutResult(status -> {
            // Codes older than the budget window serve no purpose any more.
            verifications.deleteCreatedBefore(hourAgo);
            EscalatedProperties.GuestAccessProperties config = properties.getGuestAccess();
            if (verifications.countByEmailDigestAndClientDigestAndCreatedAtAfter(emailDigest, clientDigest, hourAgo)
                    >= config.getCodesPerClientPerHour()
                    || verifications.countByEmailDigestAndCreatedAtAfter(emailDigest, hourAgo)
                    >= config.getCodesPerMailboxPerHour()) {
                throw new GuestBudgetExceededException(3600);
            }
            GuestVerification verification = new GuestVerification();
            verification.setPublicId(publicId);
            verification.setEmailDigest(emailDigest);
            verification.setClientDigest(clientDigest);
            verification.setPurpose(purpose);
            verification.setCodeDigest(codeDigest(publicId, code));
            verification.setExpiresAt(now.plusSeconds(EXPIRES_IN_SECONDS));
            verifications.save(verification);
        });

        mailer().send(address, code, purpose, EXPIRES_IN_SECONDS / 60);
        return new Challenge(publicId, code, EXPIRES_IN_SECONDS);
    }

    /**
     * Spends the proof for {@code email} and {@code purpose}, or throws a 422.
     * A wrong code counts as a guess.
     */
    public void claim(Object verificationId, Object code, String email, String purpose) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        if (!(verificationId instanceof String id) || id.isBlank()) {
            errors.put("verification_id", List.of("Request a verification code first."));
        }
        if (!(code instanceof String text) || text.isBlank()) {
            errors.put("verification_code", List.of("Enter the 8-digit code from your email."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        String id = (String) verificationId;
        String digits = ((String) code).trim();
        String emailDigest = keys.emailDigest(email);

        Boolean claimed = ownTransaction.execute(status -> {
            if (CODE.matcher(digits).matches()
                    && verifications.claim(id, codeDigest(id, digits), emailDigest, purpose, Instant.now(),
                    MAX_ATTEMPTS) == 1) {
                return true;
            }
            verifications.countGuess(id);
            return false;
        });
        if (!Boolean.TRUE.equals(claimed)) {
            throw new ValidationFailedException(Map.of("verification_code",
                    List.of("The verification code is invalid or has expired. Request a new one.")));
        }
    }

    /** Hands a claimed proof back after the operation it was spent on failed. */
    public void release(Object verificationId) {
        if (verificationId instanceof String id && !id.isBlank()) {
            ownTransaction.executeWithoutResult(status -> verifications.release(id));
        }
    }

    private String codeDigest(String publicId, String code) {
        return keys.digest("code", publicId + ":" + code);
    }

    private GuestCodeMailer mailer() {
        return mailers.stream()
                .filter(mailer -> !(mailer instanceof MailGuestCodeMailer))
                .findFirst()
                .orElseGet(() -> mailers.get(0));
    }

    private static String randomId() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
