package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
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

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Optional<String> get(String key) {
        return settingsRepository.findByKey(key).map(EscalatedSettings::getValue);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
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

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<EscalatedSettings> findByGroup(String group) {
        return settingsRepository.findByGroupOrderByKey(group);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<EscalatedSettings> findAll() {
        return settingsRepository.findAll();
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void delete(String key) {
        settingsRepository.findByKey(key).ifPresent(settingsRepository::delete);
    }
}
