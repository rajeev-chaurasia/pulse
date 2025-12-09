package com.pulse.ingestion.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker to send messages back to the client
        // Prefixes for messages destined for the client (e.g., @SubscribeMapping)
        config.enableSimpleBroker("/topic");

        // Prefixes for messages bound for methods annotated with @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Define the endpoint where the frontend will connect
        registry.addEndpoint("/pulse-websocket")
                .setAllowedOriginPatterns("*") // Allow localhost:3000 to connect
                .withSockJS(); // Enable fallback options if WebSocket isn't supported , fallback to long polling
    }
}