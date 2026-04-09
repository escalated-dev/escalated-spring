package dev.escalated.services;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowEngineTest {
    private final WorkflowEngine engine = new WorkflowEngine();

    @Test void testEqualsOperator() { assertTrue(WorkflowEngine.applyOperator("equals", "open", "open")); }
    @Test void testNotEquals() { assertTrue(WorkflowEngine.applyOperator("not_equals", "open", "closed")); }
    @Test void testContains() { assertTrue(WorkflowEngine.applyOperator("contains", "billing issue", "billing")); }
    @Test void testIsEmpty() { assertTrue(WorkflowEngine.applyOperator("is_empty", "", "")); }
    @Test void testIsNotEmpty() { assertTrue(WorkflowEngine.applyOperator("is_not_empty", "value", "")); }

    @Test void testInterpolate() {
        Map<String, String> ticket = Map.of("reference", "ESC-001", "status", "open");
        assertEquals("Ticket ESC-001 is open", WorkflowEngine.interpolateVariables("Ticket {{reference}} is {{status}}", ticket));
    }

    @Test void testEvaluateAll() {
        Map<String, String> ticket = Map.of("status", "open", "priority", "medium");
        Map<String, Object> conditions = Map.of("all", List.of(
            Map.of("field", "status", "operator", "equals", "value", "open"),
            Map.of("field", "priority", "operator", "equals", "value", "medium")
        ));
        assertTrue(engine.evaluateConditions(conditions, ticket));
    }

    @Test void testEvaluateAny() {
        Map<String, String> ticket = Map.of("status", "open");
        Map<String, Object> conditions = Map.of("any", List.of(
            Map.of("field", "status", "operator", "equals", "value", "closed"),
            Map.of("field", "status", "operator", "equals", "value", "open")
        ));
        assertTrue(engine.evaluateConditions(conditions, ticket));
    }
}
