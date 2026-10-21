package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.RuleExecution;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.RuleExecutionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fire-once bookkeeping for the sweeps (escalation rules, SLA warnings and
 * breaches, automations). Mirrors Laravel's {@code RuleFireLog}.
 *
 * <p>A rule fires once per ticket. When a ticket stops matching the rule its
 * record is removed, so the rule fires again if the ticket matches later. A
 * rule with {@code repeatAfterHours} also fires again once that many hours have
 * passed since it last fired, while the ticket still matches.
 */
@Service
public class RuleFireLog {

    private final RuleExecutionRepository executions;

    public RuleFireLog(RuleExecutionRepository executions) {
        this.executions = executions;
    }

    /**
     * Re-arms the rule for tickets that no longer match, and returns the
     * matching tickets the rule is due to act on.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public List<Ticket> due(String ruleType, Long ruleId, Integer repeatAfterHours, List<Ticket> matching) {
        Map<Long, RuleExecution> fired = executions.findByRuleTypeAndRuleId(ruleType, ruleId).stream()
                .collect(Collectors.toMap(RuleExecution::getTicketId, Function.identity(), (a, b) -> a));
        Set<Long> matchingIds = matching.stream().map(Ticket::getId).collect(Collectors.toSet());

        List<RuleExecution> stale = fired.values().stream()
                .filter(execution -> !matchingIds.contains(execution.getTicketId()))
                .toList();
        if (!stale.isEmpty()) {
            executions.deleteAllInBatch(stale);
        }

        Instant repeatBefore = repeatAfterHours != null && repeatAfterHours > 0
                ? Instant.now().minus(Duration.ofHours(repeatAfterHours))
                : null;

        return matching.stream()
                .filter(ticket -> {
                    RuleExecution execution = fired.get(ticket.getId());
                    if (execution == null) {
                        return true;
                    }
                    return repeatBefore != null && execution.getFiredAt() != null
                            && !execution.getFiredAt().isAfter(repeatBefore);
                })
                .toList();
    }

    /** Whether the rule has a record for the ticket. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public boolean hasFired(String ruleType, Long ruleId, Long ticketId) {
        return executions.findByRuleTypeAndRuleIdAndTicketId(ruleType, ruleId, ticketId).isPresent();
    }

    /**
     * Records that the rule acted on the ticket now. Runs in a transaction of
     * its own, so a concurrent sweep that inserted the row first only costs a
     * refresh of its time.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, propagation = Propagation.REQUIRES_NEW)
    public void record(String ruleType, Long ruleId, Long ticketId) {
        RuleExecution execution = executions.findByRuleTypeAndRuleIdAndTicketId(ruleType, ruleId, ticketId)
                .orElseGet(() -> {
                    RuleExecution created = new RuleExecution();
                    created.setRuleType(ruleType);
                    created.setRuleId(ruleId);
                    created.setTicketId(ticketId);
                    return created;
                });
        execution.setFiredAt(Instant.now());
        try {
            executions.saveAndFlush(execution);
        } catch (DataIntegrityViolationException raced) {
            // Another sweep recorded it first; its row stands.
        }
    }

    /** Forgets every record of a rule, e.g. when the rule is deleted. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void forget(String ruleType, Long ruleId) {
        executions.deleteByRuleTypeAndRuleId(ruleType, ruleId);
    }
}
