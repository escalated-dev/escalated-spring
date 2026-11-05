package dev.escalated.ui;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.scheduling.PluginCronScheduler;
import embedhost.EmbedHost;
import hostapp.HostApplication;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pluginfixtures.StandInPlugins;

/**
 * One host embedding the admin (ADR 2026-11-05-embeddable-admin): layout mode under
 * {@code /admin/support}, the host's security chain and {@code hostAdminOnly} middleware
 * ({@link EmbedHost}), a host layout name, and the server-rendered root view
 * {@code templates/host/admin.html} (the one {@code docs/embedding-admin.md} shows). Every embed
 * test shares this context; the gate switch, mode and frame ancestors are read per request, so the
 * tests that need them change them and put them back.
 *
 * <p>Users sign in through the session, as with a host's own login, so both the host's chain and
 * Escalated's read the same principal.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({EmbedHost.class, StandInPlugins.class})
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
    "escalated.ui.embed.enabled=true",
    "escalated.ui.embed.middleware=hostAdminOnly",
    "escalated.ui.embed.layout=Layouts/AdminLayout",
    "escalated.ui.embed.root-view=host/admin",
})
abstract class EmbedTestSupport {

    static final ObjectMapper JSON = new ObjectMapper();

    /** The wall-clock plugin scheduler stays out of these tests. */
    @MockitoBean
    PluginCronScheduler pluginCronScheduler;

    @Autowired MockMvc mvc;
    @Autowired AgentProfileRepository agents;
    @Autowired EscalatedProperties properties;

    String unique;

    @BeforeEach
    void unique() {
        unique = UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void putTheSwitchesBack() {
        EscalatedProperties.EmbedProperties embed = properties.getUi().getEmbed();
        embed.setMode("layout");
        embed.setReplaceAdminGate(false);
        embed.setFrameAncestors(null);
        properties.getAccess().setRolesPermissions(null);
    }

    AgentProfile profile(String kind, boolean agent, boolean admin) {
        AgentProfile profile = new AgentProfile();
        profile.setName(kind + " " + unique);
        profile.setEmail(kind + "-" + unique + "@example.com");
        profile.setAgent(agent);
        profile.setAdmin(admin);
        return agents.save(profile);
    }

    /** A session signed in as {@code email}, as the host's own login leaves it. */
    static MockHttpSession signedIn(String email) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                        email, null, AuthorityUtils.createAuthorityList("ROLE_USER"))));
        return session;
    }

    /** An Inertia visit from a client on the current asset version. */
    static final RequestPostProcessor INERTIA = request -> {
        request.addHeader("X-Inertia", "true");
        request.addHeader("X-Inertia-Version", "build-1");
        return request;
    };

    /** The CSRF token as Inertia's client sends it. */
    static RequestPostProcessor xsrf() {
        return request -> {
            String token = UUID.randomUUID().toString();
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    /** The page object of an Inertia visit to {@code path} as {@code who}. */
    JsonNode page(String path, AgentProfile who) throws Exception {
        return page(get(path), who);
    }

    JsonNode page(MockHttpServletRequestBuilder request, AgentProfile who) throws Exception {
        String body = mvc.perform(request.session(signedIn(who.getEmail())).with(INERTIA))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }
}
