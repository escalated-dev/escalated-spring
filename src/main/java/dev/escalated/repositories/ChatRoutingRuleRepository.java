package dev.escalated.repositories;

import dev.escalated.models.ChatRoutingRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRoutingRuleRepository extends JpaRepository<ChatRoutingRule, Long> {

    List<ChatRoutingRule> findByActiveTrueOrderByPriorityAsc();
}
