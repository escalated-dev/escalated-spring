package dev.escalated.repositories;

import dev.escalated.models.RuleExecution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleExecutionRepository extends JpaRepository<RuleExecution, Long> {

    List<RuleExecution> findByRuleTypeAndRuleId(String ruleType, Long ruleId);

    Optional<RuleExecution> findByRuleTypeAndRuleIdAndTicketId(String ruleType, Long ruleId, Long ticketId);

    void deleteByRuleTypeAndRuleId(String ruleType, Long ruleId);
}
