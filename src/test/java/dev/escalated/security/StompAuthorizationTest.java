package dev.escalated.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.ApiTokenService;
import dev.escalated.services.ChatSessionService;
import hostapp.HostApplication;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * The STOMP endpoint with broadcasting on, over a real socket.
 *
 * <p>It used to accept any client from any origin and let it subscribe to
 * anything: {@code /topic/chat-queue} carried every new chat (visitor name,
 * email, metadata) to anonymous subscribers, and chat topics keyed by
 * sequential id could be walked, undoing the unguessable widget chat token.
 *
 * <p>Same properties as {@link dev.escalated.controllers.WidgetDisabledTest},
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
class StompAuthorizationTest {

    private static final long QUIET_MS = 1500;

    @LocalServerPort private int port;
    @Autowired private ChatSessionService chats;
    @Autowired private AgentProfileRepository agents;
    @Autowired private ApiTokenService tokens;

    private final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    private StompSession session;

    @AfterEach
    void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    @Test
    void anAnonymousClientReceivesNothingFromTheChatQueue() throws Exception {
        BlockingQueue<String> received = subscribe(connect(null), "/topic/chat-queue", null);

        chats.startForWidget("Visitor", "visitor-" + UUID.randomUUID() + "@example.com", "Hi", null, null);

        assertThat(received.poll(QUIET_MS, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void anAgentReceivesTheChatQueue() throws Exception {
        BlockingQueue<String> received = subscribe(connect(agentToken()), "/topic/chat-queue", null);
        String email = "visitor-" + UUID.randomUUID() + "@example.com";

        chats.startForWidget("Visitor", email, "Hi", null, null);

        assertThat(received.poll(10, TimeUnit.SECONDS)).contains(email);
    }

    @Test
    void aVisitorWithoutTheChatTokenReceivesNothingFromThatChat() throws Exception {
        ChatSessionService.WidgetChat chat = chats.startForWidget("Visitor", "v@example.com", "Hi", null, null);
        String topic = "/topic/chat/" + chat.session().getId() + "/typing";
        BlockingQueue<String> received = subscribe(connect(null), topic, "not-the-token");

        chats.visitorTyping(chat.session());

        assertThat(received.poll(QUIET_MS, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void aVisitorWithTheChatTokenReceivesTheirChat() throws Exception {
        ChatSessionService.WidgetChat chat = chats.startForWidget("Visitor", "v@example.com", "Hi", null, null);
        String topic = "/topic/chat/" + chat.session().getId() + "/typing";
        BlockingQueue<String> received = subscribe(connect(null), topic, chat.token());

        chats.visitorTyping(chat.session());

        assertThat(received.poll(10, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void anotherSiteCannotOpenTheSocket() throws Exception {
        HttpResponse<String> response = info("https://evil.example");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void aConfiguredWidgetSiteMayOpenTheSocket() throws Exception {
        assertThat(info("https://shop.example.com").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> info(String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/escalated/ws/info"))
                .header("Origin", origin)
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String agentToken() {
        AgentProfile agent = new AgentProfile();
        String unique = UUID.randomUUID().toString();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent = agents.save(agent);
        return (String) tokens.createToken("stomp", agent.getId(), null, null).get("plainTextToken");
    }

    private StompSession connect(String bearer) throws Exception {
        StompHeaders connect = new StompHeaders();
        if (bearer != null) {
            connect.add("Authorization", "Bearer " + bearer);
        }
        session = client.connectAsync("ws://localhost:" + port + "/escalated/ws/websocket",
                        new WebSocketHttpHeaders(), connect, new StompSessionHandlerAdapter() { })
                .get(10, TimeUnit.SECONDS);
        return session;
    }

    /** Subscribes, then waits briefly so the server has registered (or refused) it. */
    private BlockingQueue<String> subscribe(StompSession stomp, String destination, String chatToken)
            throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        if (chatToken != null) {
            headers.add(StompAuthorization.CHAT_TOKEN_HEADER, chatToken);
        }
        stomp.subscribe(headers, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders frame) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders frame, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        // No receipt support on the simple broker: give the SUBSCRIBE time to be processed.
        Thread.sleep(300);
        return received;
    }
}
