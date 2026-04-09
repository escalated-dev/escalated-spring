package dev.escalated.services;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MentionServiceTest {
    private final MentionService service = new MentionService();

    @Test void testSingleMention() { assertEquals(List.of("john"), service.extractMentions("Hello @john please review")); }
    @Test void testMultipleMentions() { var r = service.extractMentions("@alice and @bob"); assertTrue(r.contains("alice")); assertTrue(r.contains("bob")); }
    @Test void testDottedUsername() { assertEquals(List.of("john.doe"), service.extractMentions("cc @john.doe")); }
    @Test void testDeduplicates() { assertEquals(1, service.extractMentions("@alice said @alice").size()); }
    @Test void testEmpty() { assertTrue(service.extractMentions("").isEmpty()); }
    @Test void testNull() { assertTrue(service.extractMentions(null).isEmpty()); }
    @Test void testNoMentions() { assertTrue(service.extractMentions("No mentions").isEmpty()); }
    @Test void testExtractUsername() { assertEquals("john", service.extractUsernameFromEmail("john@example.com")); }
}
