package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.config.EscalatedProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InertiaFilterTest {

    private final EscalatedProperties properties = new EscalatedProperties();

    @Test
    void aRedirectAfterAnInertiaPutPatchOrDeleteIsASeeOther() throws Exception {
        for (String method : new String[] {"PUT", "PATCH", "DELETE"}) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/escalated/admin/tags/1");
            request.addHeader("X-Inertia", "true");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new InertiaFilter(properties).doFilter(request, response, new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
                @Override
                protected void service(jakarta.servlet.http.HttpServletRequest req,
                                       jakarta.servlet.http.HttpServletResponse res) throws java.io.IOException {
                    res.sendRedirect("/escalated/admin/tags");
                }
            }));

            assertThat(response.getStatus()).as(method).isEqualTo(303);
            assertThat(response.getHeader("Location")).isEqualTo("/escalated/admin/tags");
        }
    }

    @Test
    void aRequestWithoutInertiaIsLeftAlone() throws Exception {
        properties.getUi().setVersion("v2");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/escalated/agent");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new InertiaFilter(properties).doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void aBlankVersionTurnsTheCheckOff() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/escalated/agent");
        request.addHeader("X-Inertia", "true");
        request.addHeader("X-Inertia-Version", "anything");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new InertiaFilter(properties).doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
