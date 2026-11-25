package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hostapp.HostApplication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code escalated.guest.routes-enabled=false} mounts no guest route: neither the guest pages nor the
 * guest ticket API, verification and lookup. The widget and the mobile guest API are not part of the
 * guest portal and stay.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
    "escalated.guest.routes-enabled=false",
})
class GuestRoutesSwitchedOffTest {

    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mvc;

    @Test
    void noGuestRouteIsRegistered() {
        List<String> patterns = PublicRouteSwitchesTest.patterns(context);
        assertThat(patterns).noneMatch(p -> p.startsWith("/escalated/guest"));
        assertThat(patterns).noneMatch(p -> p.startsWith("/escalated/api/guest"));
        // Not the guest portal: still mounted.
        assertThat(patterns).contains("/escalated/api/widget/verification", "/escalated/api/widget/tickets",
                "/escalated/api/v1/mobile/guest/verification");
    }

    @Test
    void theGuestPathsAnswerNotFound() throws Exception {
        mvc.perform(get("/escalated/guest/create").header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/escalated/api/guest/verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"g@example.com\",\"purpose\":\"lookup\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/escalated/api/guest/tickets/ESC-000001")).andExpect(status().isNotFound());
    }
}
