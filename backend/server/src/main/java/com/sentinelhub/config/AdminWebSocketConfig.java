package com.sentinelhub.config;

import com.sentinelhub.api.admin.AdminEventWebSocketHandler;
import com.sentinelhub.security.AdminWebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AdminWebSocketConfig implements WebSocketConfigurer {

    private final AdminEventWebSocketHandler eventWebSocketHandler;
    private final AdminWebSocketAuthInterceptor authInterceptor;

    public AdminWebSocketConfig(AdminEventWebSocketHandler eventWebSocketHandler,
                                AdminWebSocketAuthInterceptor authInterceptor) {
        this.eventWebSocketHandler = eventWebSocketHandler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(eventWebSocketHandler, "/api/admin/v1/ws/events")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*");
    }
}
