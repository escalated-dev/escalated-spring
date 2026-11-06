package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.AgentProfile;
import dev.escalated.services.TwoFactorService;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/**
 * Two-factor still guards the embedded admin, and the challenge (which does not move) sends the
 * user back to the embedded page they asked for.
 */
class EmbeddedAdminTwoFactorTest extends EmbedTestSupport {

    private static final Pattern SECRET = Pattern.compile("secret=([A-Z2-7]+)");

    @Autowired private TwoFactorService twoFactor;

    @BeforeEach
    void switchOn() {
        properties.getAccess().setTwoFactor(true);
        properties.getTwoFactor().setEncryptionKey("test-two-factor-key");
    }

    @AfterEach
    void switchOff() {
        properties.getAccess().setTwoFactor(null);
        properties.getTwoFactor().setEncryptionKey(null);
    }

    @Test
    void theChallengeRunsAndComesBackToTheEmbeddedPage() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        TwoFactorService.Setup setup = twoFactor.setup(admin);
        Matcher secret = SECRET.matcher(setup.qrUri());
        assertThat(secret.find()).isTrue();
        assertThat(twoFactor.confirm(admin.getId(), twoFactor.codeAt(secret.group(1), Instant.now()))).isPresent();

        MockHttpSession session = signedIn(admin.getEmail());
        mvc.perform(get("/admin/support/tickets").queryParam("status", "open").session(session))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/escalated/two-factor/challenge"));

        String code = twoFactor.codeAt(secret.group(1), Instant.now().plusSeconds(30));
        mvc.perform(post("/escalated/two-factor/challenge").session(session).with(xsrf()).with(INERTIA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/admin/support/tickets?status=open"));
        mvc.perform(get("/admin/support/tickets").session(session).with(INERTIA)).andExpect(status().isOk());
    }
}
