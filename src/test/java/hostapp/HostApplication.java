package hostapp;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A host application in a package of its own.
 *
 * <p>{@code dev.escalated.TestApplication} sits inside Escalated's own package,
 * so Boot's default entity scan picks up {@code dev.escalated.models} whether or
 * not Escalated asked it to — which makes it useless for proving where
 * Escalated's tables end up. A real host is somewhere else entirely, and this
 * stands in for one.
 */
@SpringBootApplication
public class HostApplication {
}
