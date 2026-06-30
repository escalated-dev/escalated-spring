package dev.escalated.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FollowerRecipientsTest {

    @Test
    void excludesActorAndDeduplicates() {
        assertEquals(
                List.of("7", "3"), FollowerRecipients.resolve(List.of("7", "2", "7", "3"), "2"));
    }

    @Test
    void keepsAllDeduplicatedWhenNoActorExcluded() {
        assertEquals(List.of("7", "3"), FollowerRecipients.resolve(Arrays.asList("7", "3", "7"), null));
    }
}
