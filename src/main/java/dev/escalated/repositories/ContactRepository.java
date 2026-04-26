package dev.escalated.repositories;

import dev.escalated.models.Contact;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    /** Email is stored normalized (lowercased + trimmed). */
    Optional<Contact> findByEmail(String email);
}
