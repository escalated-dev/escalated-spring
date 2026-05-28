package dev.escalated.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedProperties.TicketActionProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TicketActionRegistryTest {

    private TicketActionProperties action(String key, String label) {
        TicketActionProperties a = new TicketActionProperties();
        a.setKey(key);
        a.setLabel(label);
        return a;
    }

    private TicketActionRegistry registry(TicketActionProperties... actions) {
        EscalatedProperties props = new EscalatedProperties();
        props.setTicketActions(List.of(actions));
        return new TicketActionRegistry(props);
    }

    @Test
    void forTicketSerializesVisibleActionsWithDefaults() {
        TicketActionRegistry reg = registry(action("sync-crm", "Sync CRM"));

        List<Map<String, Object>> actions = reg.forTicket();

        assertEquals(1, actions.size());
        assertEquals("sync-crm", actions.get(0).get("key"));
        assertEquals("Sync CRM", actions.get(0).get("label"));
        assertEquals("secondary", actions.get(0).get("variant"));
        assertEquals(false, actions.get(0).get("disabled"));
    }

    @Test
    void omitsInvisibleActionsAndMarksDisabled() {
        TicketActionProperties hidden = action("hidden", "Hidden");
        hidden.setVisible(false);
        TicketActionProperties locked = action("locked", "Locked");
        locked.setEnabled(false);

        List<Map<String, Object>> actions = registry(hidden, locked).forTicket();

        assertEquals(1, actions.size());
        assertEquals("locked", actions.get(0).get("key"));
        assertEquals(true, actions.get(0).get("disabled"));
    }

    @Test
    void findReturnsConfigOrNull() {
        TicketActionRegistry reg = registry(action("a", "A"));

        assertNotNull(reg.find("a"));
        assertNull(reg.find("missing"));
    }
}
