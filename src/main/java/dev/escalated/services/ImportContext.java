package dev.escalated.services;

import java.util.function.Supplier;

/**
 * Import mode, as escalated-laravel's {@code ImportContext}. While an import runs, SLA policy
 * attachment, breach checks and automations stand down, so imported history does not fire as if
 * it were new. Thread-bound: the import runs on its own thread, so live requests are never in it.
 */
public final class ImportContext {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private ImportContext() {
    }

    public static boolean isImporting() {
        return DEPTH.get() > 0;
    }

    public static <T> T suppress(Supplier<T> work) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            return work.get();
        } finally {
            int depth = DEPTH.get() - 1;
            if (depth == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(depth);
            }
        }
    }
}
