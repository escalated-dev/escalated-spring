package dev.escalated.security;

import static dev.escalated.security.AccessTestSupport.staff;
import static dev.escalated.security.AccessTestSupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Role;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.RoleRepository;
import dev.escalated.services.PermissionSeeder;
import dev.escalated.services.SettingsService;
import dev.escalated.services.SsoService;
import hostapp.HostApplication;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Single sign-on switched on (ADR 2026-10-29-configurable-access-features, port test checklist),
 * through the real security chains and router, with a test SAML IdP, a test OAuth provider and
 * HS256 JWTs. The switch is turned on at runtime, in the context the other UI tests share.
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
class SsoEnforcementTest {

    private static final String BASE = "http://localhost";
    private static final String ACS = BASE + "/escalated/sso/saml/acs";
    private static final String SP = BASE + "/escalated/sso/saml/metadata";
    private static final String JWT_SECRET = "a-test-jwt-secret-that-is-long-enough-for-hs256!";

    @Autowired private MockMvc mvc;
    @Autowired private EscalatedProperties properties;
    @Autowired private SettingsService settings;
    @Autowired private AgentProfileRepository agents;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionSeeder seeder;
    @Autowired private ObjectProvider<UserDetailsService> hostUsers;

    private AgentProfile agent;

    @BeforeEach
    void switchOn() {
        properties.getAccess().setSso(true);
        agent = staff(agents, "sso", false, null);
        hostUser(agent.getEmail());
    }

    @AfterEach
    void switchOff() {
        properties.getAccess().setSso(null);
        properties.getAccess().setRolesPermissions(null);
        properties.getSso().setCreateUsers(null);
        properties.getSso().setSyncRoles(null);
        properties.getSso().setAllowIdpInitiated(null);
        SsoService.DEFAULTS.forEach((key, value) -> settings.set(key, value, "sso"));
    }

    private void hostUser(String email) {
        if (hostUsers.getIfUnique() instanceof UserDetailsManager manager && !manager.userExists(email)) {
            manager.createUser(User.withUsername(email).password("{noop}" + UUID.randomUUID()).roles("USER").build());
        }
    }

    private void provider(String provider, Map<String, String> values) {
        settings.set("sso_provider", provider, "sso");
        values.forEach((key, value) -> settings.set(key, value, "sso"));
    }

    /** Whether {@code session} is signed in as a staff user: the agent dashboard answers. */
    private boolean signedIn(MockHttpSession session) throws Exception {
        return mvc.perform(get("/escalated/agent").session(session)).andReturn().getResponse().getStatus() == 200;
    }

    // ── SAML ────────────────────────────────────────────────────────────

    private void saml() throws Exception {
        provider("saml", Map.of("sso_entity_id", SamlIdp.ISSUER, "sso_login_url", "https://idp.example.com/sso",
                "sso_certificate", SamlIdp.certificate("idp")));
    }

    private SamlIdp.Response pending() throws Exception {
        MvcResult login = mvc.perform(get("/escalated/sso/login")).andExpect(status().isFound()).andReturn();
        String location = login.getResponse().getHeader("Location");
        assertThat(location).startsWith("https://idp.example.com/sso?SAMLRequest=");
        SamlIdp.Response response = new SamlIdp.Response();
        response.inResponseTo = SamlIdp.requestId(location);
        response.audience = SP;
        response.destination = ACS;
        response.recipient = ACS;
        response.email = agent.getEmail();
        return response;
    }

    private MvcResult acs(MockHttpSession session, String encoded) throws Exception {
        return mvc.perform(post("/escalated/sso/saml/acs").session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("SAMLResponse", encoded)).andReturn();
    }

