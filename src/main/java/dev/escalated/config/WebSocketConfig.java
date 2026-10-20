package dev.escalated.config;

import dev.escalated.repositories.ChatSessionRepository;
import dev.escalated.security.EscalatedAuthorization;
import dev.escalated.security.StompAuthorization;
import dev.escalated.services.ApiTokenService;
import dev.escalated.widget.WidgetOrigins;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.WebUtils;

/**
 * The STOMP endpoint behind {@code escalated.broadcasting.enabled}.
 *
 * <p>Origins: the helpdesk's own origin, plus the sites listed in
 * {@code escalated.widget.allowed-origins}. It used to be
 * {@code setAllowedOriginPatterns("*")}, which let any site open a socket
 * with the visitor's cookies. The handshake is also checked against the full
 * widget allow-list ({@link WidgetOrigins}) as a second gate. Origins added
 * only through the {@code widget_allowed_origins} admin setting are not
 * admitted to the socket; list them in configuration to use real-time chat
 * from those sites (the shared widget itself polls and needs no socket).
 *
 * <p>Who may subscribe or send is {@link StompAuthorization}'s call.
 */
@Configuration
@ConditionalOnProperty(prefix = "escalated.broadcasting", name = "enabled", havingValue = "true")
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final EscalatedProperties properties;
    private final ObjectProvider<WidgetOrigins> origins;
    private final ObjectProvider<EscalatedAuthorization> authorization;
    private final ObjectProvider<ApiTokenService> tokens;
    private final ObjectProvider<ChatSessionRepository> chats;

    public WebSocketConfig(EscalatedProperties properties,
                           ObjectProvider<WidgetOrigins> origins,
                           ObjectProvider<EscalatedAuthorization> authorization,
                           ObjectProvider<ApiTokenService> tokens,
                           ObjectProvider<ChatSessionRepository> chats) {
        this.properties = properties;
        this.origins = origins;
        this.authorization = authorization;
        this.tokens = tokens;
        this.chats = chats;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompAuthorization(authorization, tokens, chats));
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] configured = properties.getWidget().getAllowedOrigins().stream()
                .map(WidgetOrigins::normalizePattern)
                .filter(Objects::nonNull)
                .toArray(String[]::new);
        registry.addEndpoint("/escalated/ws")
                .setAllowedOriginPatterns(configured)
                .addInterceptors(new OriginGuard(origins))
                .withSockJS();
    }

    /** Same-origin, no Origin at all (not a browser), or an allowed widget origin. */
    static final class OriginGuard implements HandshakeInterceptor {

        private final ObjectProvider<WidgetOrigins> origins;

        OriginGuard(ObjectProvider<WidgetOrigins> origins) {
            this.origins = origins;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler handler, Map<String, Object> attributes) {
            String origin = request.getHeaders().getOrigin();
            if (origin == null || WebUtils.isSameOrigin(request)) {
                return true;
            }
            WidgetOrigins allowed = origins.getIfAvailable();
            if (allowed != null && allowed.allows(origin)) {
                return true;
            }
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Exception exception) {
        }
    }
}
