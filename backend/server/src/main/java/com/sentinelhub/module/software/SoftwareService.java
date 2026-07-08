package com.sentinelhub.module.software;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.audit.AuditService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SoftwareService {

    private final ClientEventRepository eventRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SoftwareService(ClientEventRepository eventRepository, AuditService auditService,
                           ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public void ingestEvents(String tenantId, String deviceId, String clientId, List<Map<String, Object>> events) {
        for (Map<String, Object> event : events) {
            String type = stringVal(event.get("event_type"));
            if (type == null || type.isBlank()) {
                type = "software.violation";
            }
            String severity = stringVal(event.get("severity"));
            if (severity == null || severity.isBlank()) {
                severity = "warning";
            }
            Map<String, Object> detail = event.get("detail") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : event;
            detail.put("client_id", clientId);
            eventRepository.insert(tenantId, deviceId, type, severity, toJson(detail));
            auditService.log(tenantId, "agent", clientId, type, "device", deviceId, detail, null);
        }
    }

    public List<Map<String, Object>> listEventsForAdmin(String tenantId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return eventRepository.listByTenant(tenantId, pageSize, offset);
    }

    public int countEvents(String tenantId) {
        return eventRepository.countByTenant(tenantId);
    }

    public int countRecentAlerts(String tenantId) {
        return eventRepository.countOpenAlerts(tenantId);
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString() : null;
    }
}
