package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.HtmlUtils;

class InertiaTest {

    private final EscalatedProperties properties = new EscalatedProperties();
    private final UiRoutes routes = new UiRoutes();
    private final Inertia inertia = new Inertia(properties, List.of(request -> Map.of("errors", Map.of())),
            new DefaultInertiaRootTemplate(properties, routes), routes);

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void propsCannotBreakOutOfTheDocument() throws Exception {
        visit(new MockHttpServletRequest("GET", "/escalated/agent"));
        String hostile = "</script><script>alert(1)</script>\"'&";

        ResponseEntity<?> response = (ResponseEntity<?>) inertia.render("Escalated/Agent/Dashboard",
                Map.of("subject", hostile));
        String html = (String) response.getBody();

        assertThat(html).doesNotContain("<script>alert(1)");
        String script = html.substring(html.indexOf("application/json\">") + "application/json\">".length(),
                html.indexOf("</script>", html.indexOf("application/json\">")));
        assertThat(new ObjectMapper().readTree(script).get("props").get("subject").asText()).isEqualTo(hostile);

        String attribute = html.substring(html.indexOf("data-page=\"", html.indexOf("<div")) + "data-page=\"".length());
        attribute = attribute.substring(0, attribute.indexOf('"'));
        JsonNode fromAttribute = new ObjectMapper().readTree(HtmlUtils.htmlUnescape(attribute));
        assertThat(fromAttribute.get("props").get("subject").asText()).isEqualTo(hostile);
    }

    @Test
    void aRootViewGetsThePageObjectAndTheRouteScript() {
        properties.getUi().setRootView("escalated/app");
        visit(new MockHttpServletRequest("GET", "/escalated/agent"));

        ModelAndView view = (ModelAndView) inertia.render("Escalated/Agent/Dashboard", Map.of("stats", Map.of()));

        assertThat(view.getViewName()).isEqualTo("escalated/app");
        assertThat(view.getModel()).containsKeys("page", "pageJson", "routesScript");
        assertThat((String) view.getModel().get("pageJson")).contains("\"component\":\"Escalated/Agent/Dashboard\"");
    }

    @Test
    void aLazyPropIsOnlyEvaluatedWhenSent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/escalated/agent/tickets");
        request.addHeader("X-Inertia", "true");
        request.addHeader("X-Inertia-Partial-Component", "Escalated/Agent/TicketIndex");
        request.addHeader("X-Inertia-Partial-Data", "tickets");
        visit(request);
        AtomicInteger evaluated = new AtomicInteger();

        Map<String, Object> page = inertia.page(request, "Escalated/Agent/TicketIndex", Map.of(
                "tickets", (Supplier<Object>) () -> List.of(evaluated.incrementAndGet()),
                "departments", (Supplier<Object>) () -> List.of(evaluated.incrementAndGet())));

        assertThat(evaluated.get()).isEqualTo(1);
        assertThat(props(page)).containsOnlyKeys("tickets", "errors");
    }

    @Test
    void aPartialReloadOfAnotherPageGetsEverything() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/escalated/agent/tickets");
        request.addHeader("X-Inertia", "true");
        request.addHeader("X-Inertia-Partial-Component", "Escalated/Agent/Dashboard");
        request.addHeader("X-Inertia-Partial-Data", "stats");
        visit(request);

        Map<String, Object> page = inertia.page(request, "Escalated/Agent/TicketIndex",
                Map.of("tickets", List.of(), "filters", Map.of()));

        assertThat(props(page)).containsKeys("tickets", "filters", "errors");
    }

    @Test
    void theRouteHelperFillsParametersInOrder() {
        assertThat(routes.url("escalated.agent.tickets.show", "ESC-000001"))
                .isEqualTo("/escalated/agent/tickets/ESC-000001");
        assertThat(routes.url("escalated.agent.tickets.custom-action", "ESC-1", "refund now"))
                .isEqualTo("/escalated/agent/tickets/ESC-1/actions/refund%20now");
        assertThat(routes.script()).contains("\"escalated.agent.tickets.reply\":\"/escalated/agent/tickets/{ticket}/reply\"");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> props(Map<String, Object> page) {
        return (Map<String, Object>) page.get("props");
    }

    private static void visit(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
