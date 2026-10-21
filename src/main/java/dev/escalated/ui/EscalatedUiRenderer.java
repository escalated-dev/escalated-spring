package dev.escalated.ui;

import java.util.Map;

/**
 * Renders one of the shared pages. Every Escalated UI controller calls this rather than rendering
 * directly, as in Laravel's {@code EscalatedUiRenderer}.
 *
 * <p>The default is {@link InertiaUiRenderer}. A host that renders the pages some other way
 * (server-side templates, its own SPA shell) defines a bean of this type instead. The return value
 * is anything a Spring MVC handler may return: a {@code ResponseEntity}, a {@code ModelAndView}, a
 * view name.
 */
public interface EscalatedUiRenderer {

    Object render(String page, Map<String, Object> props);
}
