package com.sentinelhub.module.software;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.device.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminEventPushService {

    private static final Logger log = LoggerFactory.getLogger(AdminEventPushService.class);
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;
    private final Map<String, Set<WebSocketSession>> tenantSessions = new ConcurrentHashMap<>();

    public AdminEventPushService(ObjectMapper objectMapper, DeviceRepository deviceRepository) {
        this.objectMapper = objectMapper;
        this.deviceRepository = deviceRepository;
    }

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

    public void pushDriverEvent(String tenantId, String eventId, String deviceId, String clientId,
                                String eventType, String severity, Map<String, Object> detail) {
        if (eventType == null || !eventType.startsWith("driver.")) {
            return;
        }
        Set<WebSocketSession> sessions = tenantSessions.get(tenantId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String hostname = null;
        String agentId = clientId;
        var deviceOpt = deviceRepository.findById(tenantId, deviceId);
        if (deviceOpt.isPresent()) {
            hostname = deviceOpt.get().hostname();
            agentId = deviceOpt.get().agentId();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "driver.event");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", eventId);
        data.put("event_type", eventType);
        data.put("severity", severity);
        data.put("hostname", hostname);
        data.put("agent_id", agentId);
        data.put("detail", detail != null ? detail : Map.of());
        data.put("created_at", TS_FMT.format(Instant.now()));
        payload.put("data", data);

        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket driver event push failed: {}", e.getMessage());
        }
    }
}
