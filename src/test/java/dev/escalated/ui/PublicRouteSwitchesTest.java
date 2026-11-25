package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.config.GuestRoutesCondition;
import hostapp.HostApplication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * With no configuration the optional public routes are mounted as they always were: the widget, the
 * guest portal, and the customer routes open to any signed-in user. {@link GuestRoutesSwitchedOffTest},
 * {@code WidgetDisabledTest} and {@link CustomerMiddlewareTest} cover each switch.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class PublicRouteSwitchesTest {

    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mvc;

    @Test
    void theWidgetAndGuestRoutesAreMountedByDefault() {
        List<String> patterns = patterns(context);
        assertThat(patterns).contains(
                "/escalated/api/widget/config", "/escalated/api/widget/tickets", "/escalated/api/widget/embed.js",
                "/escalated/api/widget/verification", "/escalated/api/widget/lookup",
                "/escalated/api/widget/chat/start",
                "/escalated/guest/create", "/escalated/api/guest/verification", "/escalated/api/guest/lookup",
                "/escalated/api/guest/tickets/{reference}");
    }

    @Test
    void anySignedInUserStillReachesTheCustomerRoutesByDefault() throws Exception {
        String someone = "someone-" + UUID.randomUUID() + "@example.com";
        mvc.perform(get("/escalated").with(user(someone))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk());
        mvc.perform(get("/escalated/api/customer/tickets").with(user(someone))).andExpect(status().isOk());
    }

    @Test
    void theGuestSwitchFallsBackToItsEnvironmentVariable() {
        assertThat(GuestRoutesCondition.enabled(new MockEnvironment())).isTrue();
        assertThat(GuestRoutesCondition.enabled(new MockEnvironment().withProperty("ESCALATED_GUEST_ROUTES", "false")))
                .isFalse();
        assertThat(GuestRoutesCondition.enabled(new MockEnvironment().withProperty("ESCALATED_GUEST_ROUTES", "0")))
                .isFalse();
        // The property wins over the variable.
        assertThat(GuestRoutesCondition.enabled(new MockEnvironment()
                .withProperty("ESCALATED_GUEST_ROUTES", "false")
                .withProperty("escalated.guest.routes-enabled", "true"))).isTrue();
        assertThat(GuestRoutesCondition.enabled(new MockEnvironment()
                .withProperty("escalated.guest.routes-enabled", "false"))).isFalse();
    }

    /** Every URL pattern the context's request mappings serve. */
    static List<String> patterns(ApplicationContext context) {
        RequestMappingHandlerMapping mapping =
                context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        return mapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .toList();
    }
}
