package dev.escalated.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Pure-function tests for {@link Contact}. Covers {@code normalizeEmail},
 * {@code decideAction}, the email-normalizing setter, and defaults.
 * Matches the equivalents passing in NestJS, Adonis, Go, .NET, Symfony,
 * WordPress, Phoenix.
 */
class ContactTest {

    // -----------------------------------------------------------------
    // normalizeEmail
    // -----------------------------------------------------------------

    @Test
    void normalizeEmail_lowercases() {
        assertEquals("alice@example.com", Contact.normalizeEmail("ALICE@Example.COM"));
    }

    @Test
    void normalizeEmail_trimsWhitespace() {
        assertEquals("alice@example.com", Contact.normalizeEmail("  alice@example.com  "));
    }

    @Test
    void normalizeEmail_doesBothInOnePass() {
        assertEquals("mixed@case.com", Contact.normalizeEmail("  MiXeD@Case.COM  "));
    }

    @Test
    void normalizeEmail_handlesNull() {
        assertEquals("", Contact.normalizeEmail(null));
    }

    @Test
    void normalizeEmail_handlesEmpty() {
        assertEquals("", Contact.normalizeEmail(""));
    }

    // -----------------------------------------------------------------
    // decideAction
    // -----------------------------------------------------------------

    @Test
    void decideAction_createWhenNoExisting() {
        assertEquals(Contact.Action.CREATE, Contact.decideAction(null, "Alice"));
    }

    @Test
    void decideAction_returnExistingWhenExistingHasName() {
        Contact existing = new Contact();
        existing.setName("Alice");
        assertEquals(Contact.Action.RETURN_EXISTING, Contact.decideAction(existing, "Different"));
    }

    @Test
    void decideAction_updateNameWhenExistingNameIsNull() {
        Contact existing = new Contact();
        assertEquals(Contact.Action.UPDATE_NAME, Contact.decideAction(existing, "Alice"));
    }

    @Test
    void decideAction_updateNameWhenExistingNameIsEmpty() {
        Contact existing = new Contact();
        existing.setName("");
        assertEquals(Contact.Action.UPDATE_NAME, Contact.decideAction(existing, "Alice"));
    }

    @Test
    void decideAction_returnExistingWhenExistingNameIsBlankAndNoIncomingName() {
        Contact existing = new Contact();
        assertEquals(Contact.Action.RETURN_EXISTING, Contact.decideAction(existing, null));
        assertEquals(Contact.Action.RETURN_EXISTING, Contact.decideAction(existing, ""));
    }

    // -----------------------------------------------------------------
    // setEmail normalizes on write
    // -----------------------------------------------------------------

    @Test
    void setEmail_normalizesOnWrite() {
        Contact c = new Contact();
        c.setEmail("  MIX@Case.COM ");
        assertEquals("mix@case.com", c.getEmail());
    }

    // -----------------------------------------------------------------
    // Defaults
    // -----------------------------------------------------------------

    @Test
    void defaults_nullFields() {
        Contact c = new Contact();
        assertNull(c.getEmail());
        assertNull(c.getName());
        assertNull(c.getUserId());
        assertNull(c.getMetadataJson());
    }
}
