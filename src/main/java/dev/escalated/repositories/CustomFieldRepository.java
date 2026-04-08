package dev.escalated.repositories;

import dev.escalated.models.CustomField;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomFieldRepository extends JpaRepository<CustomField, Long> {

    Optional<CustomField> findByFieldKey(String fieldKey);

    List<CustomField> findByActiveTrueOrderBySortOrderAsc();
}
