package dev.escalated.repositories;

import dev.escalated.models.CustomObject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomObjectRepository extends JpaRepository<CustomObject, Long> {

    List<CustomObject> findAllByOrderByNameAscIdAsc();

    Optional<CustomObject> findBySlug(String slug);
}
