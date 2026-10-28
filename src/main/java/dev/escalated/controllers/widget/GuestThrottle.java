package dev.escalated.controllers.widget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a guest endpoint as rate-limited per client IP against the given
 * counters (each request counts against every one of them). Enforced by {@link GuestThrottleInterceptor}, which runs before the
 * request body is read and before the handler looks up a guest token.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GuestThrottle {

    Scope[] value();

    /** Each scope has its own counter per IP. */
    enum Scope {
        /** Guest ticket submissions and widget chat starts (each files a ticket). */
        TICKET,
        REPLY,
        /** Every request on the mobile guest routes. */
        REQUEST
    }
}
