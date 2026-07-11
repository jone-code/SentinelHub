package com.sentinelhub.module.software;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.ClientEventNatsProperties;
import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.zerotrust.ZerotrustService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SoftwareService {

    private static final Logger log = LoggerFactory.getLogger(SoftwareService.class);

    private final ClientEventRepository eventRepository;
    private final ClickHouseClientEventRepository clickHouseClientEventRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ZerotrustService zerotrustService;
    private final NatsClientEventPublisher natsClientEventPublisher;
    private final ClientEventNatsProperties clientEventNatsProperties;

    public SoftwareService(ClientEventRepository eventRepository,
                           ClickHouseClientEventRepository clickHouseClientEventRepository,
                           AuditService auditService,
                           ObjectMapper objectMapper,
                           ZerotrustService zerotrustService,
                           NatsClientEventPublisher natsClientEventPublisher,
                           ClientEventNatsProperties clientEventNatsProperties) {
        this.eventRepository = eventRepository;
        this.clickHouseClientEventRepository = clickHouseClientEventRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.zerotrustService = zerotrustService;
        this.natsClientEventPublisher = natsClientEventPublisher;
        this.clientEventNatsProperties = clientEventNatsProperties;
    }

    @SuppressWarnings("unchecked")
    public void ingestEvents(String tenantId, String deviceId, String clientId, List<Map<String, Object>> events) {
        if (clientEventNatsProperties.enabled()) {
            try {
                for (Map<String, Object> event : events) {
                    String type = normalizeEventType(event);
                    String severity = normalizeSeverity(event);
                    Map<String, Object> detail = event.get("detail") instanceof Map<?, ?> m
                            ? (Map<String, Object>) m : event;
                    detail.put("client_id", clientId);
                    natsClientEventPublisher.publish(
                            tenantId, deviceId, clientId, type, severity, toJson(detail));
                }
                zerotrustService.recomputeForDevice(tenantId, deviceId);
                return;
            } catch (Exception e) {
                log.warn("NATS client_events publish failed, writing synchronously: {}", e.getMessage());
            }
        }
        for (Map<String, Object> event : events) {
            String type = normalizeEventType(event);
            String severity = normalizeSeverity(event);
            Map<String, Object> detail = event.get("detail") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : event;
            detail.put("client_id", clientId);
            writeSync(tenantId, deviceId, clientId, type, severity, toJson(detail));
        }
        zerotrustService.recomputeForDevice(tenantId, deviceId);
    }

    void writeSync(String tenantId, String deviceId, String clientId, String eventType,
                   String severity, String detailJson) {
        eventRepository.insert(tenantId, deviceId, eventType, severity, detailJson);
        clickHouseClientEventRepository.insert(tenantId, deviceId, eventType, severity, detailJson);
        Map<String, Object> detail = parseDetail(detailJson);
        auditService.log(tenantId, "agent", clientId, eventType, "device", deviceId, detail, null);
    }

    public List<Map<String, Object>> listEventsForAdmin(String tenantId, int page, int pageSize,
                                                        String eventTypeFilter, String severityFilter,
                                                        String storage) {
        int offset = Math.max(0, (page - 1) * pageSize);
        if ("cold".equalsIgnoreCase(storage)) {
            return clickHouseClientEventRepository.list(tenantId, pageSize, offset, eventTypeFilter, severityFilter);
        }
        return eventRepository.listByTenant(tenantId, pageSize, offset, eventTypeFilter, severityFilter);
    }

    public int countEvents(String tenantId, String eventTypeFilter, String severityFilter, String storage) {
        if ("cold".equalsIgnoreCase(storage)) {
            return clickHouseClientEventRepository.count(tenantId, eventTypeFilter, severityFilter);
        }
        return eventRepository.countByTenant(tenantId, eventTypeFilter, severityFilter);
    }

    public int countRecentAlerts(String tenantId) {
        return eventRepository.countOpenAlerts(tenantId);
    }

    private Map<String, Object> parseDetail(String detailJson) {
        try {
            return objectMapper.readValue(detailJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String normalizeEventType(Map<String, Object> event) {
        String type = stringVal(event.get("event_type"));
        if (type == null || type.isBlank()) {
            return "software.violation";
        }
        return type;
    }

    private static String normalizeSeverity(Map<String, Object> event) {
        String severity = stringVal(event.get("severity"));
        if (severity == null || severity.isBlank()) {
            return "warning";
        }
        return severity;
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString() : null;
    }
}
