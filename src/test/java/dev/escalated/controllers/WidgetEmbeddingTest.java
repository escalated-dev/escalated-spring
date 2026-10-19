package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.services.SettingsService;
import hostapp.HostApplication;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Embedding the widget on another site: CORS for listed origins, guest-only
 * requests, host metadata on tickets and chats, {@code /config} and
 * {@code embed.js}. The whole servlet filter chain runs (Spring Security
 * included), so the answers are the ones a browser on another site gets.
 *
 * <p>MockMvc requests are for {@code http://localhost}; that is the
 * install's own origin here.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.widget.allowed-origins=https://shop.example.com, https://*.brand.test",
})
class WidgetEmbeddingTest {

    private static final String WIDGET = "/escalated/api/widget";
    private static final String SHOP = "https://shop.example.com";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private SettingsService settingsService;
    @Autowired private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void forgetSetting() {
        settingsService.delete("widget_allowed_origins");
    }

    // --- Preflight ---

    @Test
    void anAllowedOriginsPreflightIsGranted() throws Exception {
        mockMvc.perform(preflight(WIDGET + "/tickets", SHOP))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Access-Control-Allow-Origin", SHOP))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET, POST, OPTIONS"))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        "Accept, Authorization, Content-Type, X-Requested-With"))
                .andExpect(header().string("Access-Control-Max-Age", "600"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andExpect(header().stringValues("Vary", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("Origin"))));
    }

    @Test
    void chatRoutesAreCoveredToo() throws Exception {
        mockMvc.perform(preflight(WIDGET + "/chat/start", "https://help.brand.test"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://help.brand.test"));
    }

    @Test
    void anUnlistedOriginsPreflightGetsNoGrant() throws Exception {
        mockMvc.perform(preflight(WIDGET + "/tickets", "https://evil.example.com"))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Methods"));
    }

    @Test
    void theWildcardDoesNotCoverTheApexOrAnotherScheme() throws Exception {
        mockMvc.perform(preflight(WIDGET + "/tickets", "https://brand.test"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(preflight(WIDGET + "/tickets", "http://help.brand.test"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(preflight(WIDGET + "/tickets", "https://a.b.brand.test"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://a.b.brand.test"));
    }

    @Test
    void theAdminSettingAddsOrigins() throws Exception {
        mockMvc.perform(preflight(WIDGET + "/tickets", "https://blog.example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        settingsService.set("widget_allowed_origins",
                "https://blog.example.com\nhttps://bad.example.com/path", "widget");

        mockMvc.perform(preflight(WIDGET + "/tickets", "https://blog.example.com"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://blog.example.com"));
        mockMvc.perform(preflight(WIDGET + "/tickets", "https://bad.example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // --- Actual requests ---

    @Test
    void anAllowedOriginCreatesATicketAndCanReadTheAnswer() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .header("Origin", SHOP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(null)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", SHOP))
                .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andReturn().getResponse();

        assertThat(response.getHeaders("Vary")).anySatisfy(vary -> assertThat(vary).contains("Origin"));
        assertThat(savedMetadata(response)).isEqualTo(Map.of("embedded_on", SHOP));
    }

    @Test
    void anUnlistedOriginIsAnsweredAsBeforeWithoutAGrant() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .header("Origin", "https://evil.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("{\"embedded_on\":\"https://shop.example.com\"}")))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andReturn().getResponse();

        assertThat(savedMetadata(response)).isNull();
    }

    @Test
    void aSameOriginRequestIsUntouched() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .header("Origin", "http://localhost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("{\"plan\":\"pro\"}")))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andReturn().getResponse();

        assertThat(savedMetadata(response)).isEqualTo(Map.of("plan", "pro"));
    }

    @Test
    void metadataIsSanitisedAndEmbeddedOnCannotBeSpoofed() throws Exception {
        String metadata = """
                {"order_id":"  A-1001  ","plan":"pro","seats":12,"ratio":1.5,"trial":false,
                 "page_url":"https://shop.example.com/checkout?step=2",
                 "embedded_on":"https://bank.example.com",
                 "bad key":"x","nested":{"a":1},"list":[1,2],"nothing":null,
                 "long":"%s"}
                """.formatted("y".repeat(600));

        MockHttpServletResponse response = mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .header("Origin", "https://help.brand.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(metadata)))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        Map<String, Object> saved = savedMetadata(response);
        assertThat(saved).containsEntry("order_id", "A-1001")
                .containsEntry("plan", "pro")
                .containsEntry("seats", 12)
                .containsEntry("ratio", 1.5)
                .containsEntry("trial", false)
                .containsEntry("page_url", "https://shop.example.com/checkout?step=2")
                .containsEntry("embedded_on", "https://help.brand.test")
                .doesNotContainKeys("bad key", "nested", "list", "nothing");
        assertThat((String) saved.get("long")).hasSize(500);
    }

    @Test
    void aPageUrlThatIsNotAWebUrlIsDropped() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("{\"page_url\":\"javascript:alert(1)\",\"plan\":\"pro\"}")))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        assertThat(savedMetadata(response)).isEqualTo(Map.of("plan", "pro"));
    }

    @Test
    void metadataThatIsNotAnObjectIsRejected() throws Exception {
        mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .header("Origin", SHOP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("[\"a\"]")))
                .andExpect(status().is(422))
                .andExpect(header().string("Access-Control-Allow-Origin", SHOP));

        mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("\"order=1\"")))
                .andExpect(status().is(422));
    }

    @Test
    void metadataWithMoreThanFiftyKeysIsRejected() throws Exception {
        StringBuilder metadata = new StringBuilder("{");
        for (int i = 0; i < 51; i++) {
            metadata.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":1");
        }
        metadata.append('}');

        mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(metadata.toString())))
                .andExpect(status().is(422));
    }

    @Test
    void onlyTheFirstTwentyFiveEntriesAreKept() throws Exception {
        StringBuilder metadata = new StringBuilder("{");
        for (int i = 0; i < 40; i++) {
            metadata.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":").append(i);
        }
        metadata.append('}');

        MockHttpServletResponse response = mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(metadata.toString())))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        Map<String, Object> saved = savedMetadata(response);
        assertThat(saved).hasSize(25).containsKeys("k0", "k24").doesNotContainKey("k25");
    }

    @Test
    void chatMetadataIsKeptOnTheChatsTicket() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post(WIDGET + "/chat/start")
                        .header("Origin", SHOP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Visitor","email":"visitor@example.com","message":"Hi",
                                 "metadata":{"cart_total":59.9,"embedded_on":"https://x.test"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", SHOP))
                .andReturn().getResponse();

        long ticketId = json.readTree(response.getContentAsString()).get("ticketId").asLong();
        assertThat(storedMetadata("id = ?", ticketId))
                .isEqualTo(Map.of("cart_total", 59.9, "embedded_on", SHOP));
    }

    // --- Errors carry the grant too ---

    @Test
    void aForbiddenAnswerCarriesTheGrant() throws Exception {
        mockMvc.perform(get(WIDGET + "/tickets/not-a-token").header("Origin", SHOP))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Access-Control-Allow-Origin", SHOP));
    }

    @Test
    void anUnauthorizedAnswerFromTheSecurityChainCarriesTheGrant() throws Exception {
        mockMvc.perform(get(WIDGET + "/tickets/not-a-token")
                        .header("Origin", SHOP)
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", SHOP));
    }

    @Test
    void aRateLimitedAnswerCarriesTheGrantAndExposesRetryAfter() throws Exception {
        String ip = nextIp();
        int last = 0;
        MockHttpServletResponse response = null;
        for (int i = 0; i < 10 && last != 429; i++) {
            response = mockMvc.perform(post(WIDGET + "/tickets")
                            .with(request -> {
                                request.setRemoteAddr(ip);
                                return request;
                            })
                            .header("Origin", SHOP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ticketBody(null)))
                    .andReturn().getResponse();
            last = response.getStatus();
        }

        assertThat(last).isEqualTo(429);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(SHOP);
        assertThat(response.getHeader("Access-Control-Expose-Headers")).isEqualTo("Retry-After");
        assertThat(response.getHeader("Retry-After")).isNotBlank();
    }

    // --- /config ---

    @Test
    void configSaysTicketsAcceptMetadata() throws Exception {
        mockMvc.perform(get(WIDGET + "/config").header("Origin", SHOP))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", SHOP))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.color").value("#4F46E5"))
                .andExpect(jsonPath("$.position").value("bottom-right"))
                .andExpect(jsonPath("$.greeting").isString())
                .andExpect(jsonPath("$.departments").isArray())
                .andExpect(jsonPath("$.kb_enabled").isBoolean())
                .andExpect(jsonPath("$.guest_tickets_enabled").value(true))
                .andExpect(jsonPath("$.accepts_metadata").value(true))
                .andExpect(jsonPath("$.guest_verification_required").doesNotExist());
    }

    // --- embed.js ---

    @Test
    void embedJsIsServedToEverySite() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get(WIDGET + "/embed.js")
                        .header("Origin", "https://anywhere.example.org"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/javascript; charset=utf-8"))
                .andExpect(header().string("Cache-Control", "public, max-age=3600"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "cross-origin"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andReturn().getResponse();

        String etag = response.getHeader("ETag");
        assertThat(etag).startsWith("\"").endsWith("\"");
        assertThat(response.getContentAsString()).contains("EscalatedWidget");

        mockMvc.perform(get(WIDGET + "/embed.js").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag));
    }

    // --- helpers ---

    private static MockHttpServletRequestBuilder preflight(String path, String origin) {
        return options(path)
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type");
    }

    /** Each ticket-creating request comes from its own address, clear of the rate limit. */
    private static MockHttpServletRequestBuilder guest(MockHttpServletRequestBuilder builder) {
        String ip = nextIp();
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private static String nextIp() {
        int n = NEXT_IP.getAndIncrement();
        return "198.51." + (n / 250) + "." + (n % 250 + 1);
    }

    private static String ticketBody(String metadata) {
        return "{\"subject\":\"Order help\",\"body\":\"Where is it?\",\"name\":\"Guest\","
                + "\"email\":\"guest@example.com\""
                + (metadata == null ? "" : ",\"metadata\":" + metadata) + "}";
    }

    /** The metadata stored for the ticket a response created, read back from the table. */
    private Map<String, Object> savedMetadata(MockHttpServletResponse response) throws Exception {
        JsonNode body = json.readTree(response.getContentAsString());
        return storedMetadata("guest_access_token = ?", body.get("guestAccessToken").asText());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> storedMetadata(String where, Object argument) throws Exception {
        String stored = jdbc.queryForObject(
                "SELECT metadata FROM escalated_tickets WHERE " + where, String.class, argument);
        return stored == null ? null : json.readValue(stored, Map.class);
    }
}
