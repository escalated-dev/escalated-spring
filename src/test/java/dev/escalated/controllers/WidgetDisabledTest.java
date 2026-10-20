package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import hostapp.HostApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code escalated.widget.enabled=false} switches the whole widget API off,
 * not only {@code /config}. Ticket creation, chat and the article routes used
 * to stay open to anyone on a helpdesk that had turned the widget off.
 *
 * <p>Same properties as {@link dev.escalated.security.StompAuthorizationTest},
 * so both share one context.
 */
@SpringBootTest(classes = HostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.broadcasting.enabled=true",
    "escalated.widget.enabled=false",
    "escalated.widget.allowed-origins=https://shop.example.com",
})
class WidgetDisabledTest {

    private static final String TICKET =
            "{\"name\":\"G\",\"email\":\"g@example.com\",\"subject\":\"S\",\"description\":\"D\"}";
    private static final String CHAT = "{\"name\":\"G\",\"email\":\"g@example.com\",\"message\":\"Hi\"}";

    @LocalServerPort private int port;

    @ParameterizedTest
    @CsvSource({
        "GET, /escalated/api/widget/config",
        "POST, /escalated/api/widget/tickets",
        "POST, /escalated/api/widget/chat/start",
        "GET, /escalated/api/widget/chat/availability",
        "GET, /escalated/api/widget/articles?q=reset",
        "GET, /escalated/api/widget/articles/reset",
        "GET, /escalated/api/widget/kb/search?query=reset",
        "GET, /escalated/api/widget/tickets/some-token",
        "POST, /escalated/api/widget/csat/some-token",
    })
    void everyWidgetRouteIsRefused(String method, String path) throws Exception {
        String body = path.endsWith("/chat/start") ? CHAT : path.contains("/csat/") ? "{\"rating\":5}" : TICKET;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        request = "POST".equals(method)
                ? request.POST(HttpRequest.BodyPublishers.ofString(body))
                : request.GET();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"enabled\":false");
    }
}