    @Test
    void aSignedSamlResponseForThePendingRequestSignsTheUserIn() throws Exception {
        saml();
        MockHttpSession session = new MockHttpSession();
        MvcResult result = acs(session, pending().encoded());
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/escalated/agent");
        assertThat(signedIn(session)).isTrue();

        String metadata = mvc.perform(get("/escalated/sso/saml/metadata")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(metadata).contains("entityID=\"" + SP + "\"").contains("Location=\"" + ACS + "\"");
    }

    private void refusedSaml(Consumer<SamlIdp.Response> change) throws Exception {
        SamlIdp.Response response = pending();
        change.accept(response);
        MockHttpSession session = new MockHttpSession();
        assertThat(acs(session, response.encoded()).getResponse().getStatus()).isEqualTo(403);
        assertThat(signedIn(session)).isFalse();
    }

    @Test
    void samlRefusals() throws Exception {
        saml();
        refusedSaml(r -> r.sign = false);
        refusedSaml(r -> r.signWith = "other");
        refusedSaml(r -> r.afterSigning = xml -> xml.replace("SSO User", "Someone Else"));
        refusedSaml(r -> r.issuer = "https://evil.example.com");
        refusedSaml(r -> r.audience = "https://other-sp.example.com");
        refusedSaml(r -> r.destination = "https://other-sp.example.com/acs");
        refusedSaml(r -> r.recipient = "https://other-sp.example.com/acs");
        refusedSaml(r -> {
            r.notBefore = Instant.now().minusSeconds(3600);
            r.notOnOrAfter = Instant.now().minusSeconds(1800);
        });
        refusedSaml(r -> r.inResponseTo = "_unknown");
        refusedSaml(r -> r.inResponseTo = null);

        // The same assertion a second time.
        SamlIdp.Response once = pending();
        String encoded = once.encoded();
        assertThat(acs(new MockHttpSession(), encoded).getResponse().getStatus()).isEqualTo(302);
        assertThat(acs(new MockHttpSession(), encoded).getResponse().getStatus()).isEqualTo(403);

        // Unsolicited responses are accepted only when allowed, and even then each assertion once.
        properties.getSso().setAllowIdpInitiated(true);
        SamlIdp.Response unsolicited = pending();
        unsolicited.inResponseTo = null;
        String replayed = unsolicited.encoded();
        assertThat(acs(new MockHttpSession(), replayed).getResponse().getStatus()).isEqualTo(302);
        assertThat(acs(new MockHttpSession(), replayed).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void withoutACertificateEverySamlResponseIsRefused() throws Exception {
        saml();
        SamlIdp.Response response = pending();
        settings.set("sso_certificate", "", "sso");
        assertThat(acs(new MockHttpSession(), response.encoded()).getResponse().getStatus()).isEqualTo(403);
    }

    // ── OAuth ───────────────────────────────────────────────────────────

    @Test
    void oauthSignsInWithAMatchingStateAndSendsThePkceVerifier() throws Exception {
        AtomicReference<String> sentVerifier = new AtomicReference<>();
        HttpServer idp = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        idp.createContext("/token", exchange -> {
            Map<String, String> form = new HashMap<>();
            for (String pair : new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&")) {
                String[] kv = pair.split("=", 2);
                form.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
            sentVerifier.set(form.get("code_verifier"));
            byte[] body = "good-code".equals(form.get("code"))
                    ? "{\"access_token\":\"at-1\",\"token_type\":\"Bearer\"}".getBytes(StandardCharsets.UTF_8)
                    : "{\"error\":\"invalid_grant\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders("good-code".equals(form.get("code")) ? 200 : 400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        idp.createContext("/userinfo", exchange -> {
            boolean ok = "Bearer at-1".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("{\"sub\":\"1\",\"email\":\"" + agent.getEmail() + "\",\"name\":\"SSO\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(ok ? 200 : 401, ok ? body.length : -1);
            if (ok) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        idp.start();
        try {
            String root = "http://127.0.0.1:" + idp.getAddress().getPort();
            provider("oauth", Map.of("sso_oauth_authorize_url", root + "/authorize", "sso_oauth_token_url", root + "/token",
                    "sso_oauth_userinfo_url", root + "/userinfo", "sso_oauth_client_id", "escalated",
                    "sso_oauth_client_secret", "shh"));

            MockHttpSession session = new MockHttpSession();
            String location = mvc.perform(get("/escalated/sso/login").session(session)).andExpect(status().isFound())
                    .andReturn().getResponse().getHeader("Location");
            assertThat(location).startsWith(root + "/authorize?");
            String state = query(location, "state");
            String challenge = query(location, "code_challenge");
            assertThat(query(location, "code_challenge_method")).isEqualTo("S256");

            // A mismatched state is refused, and the state is gone after any use.
            mvc.perform(get("/escalated/sso/oauth/callback").session(session).param("state", "forged")
                    .param("code", "good-code")).andExpect(status().isForbidden());
            mvc.perform(get("/escalated/sso/oauth/callback").session(session).param("state", state)
                    .param("code", "good-code")).andExpect(status().isForbidden());
            assertThat(signedIn(session)).isFalse();

            MockHttpSession fresh = new MockHttpSession();
            location = mvc.perform(get("/escalated/sso/login").session(fresh)).andReturn().getResponse().getHeader("Location");
            state = query(location, "state");
            challenge = query(location, "code_challenge");
            // A missing state is refused too.
            mvc.perform(get("/escalated/sso/oauth/callback").session(new MockHttpSession()).param("state", state)
                    .param("code", "good-code")).andExpect(status().isForbidden());
            mvc.perform(get("/escalated/sso/oauth/callback").session(fresh).param("state", state)
                    .param("code", "good-code")).andExpect(status().isFound());
            assertThat(signedIn(fresh)).isTrue();
            String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(sentVerifier.get().getBytes(StandardCharsets.US_ASCII)));
            assertThat(expected).isEqualTo(challenge);

            // An error from the provider is refused.
            MockHttpSession denied = new MockHttpSession();
            location = mvc.perform(get("/escalated/sso/login").session(denied)).andReturn().getResponse().getHeader("Location");
            mvc.perform(get("/escalated/sso/oauth/callback").session(denied).param("state", query(location, "state"))
                    .param("error", "access_denied")).andExpect(status().isForbidden());
        } finally {
            idp.stop(0);
        }
    }

    private static String query(String url, String name) {
        Matcher matcher = Pattern.compile("[?&]" + name + "=([^&]+)").matcher(url);
        assertThat(matcher.find()).as(name).isTrue();
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    // ── JWT ─────────────────────────────────────────────────────────────

    private void jwtProvider() {
        provider("jwt", Map.of("sso_url", "https://idp.example.com/jwt", "sso_jwt_secret", JWT_SECRET,
                "sso_jwt_algorithm", "HS256"));
    }

    private static JWTClaimsSet.Builder claims(String email) {
        return new JWTClaimsSet.Builder().claim("email", email).claim("name", "SSO").audience(BASE)
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).issueTime(new Date())
                .jwtID(UUID.randomUUID().toString());
    }

    private static String sign(JWTClaimsSet claims, JWSAlgorithm algorithm, String secret) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private int jwt(MockHttpSession session, String token) throws Exception {
        return mvc.perform(post("/escalated/sso/jwt/callback").session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("jwt", token)).andReturn().getResponse().getStatus();
    }

    @Test
    void aValidJwtSignsTheUserInAndEveryFlawIsRefused() throws Exception {
        jwtProvider();
        MockHttpSession session = new MockHttpSession();
        String valid = sign(claims(agent.getEmail()).build(), JWSAlgorithm.HS256, JWT_SECRET);
        assertThat(jwt(session, valid)).isEqualTo(302);
        assertThat(signedIn(session)).isTrue();
        // GET works too, and the login route points at the IdP with the callback.
        assertThat(mvc.perform(get("/escalated/sso/jwt/callback").param("jwt",
                sign(claims(agent.getEmail()).build(), JWSAlgorithm.HS256, JWT_SECRET))).andReturn().getResponse()
                .getStatus()).isEqualTo(302);
        assertThat(mvc.perform(get("/escalated/sso/login")).andReturn().getResponse().getHeader("Location"))
                .startsWith("https://idp.example.com/jwt?return_to=");

        String email = agent.getEmail();
        String[] refused = {
            sign(claims(email).build(), JWSAlgorithm.HS256, JWT_SECRET + "-wrong"),
            new PlainJWT(claims(email).build()).serialize(),
            sign(claims(email).build(), JWSAlgorithm.HS384, JWT_SECRET),
            sign(claims(email).expirationTime(null).build(), JWSAlgorithm.HS256, JWT_SECRET),
            sign(claims(email).expirationTime(Date.from(Instant.now().minusSeconds(3600))).build(), JWSAlgorithm.HS256,
                    JWT_SECRET),
            sign(claims(email).audience("https://other.example.com").build(), JWSAlgorithm.HS256, JWT_SECRET),
            sign(claims(email).jwtID(null).build(), JWSAlgorithm.HS256, JWT_SECRET),
            valid,
        };
        for (String token : refused) {
            MockHttpSession other = new MockHttpSession();
            assertThat(jwt(other, token)).as(token).isEqualTo(403);
            assertThat(signedIn(other)).isFalse();
        }
    }

    // ── Users and roles ─────────────────────────────────────────────────

    @Test
    void anUnknownEmailIsRefusedUnlessCreateUsersIsOn() throws Exception {
        assumeTrue(hostUsers.getIfUnique() instanceof UserDetailsManager, "the host has a UserDetailsManager");
        jwtProvider();
        String stranger = "stranger-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        assertThat(jwt(new MockHttpSession(), sign(claims(stranger).build(), JWSAlgorithm.HS256, JWT_SECRET))).isEqualTo(403);
        UserDetailsManager manager = (UserDetailsManager) hostUsers.getIfUnique();
        assertThat(manager.userExists(stranger)).isFalse();

        properties.getSso().setCreateUsers(true);
        assertThat(jwt(new MockHttpSession(), sign(claims(stranger).build(), JWSAlgorithm.HS256, JWT_SECRET))).isEqualTo(302);
        assertThat(manager.userExists(stranger)).isTrue();
    }

    @Test
    void theRoleIsMappedOnlyWithRolesOnAndSyncRolesOn() throws Exception {
        seeder.seed();
        Role light = roles.findByName("light_agent").orElseThrow();
        jwtProvider();
        java.util.function.Supplier<String> token = () -> {
            try {
                return sign(claims(agent.getEmail()).claim("role", "light_agent").build(), JWSAlgorithm.HS256, JWT_SECRET);
            } catch (Exception impossible) {
                throw new IllegalStateException(impossible);
            }
        };
        properties.getSso().setSyncRoles(true);
        jwt(new MockHttpSession(), token.get());
        assertThat(agents.findById(agent.getId()).orElseThrow().getRole()).isNull();

        properties.getAccess().setRolesPermissions(true);
        properties.getSso().setSyncRoles(false);
        jwt(new MockHttpSession(), token.get());
        assertThat(agents.findById(agent.getId()).orElseThrow().getRole()).isNull();

        properties.getSso().setSyncRoles(true);
        jwt(new MockHttpSession(), sign(claims(agent.getEmail()).claim("role", "no-such-role").build(),
                JWSAlgorithm.HS256, JWT_SECRET));
        assertThat(agents.findById(agent.getId()).orElseThrow().getRole()).isNull();
        jwt(new MockHttpSession(), token.get());
        assertThat(agents.findById(agent.getId()).orElseThrow().getRole().getId()).isEqualTo(light.getId());
    }

    // ── Switch, provider and settings ───────────────────────────────────

    @Test
    void providerNoneMakesLogin404AndOtherProvidersRoutes404() throws Exception {
        provider("none", Map.of());
        mvc.perform(get("/escalated/sso/login")).andExpect(status().isNotFound());
        jwtProvider();
        mvc.perform(get("/escalated/sso/saml/metadata")).andExpect(status().isNotFound());
        mvc.perform(post("/escalated/sso/saml/acs").param("SAMLResponse", "x")).andExpect(status().isNotFound());
        mvc.perform(get("/escalated/sso/oauth/callback")).andExpect(status().isNotFound());
    }

    @Test
    void theSettingsPageMasksSecretsListsEndpointsAndKeepsAMaskedSecret() throws Exception {
        AgentProfile admin = staff(agents, "admin", true, null);
        jwtProvider();
        MockHttpSession session = new MockHttpSession();
        String body = mvc.perform(get("/escalated/admin/settings/sso").session(session).with(user(admin.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode page = AccessTestSupport.JSON.readTree(body);
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Settings/SsoSettings");
        assertThat(page.get("props").get("settings").get("sso_jwt_secret").asText()).isEqualTo("********");
        assertThat(body).doesNotContain(JWT_SECRET);
        JsonNode endpoints = page.get("props").get("endpoints");
        assertThat(endpoints.get("acs").asText()).isEqualTo(ACS);
        assertThat(endpoints.get("sp_entity_id").asText()).isEqualTo(SP);
        assertThat(endpoints.get("jwt_callback").asText()).isEqualTo(BASE + "/escalated/sso/jwt/callback");
        assertThat(page.get("props").get("escalated").get("features").get("sso").asBoolean()).isTrue();

        mvc.perform(post("/escalated/admin/settings/sso").session(session).with(user(admin.getEmail())).with(xsrf())
                        .header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sso_provider\":\"jwt\",\"sso_url\":\"https://idp.example.com/jwt2\","
                                + "\"sso_jwt_secret\":\"********\",\"sso_jwt_algorithm\":\"HS256\"}"))
                .andExpect(status().isSeeOther());
        assertThat(settings.get("sso_jwt_secret")).contains(JWT_SECRET);
        assertThat(settings.get("sso_url")).contains("https://idp.example.com/jwt2");
    }

    @Test
    void withTheSwitchOffEveryRouteAnswers404() throws Exception {
        jwtProvider();
        properties.getAccess().setSso(null);
        settings.set("sso", "1", "general");
        AgentProfile admin = staff(agents, "admin", true, null);
        mvc.perform(get("/escalated/sso/login")).andExpect(status().isNotFound());
        mvc.perform(get("/escalated/sso/saml/metadata")).andExpect(status().isNotFound());
        mvc.perform(post("/escalated/sso/saml/acs").param("SAMLResponse", "x")).andExpect(status().isNotFound());
        mvc.perform(get("/escalated/sso/oauth/callback")).andExpect(status().isNotFound());
        mvc.perform(post("/escalated/sso/jwt/callback").param("jwt",
                sign(claims(agent.getEmail()).build(), JWSAlgorithm.HS256, JWT_SECRET))).andExpect(status().isNotFound());
        mvc.perform(get("/escalated/admin/settings/sso").with(user(admin.getEmail()))).andExpect(status().isNotFound());
        assertThat(AccessTestSupport.shared(mvc, "/escalated/agent", agent).get("features").get("sso").asBoolean())
                .isFalse();
    }
}
