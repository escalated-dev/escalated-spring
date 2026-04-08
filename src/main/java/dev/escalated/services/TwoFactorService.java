package dev.escalated.services;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TwoFactorService {

    private static final int SECRET_SIZE = 20;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;

    private final AgentProfileRepository agentRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public TwoFactorService(AgentProfileRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Transactional
    public String enableTwoFactor(Long agentId) {
        AgentProfile agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found: " + agentId));

        byte[] secretBytes = new byte[SECRET_SIZE];
        secureRandom.nextBytes(secretBytes);
        String secret = Base64.getEncoder().encodeToString(secretBytes);

        agent.setTwoFactorSecret(secret);
        agentRepository.save(agent);

        return secret;
    }

    @Transactional
    public boolean verifyAndActivate(Long agentId, String code) {
        AgentProfile agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found: " + agentId));

        if (agent.getTwoFactorSecret() == null) {
            throw new IllegalStateException("2FA not initialized for agent");
        }

        if (verifyCode(agent.getTwoFactorSecret(), code)) {
            agent.setTwoFactorEnabled(true);
            agentRepository.save(agent);
            return true;
        }
        return false;
    }

    public boolean verifyCode(String secret, String code) {
        try {
            long timeStep = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
            for (int i = -1; i <= 1; i++) {
                String generated = generateCode(secret, timeStep + i);
                if (generated.equals(code)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            return false;
        }
        return false;
    }

    @Transactional
    public void disableTwoFactor(Long agentId) {
        AgentProfile agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found: " + agentId));
        agent.setTwoFactorSecret(null);
        agent.setTwoFactorEnabled(false);
        agentRepository.save(agent);
    }

    private String generateCode(String secret, long timeStep) throws Exception {
        byte[] secretBytes = Base64.getDecoder().decode(secret);
        byte[] timeBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            timeBytes[i] = (byte) (timeStep & 0xff);
            timeStep >>= 8;
        }

        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
        byte[] hash = mac.doFinal(timeBytes);

        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);

        int otp = binary % (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", otp);
    }

    public String getProvisioningUri(String secret, String email, String issuer) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d",
                issuer, email, secret, issuer, CODE_DIGITS, TIME_STEP_SECONDS);
    }
}
