package dev.escalated.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.services.SettingsService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * An allowed cross-site request never acts as whoever is signed in to the
 * host in the same browser.
 */
class WidgetCorsFilterTest {

    private static final String SHOP = "https://shop.example.com";

    private final AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
    private final AtomicReference<Authentication> seenAuthentication = new AtomicReference<>();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anAllowedCrossSiteRequestIsAGuest() throws Exception {
        MockHttpServletRequest request = signedInRequest(SHOP);
        Authentication host = UsernamePasswordAuthenticationToken.authenticated("agent@example.com", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(host);

        filter().doFilter(request, new MockHttpServletResponse(), recordingChain());

        HttpServletRequest downstream = seen.get();
        assertThat(seenAuthentication.get()).isNull();
        assertThat(downstream.getUserPrincipal()).isNull();
        assertThat(downstream.getRemoteUser()).isNull();
        assertThat(downstream.isUserInRole("ADMIN")).isFalse();
        assertThat(downstream.getCookies()).isNull();
        assertThat(downstream.getHeader("Cookie")).isNull();
        assertThat(Collections.list(downstream.getHeaderNames())).doesNotContain("Cookie");
        assertThat(downstream.getSession(false)).isNull();
        assertThat(downstream.getRequestedSessionId()).isNull();
        assertThatThrownBy(downstream::getSession).isInstanceOf(IllegalStateException.class);
        // A bearer guest token still reaches the route.
        assertThat(downstream.getHeader("Authorization")).isEqualTo("Bearer guest-token");
        assertThat(downstream.getAttribute(WidgetOrigins.ATTRIBUTE)).isEqualTo(SHOP);
        // The host's own context is back once the request has been handled.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(host);
    }

    @Test
    void sameOriginAndUnlistedRequestsKeepTheSignedInUser() throws Exception {
        for (String origin : new String[] {"http://localhost", "https://evil.example.com", null}) {
            MockHttpServletRequest request = signedInRequest(origin);

            filter().doFilter(request, new MockHttpServletResponse(), recordingChain());

            assertThat(seen.get().getUserPrincipal()).isNotNull();
            assertThat(seen.get().getSession(false)).isNotNull();
            assertThat(seen.get().getCookies()).isNotEmpty();
            assertThat(seen.get().getAttribute(WidgetOrigins.ATTRIBUTE)).isNull();
        }
    }

    @Test
    void routesOutsideTheWidgetAreLeftAlone() throws Exception {
        MockHttpServletRequest request = signedInRequest(SHOP);
        request.setRequestURI("/escalated/api/agent/tickets");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, recordingChain());

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(seen.get().getUserPrincipal()).isNotNull();
    }

    @Test
    void varyKeepsWhatIsAlreadyThere() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.addHeader("Vary", "Accept-Encoding");

        filter().doFilter(signedInRequest(SHOP), response, recordingChain());

        assertThat(response.getHeader("Vary")).isEqualTo("Accept-Encoding, Origin");
    }

    private MockFilterChain recordingChain() {
        return new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
                seen.set(req);
                seenAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            }
        });
    }

    private static MockHttpServletRequest signedInRequest(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/escalated/api/widget/tickets");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        request.addHeader("Authorization", "Bearer guest-token");
        request.setCookies(new Cookie("JSESSIONID", "abc"));
        request.setSession(new MockHttpSession());
        request.setRequestedSessionId("abc");
        request.setUserPrincipal(() -> "agent@example.com");
        request.setRemoteUser("agent@example.com");
        return request;
    }

    private static WidgetCorsFilter filter() {
        EscalatedProperties properties = new EscalatedProperties();
        properties.getWidget().setAllowedOrigins(List.of(SHOP));
        SettingsService settings = mock(SettingsService.class);
        when(settings.getOrDefault(WidgetOrigins.SETTING, "")).thenReturn("");
        return new WidgetCorsFilter(new WidgetOrigins(properties, settings));
    }
}
