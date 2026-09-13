package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.ApiToken;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ApiTokenRepository;
import jakarta.persistence.EntityNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiTokenService {

    private final ApiTokenRepository tokenRepository;
    private final AgentProfileRepository agentRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiTokenService(ApiTokenRepository tokenRepository,
                           AgentProfileRepository agentRepository) {
        this.tokenRepository = tokenRepository;
        this.agentRepository = agentRepository;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> createToken(String name, Long agentId, String abilities, Instant expiresAt) {
        AgentProfile agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found: " + agentId));

        byte[] rawToken = new byte[40];
        secureRandom.nextBytes(rawToken);
        String plainTextToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken);

        ApiToken token = new ApiToken();
        token.setName(name);
        token.setTokenHash(hashToken(plainTextToken));
        token.setAbilities(abilities != null ? abilities : "*");
        token.setExpiresAt(expiresAt);
        token.setAgent(agent);

        ApiToken saved = tokenRepository.save(token);
        return Map.of("token", saved, "plainTextToken", plainTextToken);
    }

    /**
     * Returns the token with its agent loaded, so the caller can read the agent
     * after this transaction has closed.
     *
     * <p>Read-write on purpose: it records {@code last_used_at}, and a
     * read-only transaction silently never flushes that update.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public ApiToken validateToken(String plainTextToken) {
        String hash = hashToken(plainTextToken);
        ApiToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new EntityNotFoundException("Invalid API token"));

        if (token.isExpired()) {
            throw new RuntimeException("API token has expired");
        }

        token.setLastUsedAt(Instant.now());
        return tokenRepository.save(token);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<ApiToken> findByAgent(Long agentId) {
        return tokenRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void delete(Long id) {
        tokenRepository.deleteById(id);
    }

    private String hashToken(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256 not available", ex);
        }
    }
}
