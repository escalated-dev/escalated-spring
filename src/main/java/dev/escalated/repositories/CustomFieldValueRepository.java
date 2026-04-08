package dev.escalated.repositories;

import dev.escalated.models.CustomFieldValue;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomFieldValueRepository extends JpaRepository<CustomFieldValue, Long> {

    List<CustomFieldValue> findByTicketId(Long ticketId);

    Optional<CustomFieldValue> findByTicketIdAndCustomFieldId(Long ticketId, Long customFieldId);
}
