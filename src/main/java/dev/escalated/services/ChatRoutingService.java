package dev.escalated.services;

import dev.escalated.models.ChatRoutingRule;
import dev.escalated.repositories.ChatRoutingRuleRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Evaluates chat routing rules to determine which agent or department
 * should receive an incoming chat session.
 */
@Service
public class ChatRoutingService {

    private final ChatRoutingRuleRepository routingRuleRepository;

    public ChatRoutingService(ChatRoutingRuleRepository routingRuleRepository) {
        this.routingRuleRepository = routingRuleRepository;
    }

    /**
     * Resolve the best routing target for a new chat session.
     */
    public RouteResult resolve(Long requestedDepartmentId) {
        List<ChatRoutingRule> rules = routingRuleRepository.findByActiveTrueOrderByPriorityAsc();

        for (ChatRoutingRule rule : rules) {
            if (rule.getDepartmentId() != null && rule.getDepartmentId().equals(requestedDepartmentId)) {
                return new RouteResult(rule.getDepartmentId(), rule.getAgentId());
            }

            if (requestedDepartmentId == null) {
                return new RouteResult(rule.getDepartmentId(), rule.getAgentId());
            }
        }

        return new RouteResult(requestedDepartmentId, null);
    }

    public ChatRoutingRule createRule(String name, Long departmentId, Long agentId,
                                     String conditions, int priority) {
        ChatRoutingRule rule = new ChatRoutingRule();
        rule.setName(name);
        rule.setDepartmentId(departmentId);
        rule.setAgentId(agentId);
        rule.setConditions(conditions);
        rule.setPriority(priority);
        rule.setActive(true);
        return routingRuleRepository.save(rule);
    }

    public List<ChatRoutingRule> getAllRules() {
        return routingRuleRepository.findByActiveTrueOrderByPriorityAsc();
    }

    public void deleteRule(Long ruleId) {
        routingRuleRepository.deleteById(ruleId);
    }

    public record RouteResult(Long departmentId, Long agentId) {}
}
