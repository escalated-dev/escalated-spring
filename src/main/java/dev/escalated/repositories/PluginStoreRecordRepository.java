package dev.escalated.repositories;

import dev.escalated.models.PluginStoreRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PluginStoreRecordRepository extends JpaRepository<PluginStoreRecord, Long> {

    Optional<PluginStoreRecord> findFirstByPluginAndCollectionAndKeyOrderByIdAsc(
            String plugin, String collection, String key);

    List<PluginStoreRecord> findByPluginAndCollectionOrderByIdAsc(String plugin, String collection);

    List<PluginStoreRecord> findByPluginAndCollectionAndKey(String plugin, String collection, String key);

    long countByPlugin(String plugin);
}
