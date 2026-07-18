package com.sentinelhub.module.software;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AdminWebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(AdminWebSocketSessionRegistry.class);

    private final Map<String, Set<WebSocketSession>> tenantSessions = new ConcurrentHashMap<>();

    public void register(String tenantId, WebSocketSession session) {
        tenantSessions.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(String tenantId, WebSocketSession session) {
        Set<WebSocketSession> sessions = tenantSessions.get(tenantId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                tenantSessions.remove(tenantId);
            }
        }
    }

    public void broadcast(String tenantId, String json) {
        Set<WebSocketSession> sessions = tenantSessions.get(tenantId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        try {
            TextMessage message = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket local broadcast failed: {}", e.getMessage());
        }
    }
}
