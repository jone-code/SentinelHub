package com.sentinelhub.module.software;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.device.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminEventPushService {

    private static final Logger log = LoggerFactory.getLogger(AdminEventPushService.class);
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;
    private final NatsAdminEventBroadcastBridge broadcastBridge;
    private final AdminWebSocketSessionRegistry sessionRegistry;

    public AdminEventPushService(ObjectMapper objectMapper,
                                 DeviceRepository deviceRepository,
                                 NatsAdminEventBroadcastBridge broadcastBridge,
                                 AdminWebSocketSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.deviceRepository = deviceRepository;
        this.broadcastBridge = broadcastBridge;
        this.sessionRegistry = sessionRegistry;
    }

    public boolean tryRegister(String tenantId, org.springframework.web.socket.WebSocketSession session) {
        return sessionRegistry.tryRegister(tenantId, session);
    }

    public boolean isGlobalLimitExceeded() {
        return sessionRegistry.isGlobalLimitExceeded();
    }

    public void register(String tenantId, org.springframework.web.socket.WebSocketSession session) {
        sessionRegistry.register(tenantId, session);
    }

    public void unregister(String tenantId, org.springframework.web.socket.WebSocketSession session) {
        sessionRegistry.unregister(tenantId, session);
    }

    public void pushDriverEvent(String tenantId, String eventId, String deviceId, String clientId,
                                String eventType, String severity, Map<String, Object> detail) {
        if (eventType == null || !eventType.startsWith("driver.")) {
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

        if (broadcastBridge.isEnabled()) {
            broadcastBridge.publish(tenantId, payload);
        } else {
            try {
                sessionRegistry.broadcast(tenantId, objectMapper.writeValueAsString(payload));
            } catch (Exception e) {
                log.warn("WebSocket driver event push failed: {}", e.getMessage());
            }
        }
    }
}
