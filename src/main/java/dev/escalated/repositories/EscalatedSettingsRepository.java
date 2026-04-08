package dev.escalated.repositories;

import dev.escalated.models.EscalatedSettings;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EscalatedSettingsRepository extends JpaRepository<EscalatedSettings, Long> {

    Optional<EscalatedSettings> findByKey(String key);

    List<EscalatedSettings> findByGroupOrderByKey(String group);
}
