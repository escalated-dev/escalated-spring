package dev.escalated.repositories;

import dev.escalated.models.EscalationRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EscalationRuleRepository extends JpaRepository<EscalationRule, Long> {

    List<EscalationRule> findBySlaPolicyIdAndActiveTrueOrderByMinutesBeforeOrAfterAsc(Long slaPolicyId);
}
