package com.sentinelhub.module.software;

import com.sentinelhub.config.WebSocketLimitsProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final Counter broadcastsThrottled;
    private final Counter globalLimitRejected;
    private volatile boolean lastRejectWasGlobal;

    public AdminWebSocketSessionRegistry(WebSocketLimitsProperties limits,
                                         AdminWebSocketRateLimiter rateLimiter,
                                         MeterRegistry meterRegistry) {
        this.limits = limits;
        this.rateLimiter = rateLimiter;
        Gauge.builder("sentinel.websocket.connections.total", totalConnections::get)
                .description("Active admin WebSocket connections")
                .register(meterRegistry);
        Gauge.builder("sentinel.websocket.connections.tenants", () -> (double) tenantSessions.size())
                .description("Tenants with active WebSocket connections")
                .register(meterRegistry);
        broadcastsThrottled = Counter.builder("sentinel.websocket.broadcasts.throttled")
                .description("Throttled WebSocket broadcasts")
                .register(meterRegistry);
        globalLimitRejected = Counter.builder("sentinel.websocket.connections.rejected.global")
                .description("Connections rejected due to global pool quota")
                .register(meterRegistry);
    }

    public boolean tryRegister(String tenantId, WebSocketSession session) {
        lastRejectWasGlobal = false;
        int globalMax = limits.maxConnectionsGlobal();
        if (globalMax > 0 && totalConnections.get() >= globalMax) {
            globalLimitRejected.increment();
            lastRejectWasGlobal = true;
            return false;
        }
        synchronized (lockFor(tenantId)) {
            if (globalMax > 0 && totalConnections.get() >= globalMax) {
                globalLimitRejected.increment();
                lastRejectWasGlobal = true;
                return false;
            }
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
            throw new IllegalStateException("websocket connection limit exceeded");
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
            broadcastsThrottled.increment();
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
        out.put("broadcasts_throttled", (long) broadcastsThrottled.count());
        out.put("global_limit_rejected", (long) globalLimitRejected.count());
        out.put("max_connections_per_tenant", limits.maxConnectionsPerTenant());
        out.put("max_connections_global", limits.maxConnectionsGlobal());
        out.put("max_events_per_second_per_tenant", limits.maxEventsPerSecondPerTenant());
        return out;
    }

    public boolean isGlobalLimitExceeded() {
        return lastRejectWasGlobal;
    }

    private static Object lockFor(String tenantId) {
        return tenantId.intern();
    }
}
