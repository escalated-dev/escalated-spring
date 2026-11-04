package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.models.TicketStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The built-in statuses are escalated-laravel's TicketStatus values: waiting on the customer and
 * waiting on an agent are two statuses, not one {@code pending}. {@code pending}, the value this
 * port used to store, is still read as waiting on the customer, so workflows, macros, automations,
 * escalation rules and saved views written with it keep working.
 */
class TicketStatusVocabularyTest {

    @Test
    void theWaitingStatusesAreTwoStatusesWithLaravelsValues() {
        assertThat(Arrays.stream(TicketStatus.values()).map(TicketStatuses::slugOf).toList())
                .contains("waiting_on_customer", "waiting_on_agent")
                .doesNotContain("pending");
        assertThat(TicketStatuses.builtIn("waiting_on_customer")).isEqualTo(TicketStatus.WAITING_ON_CUSTOMER);
        assertThat(TicketStatuses.builtIn("waiting_on_agent")).isEqualTo(TicketStatus.WAITING_ON_AGENT);
        assertThat(TicketStatuses.builtIn("WAITING_ON_AGENT")).isEqualTo(TicketStatus.WAITING_ON_AGENT);
    }

    @Test
    void theOldPendingValueIsReadAsWaitingOnTheCustomer() {
        assertThat(TicketStatuses.builtIn("pending")).isEqualTo(TicketStatus.WAITING_ON_CUSTOMER);
        assertThat(TicketStatuses.builtIn("PENDING")).isEqualTo(TicketStatus.WAITING_ON_CUSTOMER);
    }

    @Test
    void waitingOnTheCustomerIsPendingAndWaitingOnAnAgentIsOpen() {
        assertThat(TicketStatuses.categoryOf(TicketStatus.WAITING_ON_CUSTOMER)).isEqualTo("pending");
        assertThat(TicketStatuses.categoryOf(TicketStatus.WAITING_ON_AGENT)).isEqualTo("open");
        assertThat(TicketStatuses.anchorOf("pending")).isEqualTo(TicketStatus.WAITING_ON_CUSTOMER);
    }

    @Test
    void aWorkflowConditionOnThePendingStatusMatchesWaitingOnTheCustomer() {
        WorkflowEngine engine = new WorkflowEngine();
        Map<String, String> waiting = Map.of("status", "waiting_on_customer");
        Map<String, String> onAgent = Map.of("status", "waiting_on_agent");
        Map<String, Object> pending = Map.of("all", List.of(
                Map.of("field", "status", "operator", "equals", "value", "pending")));
        assertThat(engine.evaluateConditions(pending, waiting)).isTrue();
        assertThat(engine.evaluateConditions(pending, onAgent)).isFalse();
        Map<String, Object> notPending = Map.of("all", List.of(
                Map.of("field", "status", "operator", "not_equals", "value", "pending")));
        assertThat(engine.evaluateConditions(notPending, waiting)).isFalse();
        assertThat(engine.evaluateConditions(notPending, onAgent)).isTrue();
    }
}
