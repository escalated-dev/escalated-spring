package dev.escalated.repositories;

import dev.escalated.models.CustomObjectRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomObjectRecordRepository extends JpaRepository<CustomObjectRecord, Long> {

    List<CustomObjectRecord> findByObjectIdOrderByIdDesc(Long objectId);

    Optional<CustomObjectRecord> findByIdAndObjectId(Long id, Long objectId);

    long countByObjectId(Long objectId);

    @Query("select r.objectId, count(r) from CustomObjectRecord r group by r.objectId")
    List<Object[]> countByObject();

    @Modifying
    @Query("delete from CustomObjectRecord r where r.objectId = :objectId")
    int deleteByObjectId(@Param("objectId") Long objectId);
}
