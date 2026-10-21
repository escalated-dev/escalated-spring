package dev.escalated.services;

/** Too many verification codes for one mailbox this hour; answered as 429 with {@code Retry-After}. */
public class GuestBudgetExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public GuestBudgetExceededException(long retryAfterSeconds) {
        super("Too many verification codes were requested for this email address. Try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
