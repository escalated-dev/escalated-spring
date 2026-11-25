package dev.escalated.config;

import java.util.Locale;
import java.util.Set;

/** Reads the switches that decide whether an optional group of public routes is mounted. */
final class RouteSwitches {

    private static final Set<String> OFF = Set.of("false", "0", "off", "no");

    private RouteSwitches() {
    }

    /** Unset or blank is on; {@code false}, {@code 0}, {@code off} and {@code no} are off. */
    static boolean isOn(String value) {
        return value == null || value.isBlank() || !OFF.contains(value.trim().toLowerCase(Locale.ROOT));
    }
}
