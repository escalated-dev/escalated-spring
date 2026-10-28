package dev.escalated.security;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.ApiToken;
import dev.escalated.repositories.ChatSessionRepository;
import dev.escalated.services.ApiTokenService;
import dev.escalated.services.WidgetChatTokens;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Who may do what on the STOMP endpoint ({@code /escalated/ws}).
 *
 * <p>The endpoint used to accept any client and let it subscribe to anything,
 * so an anonymous visitor could read {@code /topic/chat-queue} (every new chat:
 * visitor name, email, metadata) and walk {@code /topic/chat/{n}/messages} by
 * sequential id. Now:
 *
 * <ul>
 *   <li><b>CONNECT</b> takes the principal from the HTTP handshake (the host's
 *       session login), or from an Escalated API token in a STOMP
 *       {@code Authorization: Bearer ...} header. A bad token is refused.
 *       Connecting with neither is allowed, for widget visitors.</li>
 *   <li><b>SUBSCRIBE</b>: staff (the agent gate) may subscribe to anything.
 *       Anyone else only to their own chat's topics,
 *       {@code /topic/chat/{id}}, {@code .../messages} and {@code .../typing},
 *       and only by presenting that chat's widget token in a
 *       {@code chat-token} header.</li>
 *   <li><b>SEND</b> straight to a broker destination ({@code /topic},
 *       {@code /queue}) is refused for everyone: it would let a client forge
 *       chat messages to other subscribers. {@code /app} messages need a
 *       signed-in principal.</li>
 * </ul>
 */
public class StompAuthorization implements ChannelInterceptor {

    /** STOMP header a widget visitor sends the chat token in. */
    public static final String CHAT_TOKEN_HEADER = "chat-token";

    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chat/(\\d+)(/messages|/typing)?$");

    private final ObjectProvider<EscalatedAuthorization> authorization;
    private final ObjectProvider<ApiTokenService> tokens;
    private final ObjectProvider<ChatSessionRepository> chats;

    public StompAuthorization(ObjectProvider<EscalatedAuthorization> authorization,
                              ObjectProvider<ApiTokenService> tokens,
                              ObjectProvider<ChatSessionRepository> chats) {
        this.authorization = authorization;
        this.tokens = tokens;
        this.chats = chats;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            Authentication fromToken = bearer(accessor.getFirstNativeHeader("Authorization"));
            if (fromToken != null) {
                accessor.setUser(fromToken);
            }
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscribe(accessor);
        } else if (command == StompCommand.SEND) {
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/app/") || authentication(accessor.getUser()) == null) {
                throw denied();
            }
        }
        return message;
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw denied();
        }
        if (authorization.getObject().isAgent(authentication(accessor.getUser()))) {
            return;
        }
        Matcher chat = CHAT_TOPIC.matcher(destination);
        String token = accessor.getFirstNativeHeader(CHAT_TOKEN_HEADER);
        if (!chat.matches() || token == null || token.isBlank()) {
            throw denied();
        }
        byte[] presented = WidgetChatTokens.hash(token).getBytes(StandardCharsets.UTF_8);
        boolean owns = chats.getObject().findById(Long.valueOf(chat.group(1)))
                .map(session -> session.getWidgetTokenHash() != null
                        // An expired chat token opens nothing (escalated-laravel's expiring chat grant).
                        && (session.getWidgetTokenExpiresAt() == null
                            || session.getWidgetTokenExpiresAt().isAfter(java.time.Instant.now()))
                        && MessageDigest.isEqual(session.getWidgetTokenHash().getBytes(StandardCharsets.UTF_8), presented))
                .orElse(false);
        if (!owns) {
            throw denied();
        }
    }

    /** The principal of an Escalated API token, or null when no bearer header was sent. */
    private Authentication bearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        AgentProfile agent;
        String abilities = null;
        try {
            ApiToken token = tokens.getObject().validateToken(header.substring(7));
            agent = token.getAgent();
            abilities = token.getAbilities();
        } catch (RuntimeException ex) {
            agent = null;
        }
        if (agent == null || !agent.isActive()) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                agent.getEmail(), null, EscalatedAuthorization.authoritiesOf(agent, abilities));
    }

    private static Authentication authentication(Principal principal) {
        if (principal == null) {
            return null;
        }
        if (principal instanceof Authentication authentication) {
            return authentication;
        }
        return UsernamePasswordAuthenticationToken.authenticated(principal.getName(), null, List.of());
    }

    private static MessageDeliveryException denied() {
        return new MessageDeliveryException("Access denied");
    }
}
