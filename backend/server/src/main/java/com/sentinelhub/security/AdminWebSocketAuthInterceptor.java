package com.sentinelhub.security;

import io.jsonwebtoken.Claims;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class AdminWebSocketAuthInterceptor implements HandshakeInterceptor {

    public static final String TENANT_ID_ATTR = "tenantId";
    public static final String USER_ID_ATTR = "userId";

    private final JwtService jwtService;

    public AdminWebSocketAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = jwtService.parse(token);
            String tenantId = claims.get("tenant_id", String.class);
            if (tenantId == null || tenantId.isBlank()) {
                return false;
            }
            attributes.put(TENANT_ID_ATTR, tenantId);
            attributes.put(USER_ID_ATTR, claims.getSubject());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
