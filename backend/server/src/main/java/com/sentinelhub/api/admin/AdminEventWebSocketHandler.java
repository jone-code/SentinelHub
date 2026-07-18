package com.sentinelhub.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.software.AdminEventPushService;
import com.sentinelhub.security.AdminWebSocketAuthInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class AdminEventWebSocketHandler extends TextWebSocketHandler {

    private final AdminEventPushService pushService;
    private final ObjectMapper objectMapper;

    public AdminEventWebSocketHandler(AdminEventPushService pushService, ObjectMapper objectMapper) {
        this.pushService = pushService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String tenantId = tenantId(session);
        if (tenantId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        if (!pushService.tryRegister(tenantId, session)) {
            int code = pushService.isGlobalLimitExceeded() ? 4430 : 4429;
            String reason = code == 4430 ? "global connection pool exhausted" : "tenant connection limit exceeded";
            session.close(new CloseStatus(code, reason));
            return;
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "connected",
                "filter", "driver.*"
        ))));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String tenantId = tenantId(session);
        if (tenantId != null) {
            pushService.unregister(tenantId, session);
        }
    }

    private static String tenantId(WebSocketSession session) {
        Object tenantId = session.getAttributes().get(AdminWebSocketAuthInterceptor.TENANT_ID_ATTR);
        return tenantId != null ? tenantId.toString() : null;
    }
}
