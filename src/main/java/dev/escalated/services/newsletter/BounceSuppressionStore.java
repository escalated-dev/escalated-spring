package dev.escalated.services.newsletter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.EscalatedSettings;
import dev.escalated.repositories.EscalatedSettingsRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class BounceSuppressionStore {

    static final String KEY = "newsletter.suppressed_emails";

    private final EscalatedSettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;

    public BounceSuppressionStore(EscalatedSettingsRepository settingsRepository, ObjectMapper objectMapper) {
        this.settingsRepository = settingsRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void markBounced(String email) {
        mark(email);
    }

    @Transactional
    public void markComplained(String email) {
        mark(email);
    }

    @Transactional(readOnly = true)
    public boolean isBounced(String email) {
        return load().contains(email.toLowerCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public List<String> filterSendable(List<String> emails) {
        Set<String> suppressed = load();
        List<String> sendable = new ArrayList<>();
        for (String email : emails) {
            if (!suppressed.contains(email.toLowerCase(Locale.ROOT))) {
                sendable.add(email);
            }
        }
        return sendable;
    }

    private void mark(String email) {
        String lowered = email.toLowerCase(Locale.ROOT);
        Set<String> list = new LinkedHashSet<>(load());
        if (!list.add(lowered)) {
            return;
        }
        persist(new ArrayList<>(list));
    }

    private Set<String> load() {
        return settingsRepository.findByKey(KEY)
                .map(EscalatedSettings::getValue)
                .map(this::parseList)
                .orElseGet(LinkedHashSet::new);
    }

    private Set<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            Set<String> lowered = new LinkedHashSet<>();
            for (String entry : parsed) {
                lowered.add(entry.toLowerCase(Locale.ROOT));
            }
            return lowered;
        } catch (Exception ex) {
            return new LinkedHashSet<>();
        }
    }

    private void persist(List<String> emails) {
        String json;
        try {
            json = objectMapper.writeValueAsString(emails);
        } catch (Exception ex) {
            json = "[]";
        }
        EscalatedSettings row = settingsRepository.findByKey(KEY).orElseGet(() -> {
            EscalatedSettings settings = new EscalatedSettings();
            settings.setKey(KEY);
            return settings;
        });
        row.setValue(json);
        row.setGroup("newsletter");
        settingsRepository.save(row);
    }
}
