package dev.escalated.plugins;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Five-field cron expressions ({@code minute hour day-of-month month day-of-week}), as the plugin
 * SDK's {@code cron} keys are written, evaluated in UTC. Supports {@code *}, numbers, {@code a-b}
 * ranges, {@code /step}, comma lists, month and weekday names, 7 as Sunday, and the
 * {@code @hourly}-style shorthands. When both day fields are restricted, either one matching is
 * enough, as cron does.
 */
final class PluginCron {

    private static final Map<String, String> SHORTHANDS = Map.of(
            "@yearly", "0 0 1 1 *",
            "@annually", "0 0 1 1 *",
            "@monthly", "0 0 1 * *",
            "@weekly", "0 0 * * 0",
            "@daily", "0 0 * * *",
            "@midnight", "0 0 * * *",
            "@hourly", "0 * * * *");

    private static final List<String> MONTHS =
            List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");
    private static final List<String> DAYS = List.of("sun", "mon", "tue", "wed", "thu", "fri", "sat");

    private record Field(int min, int max, List<String> names, int base) {
    }

    private static final List<Field> FIELDS = List.of(
            new Field(0, 59, List.of(), 0),
            new Field(0, 23, List.of(), 0),
            new Field(1, 31, List.of(), 0),
            new Field(1, 12, MONTHS, 1),
            new Field(0, 7, DAYS, 0));

    private record Parsed(Set<Integer> values, boolean any) {
    }

    private PluginCron() {
    }

    /**
     * Whether {@code expression} is due in the minute containing {@code at} (UTC).
     *
     * @throws IllegalArgumentException when the expression cannot be read
     */
    static boolean due(String expression, Instant at) {
        String text = expression == null ? "" : expression.trim();
        text = SHORTHANDS.getOrDefault(text.toLowerCase(Locale.ROOT), text);
        String[] parts = text.isEmpty() ? new String[0] : text.split("\\s+");
        if (parts.length != 5) {
            throw invalid(expression);
        }
        Parsed[] fields = new Parsed[5];
        for (int i = 0; i < 5; i++) {
            fields[i] = parse(parts[i], FIELDS.get(i), expression);
        }
        if (fields[4].values().contains(7)) {
            fields[4].values().add(0);
        }
        ZonedDateTime time = at.atZone(ZoneOffset.UTC);
        if (!fields[0].values().contains(time.getMinute()) || !fields[1].values().contains(time.getHour())
                || !fields[3].values().contains(time.getMonthValue())) {
            return false;
        }
        boolean dom = fields[2].values().contains(time.getDayOfMonth());
        boolean dow = fields[4].values().contains(time.getDayOfWeek().getValue() % 7);
        if (!fields[2].any() && !fields[4].any()) {
            return dom || dow;
        }
        return dom && dow;
    }

    private static Parsed parse(String text, Field field, String expression) {
        Set<Integer> values = new HashSet<>();
        boolean any = false;
        for (String part : text.split(",", -1)) {
            String[] pieces = part.split("/", -1);
            if (pieces.length > 2) {
                throw invalid(expression);
            }
            int step = 1;
            if (pieces.length == 2) {
                step = number(pieces[1], 1, Integer.MAX_VALUE, expression);
            }
            String range = pieces[0];
            int from;
            int to;
            if ("*".equals(range)) {
                from = field.min();
                to = field.max();
                any = pieces.length == 1;
            } else if (range.contains("-")) {
                String[] ends = range.split("-", -1);
                if (ends.length != 2) {
                    throw invalid(expression);
                }
                from = value(ends[0], field, expression);
                to = value(ends[1], field, expression);
                if (from > to) {
                    throw invalid(expression);
                }
            } else {
                from = value(range, field, expression);
                to = pieces.length == 2 ? field.max() : from;
            }
            for (int n = from; n <= to; n += step) {
                values.add(n);
            }
        }
        return new Parsed(values, any);
    }

    private static int value(String token, Field field, String expression) {
        int name = field.names().indexOf(token.toLowerCase(Locale.ROOT));
        if (name >= 0) {
            return name + field.base();
        }
        return number(token, field.min(), field.max(), expression);
    }

    private static int number(String token, int min, int max, String expression) {
        if (token.isEmpty() || !token.chars().allMatch(Character::isDigit) || token.length() > 9) {
            throw invalid(expression);
        }
        int n = Integer.parseInt(token);
        if (n < min || n > max) {
            throw invalid(expression);
        }
        return n;
    }

    private static IllegalArgumentException invalid(String expression) {
        return new IllegalArgumentException("invalid cron expression \"" + expression + "\"");
    }
}
