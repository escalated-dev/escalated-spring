package dev.escalated.services.ratelimit;

import java.time.Duration;

/**
 * Where the guest endpoint counters live. The default keeps them in process
 * memory; a host running several instances defines a bean of this type backed
 * by something they share (e.g. Redis), and Escalated uses it instead.
 */
public interface GuestRateLimitStore {

    /**
     * Counts one hit against {@code key} in a fixed window of {@code window}
     * that opens at the key's first hit.
     *
     * @return the hits counted in the current window, including this one, and
     *         how long until that window closes
     */
    Hit increment(String key, Duration window);

    /** The state of a key's window after a hit. */
    record Hit(long count, Duration resetsIn) {
    }
}
