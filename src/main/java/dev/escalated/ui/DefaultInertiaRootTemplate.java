package dev.escalated.ui;

import dev.escalated.config.EscalatedProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.util.HtmlUtils;

/**
 * The built-in first-visit document: the host's stylesheets and scripts
 * ({@code escalated.ui.stylesheets}, {@code escalated.ui.scripts}), the {@code route()} table, and
 * the page object.
 *
 * <p>The page object is written both ways Inertia clients read it: a
 * {@code <script data-page="app" type="application/json">} element (Inertia 3) and the
 * {@code data-page} attribute of {@code #app} (Inertia 1 and 2).
 */
public class DefaultInertiaRootTemplate implements InertiaRootTemplate {

    private final EscalatedProperties properties;
    private final UiRoutes routes;

    public DefaultInertiaRootTemplate(EscalatedProperties properties, UiRoutes routes) {
        this.properties = properties;
        this.routes = routes;
    }

    @Override
    public String render(Map<String, Object> page, String pageJson, HttpServletRequest request) {
        EscalatedProperties.UiProperties ui = properties.getUi();
        StringBuilder html = new StringBuilder(pageJson.length() * 2 + 1024);
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
                .append("<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>").append(HtmlUtils.htmlEscape(ui.getTitle() == null ? "" : ui.getTitle()))
                .append("</title>\n");
        String csrf = csrfToken(request);
        if (csrf != null) {
            html.append("<meta name=\"csrf-token\" content=\"").append(HtmlUtils.htmlEscape(csrf)).append("\">\n");
        }
        for (String stylesheet : ui.getStylesheets()) {
            html.append("<link rel=\"stylesheet\" href=\"").append(HtmlUtils.htmlEscape(stylesheet)).append("\">\n");
        }
        html.append("<script>").append(routes.script()).append("</script>\n");
        for (String script : ui.getScripts()) {
            html.append("<script type=\"module\" src=\"").append(HtmlUtils.htmlEscape(script)).append("\"></script>\n");
        }
        html.append("</head>\n<body>\n")
                .append("<script data-page=\"app\" type=\"application/json\">").append(scriptSafe(pageJson))
                .append("</script>\n")
                .append("<div id=\"app\" data-page=\"").append(HtmlUtils.htmlEscape(pageJson, "UTF-8"))
                .append("\"></div>\n")
                .append("</body>\n</html>\n");
        return html.toString();
    }

    /**
     * The request's CSRF token, for the {@code csrf-token} meta tag the pages' {@code fetch} calls
     * read. Null when CSRF protection is not in play for the request.
     */
    static String csrfToken(HttpServletRequest request) {
        Object token = request.getAttribute(CsrfToken.class.getName());
        return token instanceof CsrfToken csrf ? csrf.getToken() : null;
    }

    /** JSON safe inside a script element: no tag can open or close in it. */
    static String scriptSafe(String json) {
        return json.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }
}
