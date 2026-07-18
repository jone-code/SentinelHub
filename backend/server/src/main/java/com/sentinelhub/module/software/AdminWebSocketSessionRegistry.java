package com.sentinelhub.module.software;

import com.sentinelhub.config.WebSocketLimitsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AdminWebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(AdminWebSocketSessionRegistry.class);

    private final WebSocketLimitsProperties limits;
    private final AdminWebSocketRateLimiter rateLimiter;
    private final Map<String, Set<WebSocketSession>> tenantSessions = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnections = new AtomicInteger();
    private final AtomicInteger broadcastsThrottled = new AtomicInteger();

    public AdminWebSocketSessionRegistry(WebSocketLimitsProperties limits,
                                         AdminWebSocketRateLimiter rateLimiter) {
        this.limits = limits;
        this.rateLimiter = rateLimiter;
    }

    public boolean tryRegister(String tenantId, WebSocketSession session) {
        synchronized (lockFor(tenantId)) {
            Set<WebSocketSession> sessions = tenantSessions.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet());
            int max = limits.maxConnectionsPerTenant();
            if (max > 0 && sessions.size() >= max) {
                return false;
            }
            sessions.add(session);
            totalConnections.incrementAndGet();
            return true;
        }
    }

    public void register(String tenantId, WebSocketSession session) {
        if (!tryRegister(tenantId, session)) {
            throw new IllegalStateException("tenant websocket connection limit exceeded");
        }
    }

    public void unregister(String tenantId, WebSocketSession session) {
        synchronized (lockFor(tenantId)) {
            Set<WebSocketSession> sessions = tenantSessions.get(tenantId);
            if (sessions != null && sessions.remove(session)) {
                totalConnections.decrementAndGet();
                if (sessions.isEmpty()) {
                    tenantSessions.remove(tenantId);
                }
            }
        }
    }

    public void broadcast(String tenantId, String json) {
        if (!rateLimiter.allowBroadcast(tenantId)) {
            broadcastsThrottled.incrementAndGet();
            return;
        }
        Set<WebSocketSession> sessions = tenantSessions.get(tenantId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        try {
            TextMessage message = new TextMessage(json);
            synchronized (lockFor(tenantId)) {
                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        synchronized (session) {
                            session.sendMessage(message);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket local broadcast failed: {}", e.getMessage());
        }
    }

    public Map<String, Object> stats() {
        Map<String, Integer> perTenant = new LinkedHashMap<>();
        tenantSessions.forEach((tenant, sessions) -> perTenant.put(tenant, sessions.size()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total_connections", totalConnections.get());
        out.put("tenant_count", tenantSessions.size());
        out.put("per_tenant", perTenant);
        out.put("broadcasts_throttled", broadcastsThrottled.get());
        out.put("max_connections_per_tenant", limits.maxConnectionsPerTenant());
        out.put("max_events_per_second_per_tenant", limits.maxEventsPerSecondPerTenant());
        return out;
    }

    private static Object lockFor(String tenantId) {
        return tenantId.intern();
    }
}
