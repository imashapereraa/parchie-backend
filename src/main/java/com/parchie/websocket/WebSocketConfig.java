package com.parchie.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Value("${parchie.cors.allowed-origins:*}")
    private String allowedOrigins;

    private final SessionRelayHandler sessionRelayHandler;

    public WebSocketConfig(SessionRelayHandler sessionRelayHandler) {
        this.sessionRelayHandler = sessionRelayHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sessionRelayHandler, "/ws/sessions/*")
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
