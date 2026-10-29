package dev.escalated.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PluginCronTest {

    private static boolean due(String expression, String at) {
        return PluginCron.due(expression, OffsetDateTime.parse(at).toInstant());
    }

    @Test
    void matchesEveryMinuteFixedTimesRangesStepsAndLists() {
        assertThat(due("* * * * *", "2026-10-29T13:37:00Z")).isTrue();
        assertThat(due("37 13 * * *", "2026-10-29T13:37:45Z")).isTrue();
        assertThat(due("38 13 * * *", "2026-10-29T13:37:00Z")).isFalse();
        assertThat(due("*/15 * * * *", "2026-10-29T13:45:00Z")).isTrue();
        assertThat(due("*/15 * * * *", "2026-10-29T13:46:00Z")).isFalse();
        assertThat(due("0-10/5 9-17 * * *", "2026-10-29T09:10:00Z")).isTrue();
        assertThat(due("0-10/5 9-17 * * *", "2026-10-29T18:10:00Z")).isFalse();
        assertThat(due("1,2,3 * * * *", "2026-10-29T13:02:00Z")).isTrue();
    }

    @Test
    void readsNamesSevenAsSundayAndTheEitherDayRuleInUtc() {
        // 2026-10-29 is a Thursday; 2026-11-01 a Sunday.
        assertThat(due("0 9 * * thu", "2026-10-29T09:00:00Z")).isTrue();
        assertThat(due("0 9 * * MON-WED", "2026-10-29T09:00:00Z")).isFalse();
        assertThat(due("0 0 * * 7", "2026-11-01T00:00:00Z")).isTrue();
        assertThat(due("0 0 1 nov *", "2026-11-01T00:00:00Z")).isTrue();
        assertThat(due("0 0 13 * 4", "2026-10-29T00:00:00Z")).isTrue();
        assertThat(due("0 0 13 * 5", "2026-10-29T00:00:00Z")).isFalse();
        assertThat(due("0 0 29 * *", "2026-10-29T00:00:00Z")).isTrue();
        assertThat(due("0 9 * * *", "2026-10-29T05:00:00-04:00")).isTrue();
        assertThat(due("@hourly", "2026-10-29T13:00:00Z")).isTrue();
        assertThat(due("@daily", "2026-10-29T13:00:00Z")).isFalse();
        assertThat(due("@weekly", "2026-11-01T00:00:00Z")).isTrue();
    }

    @Test
    void refusesWhatItCannotRead() {
        for (String bad : new String[] {"", "* * * *", "61 * * * *", "* * * * * *", "a b c d e", "*/0 * * * *",
            "5-1 * * * *"}) {
            assertThatThrownBy(() -> due(bad, "2026-10-29T13:37:00Z")).as(bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
