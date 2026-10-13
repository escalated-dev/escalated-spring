package dev.escalated.security;

/** An exact, opaque account identity. Numeric strings are never coerced. */
public record TicketAccount(String type, String id) {
    public TicketAccount {
        if (type == null
                || type.isBlank()
                || id == null
                || id.isBlank()
                || type.length() > 191
                || id.length() > 191) {
            throw new IllegalArgumentException(
                    "An account requires a type and id of at most 191 characters");
        }
    }
}
