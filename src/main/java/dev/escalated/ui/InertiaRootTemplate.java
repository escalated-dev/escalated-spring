package dev.escalated.ui;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * The HTML document a first (non-Inertia) visit receives. It must carry the page object, HTML
 * escaped, in a {@code data-page} attribute of the element the host's frontend mounts on, and load
 * that frontend.
 *
 * <p>The default is {@link DefaultInertiaRootTemplate}. A host replaces it with a bean of this
 * type, or names a Thymeleaf view in {@code escalated.ui.root-view}.
 */
public interface InertiaRootTemplate {

    /**
     * @param page the page object ({@code component}, {@code props}, {@code url}, {@code version})
     * @param pageJson the same, as JSON (not yet HTML escaped)
     */
    String render(Map<String, Object> page, String pageJson, HttpServletRequest request);
}
