package dev.escalated.services.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default {@link GuestRateLimitStore}: fixed windows in a concurrent map,
 * per process. Expired windows are swept at most once a minute so the map does
 * not grow with every address that ever called.
 */
public class InMemoryGuestRateLimitStore implements GuestRateLimitStore {

    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private volatile Instant nextSweep;

    public InMemoryGuestRateLimitStore() {
        this(Clock.systemUTC());
    }

    public InMemoryGuestRateLimitStore(Clock clock) {
        this.clock = clock;
        this.nextSweep = clock.instant().plus(SWEEP_INTERVAL);
    }

    @Override
    public Hit increment(String key, Duration window) {
        Instant now = clock.instant();
        sweep(now);

        Window current = windows.compute(key, (k, existing) ->
                existing == null || !now.isBefore(existing.endsAt())
                        ? new Window(now.plus(window), 1)
                        : new Window(existing.endsAt(), existing.count() + 1));

        return new Hit(current.count(), Duration.between(now, current.endsAt()));
    }

    private void sweep(Instant now) {
        if (now.isBefore(nextSweep)) {
            return;
        }
        nextSweep = now.plus(SWEEP_INTERVAL);
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().endsAt()));
    }

    private record Window(Instant endsAt, long count) {
    }
}
