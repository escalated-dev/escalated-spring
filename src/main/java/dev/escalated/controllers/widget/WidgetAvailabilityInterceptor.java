package dev.escalated.controllers.widget;

import dev.escalated.config.EscalatedProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Switches the public widget API off as a whole.
 *
 * <p>{@code escalated.widget.enabled=false} used to be read by {@code /config}
 * alone, so ticket creation, chat and the article search stayed open to anyone
 * on a helpdesk that had turned the widget off. Now every widget route answers
 * 403, as in the Laravel reference.
 *
 * <p>The knowledge-base routes ({@code /articles}, {@code /kb/**}) also need
 * {@code escalated.knowledge-base.enabled}; with it off they answer 404, as
 * Laravel's {@code ensureKnowledgeBaseAvailable} does.
 */
@Component
public class WidgetAvailabilityInterceptor implements HandlerInterceptor {

    static final String WIDGET = "/escalated/api/widget";

    private final ObjectProvider<EscalatedProperties> properties;

    public WidgetAvailabilityInterceptor(ObjectProvider<EscalatedProperties> properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals(WIDGET) && !path.startsWith(WIDGET + "/")) {
            return true;
        }
        EscalatedProperties config = properties.getIfAvailable(EscalatedProperties::new);
        if (!config.getWidget().isEnabled()) {
            return refuse(response, 403, "{\"enabled\":false,\"message\":\"The widget is disabled.\"}");
        }
        String rest = path.substring(WIDGET.length());
        boolean knowledgeBase = rest.equals("/articles") || rest.startsWith("/articles/")
                || rest.equals("/kb") || rest.startsWith("/kb/");
        if (knowledgeBase && !config.getKnowledgeBase().isEnabled()) {
            return refuse(response, 404, "{\"message\":\"Not found.\"}");
        }
        return true;
    }

    private static boolean refuse(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
        return false;
    }
}
