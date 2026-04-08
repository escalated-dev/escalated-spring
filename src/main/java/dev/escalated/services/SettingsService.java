package dev.escalated.services;

import dev.escalated.models.EscalatedSettings;
import dev.escalated.repositories.EscalatedSettingsRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final EscalatedSettingsRepository settingsRepository;

    public SettingsService(EscalatedSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        return settingsRepository.findByKey(key).map(EscalatedSettings::getValue);
    }

    @Transactional(readOnly = true)
    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value, String group) {
        EscalatedSettings settings = settingsRepository.findByKey(key)
                .orElseGet(() -> {
                    EscalatedSettings ns = new EscalatedSettings();
                    ns.setKey(key);
                    return ns;
                });
        settings.setValue(value);
        if (group != null) {
            settings.setGroup(group);
        }
        settingsRepository.save(settings);
    }

    @Transactional(readOnly = true)
    public List<EscalatedSettings> findByGroup(String group) {
        return settingsRepository.findByGroupOrderByKey(group);
    }

    @Transactional(readOnly = true)
    public List<EscalatedSettings> findAll() {
        return settingsRepository.findAll();
    }

    @Transactional
    public void delete(String key) {
        settingsRepository.findByKey(key).ifPresent(settingsRepository::delete);
    }
}
