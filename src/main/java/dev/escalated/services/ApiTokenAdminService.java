package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.ApiToken;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ApiTokenRepository;
import dev.escalated.security.EscalatedAuthorization;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin management of API tokens, as in Laravel's {@code ApiTokenController}:
 * list, issue (the plain-text token is answered once), rename or change
 * abilities, revoke. Abilities are {@code agent}, {@code admin} and
 * {@code *}; the token's owner must still pass the matching gate.
 */
@Service
public class ApiTokenAdminService {

    private final ApiTokenService tokens;
    private final ApiTokenRepository repository;
    private final AgentProfileRepository agents;

    public ApiTokenAdminService(ApiTokenService tokens, ApiTokenRepository repository,
                                AgentProfileRepository agents) {
        this.tokens = tokens;
        this.repository = repository;
        this.agents = agents;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Map<String, Object>> list() {
        return repository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(ApiTokenAdminService::view)
                .toList();
    }

    /**
     * Issues a token from {@code name}, {@code agent_id} (or {@code user_id}),
     * {@code abilities} (default {@code ["*"]}) and {@code expires_in_days}
     * (1-365, optional). Answers {@code {token, plain_text_token}}.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> issue(Map<String, Object> body) {
        Map<String, Object> input = body == null ? Map.of() : body;
        Map<String, List<String>> errors = new LinkedHashMap<>();

        String name = name(input.get("name"), true, errors);
        Object rawAgent = input.containsKey("agent_id") ? input.get("agent_id") : input.get("user_id");
        Long agentId = asLong(rawAgent);
        AgentProfile agent = null;
        if (agentId == null) {
            errors.put("agent_id", List.of("The agent id field is required."));
        } else {
            agent = agents.findById(agentId).orElse(null);
            if (agent == null || !agent.isActive() || !(agent.isAgent() || agent.isAdmin())) {
                errors.put("agent_id", List.of("The selected agent is invalid."));
            }
        }
        List<String> abilities = abilities(input.containsKey("abilities") ? input.get("abilities") : List.of("*"),
                errors);
        Instant expiresAt = null;
        Object days = input.get("expires_in_days");
        if (days != null && !(days instanceof String blank && blank.isBlank())) {
            Long value = asLong(days);
            if (value == null || value < 1 || value > 365) {
                errors.put("expires_in_days", List.of("The expires in days must be between 1 and 365."));
            } else {
                expiresAt = Instant.now().plus(Duration.ofDays(value));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        Map<String, Object> created = tokens.createToken(name, agent.getId(), String.join(",", abilities), expiresAt);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("token", view((ApiToken) created.get("token")));
        answer.put("plain_text_token", created.get("plainTextToken"));
        return answer;
    }

    /** Renames a token or changes its abilities ({@code name}, {@code abilities}, both optional). */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        ApiToken token = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Token not found"));
        Map<String, Object> input = body == null ? Map.of() : body;
        Map<String, List<String>> errors = new LinkedHashMap<>();
        String name = input.containsKey("name") ? name(input.get("name"), true, errors) : null;
        List<String> abilities = input.containsKey("abilities") ? abilities(input.get("abilities"), errors) : null;
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        if (name != null) {
            token.setName(name);
        }
        if (abilities != null) {
            token.setAbilities(String.join(",", abilities));
        }
        return view(repository.save(token));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void revoke(Long id) {
        ApiToken token = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Token not found"));
        repository.delete(token);
    }

    private static String name(Object raw, boolean required, Map<String, List<String>> errors) {
        if (!(raw instanceof String text) || text.isBlank()) {
            if (required) {
                errors.put("name", List.of("The name field is required."));
            }
            return null;
        }
        if (text.length() > 255) {
            errors.put("name", List.of("The name may not be greater than 255 characters."));
            return null;
        }
        return text.trim();
    }

    private static List<String> abilities(Object raw, Map<String, List<String>> errors) {
        if (!(raw instanceof List<?> items) || items.isEmpty()) {
            errors.put("abilities", List.of("The abilities field must be a list of agent, admin or *."));
            return null;
        }
        List<String> abilities = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof String ability) || !EscalatedAuthorization.ABILITIES.contains(ability)) {
                errors.put("abilities." + i, List.of("The selected ability is invalid."));
                continue;
            }
            if (!abilities.contains(ability)) {
                abilities.add(ability);
            }
        }
        return abilities;
    }

    /** A token as admins see it: never its hash or plain text. */
    private static Map<String, Object> view(ApiToken token) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", token.getId());
        view.put("name", token.getName());
        AgentProfile agent = token.getAgent();
        view.put("agent_id", agent == null ? null : agent.getId());
        view.put("user_name", agent == null ? null : agent.getName());
        view.put("user_email", agent == null ? null : agent.getEmail());
        view.put("abilities", List.copyOf(EscalatedAuthorization.abilitiesOf(token.getAbilities())));
        view.put("last_used_at", token.getLastUsedAt());
        view.put("expires_at", token.getExpiresAt());
        view.put("is_expired", token.isExpired());
        view.put("created_at", token.getCreatedAt());
        return view;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
