package com.sentinelhub.module.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AuditService {

    private final AuditRepository auditRepository;
    private final ClickHouseAuditRepository clickHouseAuditRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRepository auditRepository,
                        ClickHouseAuditRepository clickHouseAuditRepository,
                        ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.clickHouseAuditRepository = clickHouseAuditRepository;
        this.objectMapper = objectMapper;
    }

    public void log(String tenantId, String actorType, String actorId, String action,
                    String resource, String resourceId, Map<String, Object> detail, String ip) {
        String json = "{}";
        try {
            json = objectMapper.writeValueAsString(detail != null ? detail : Map.of());
        } catch (JsonProcessingException ignored) {
        }
        auditRepository.insert(tenantId, actorType, actorId, action, resource, resourceId, json, ip);
        clickHouseAuditRepository.insert(tenantId, actorType, actorId, action, resource, resourceId, json, ip);
    }

    public List<Map<String, Object>> listForAdmin(String tenantId, int page, int pageSize,
                                                   String actionFilter, String storage) {
        int offset = Math.max(0, (page - 1) * pageSize);
        if ("cold".equalsIgnoreCase(storage)) {
            return clickHouseAuditRepository.list(tenantId, pageSize, offset, actionFilter);
        }
        return auditRepository.list(tenantId, pageSize, offset, actionFilter);
    }

    public int count(String tenantId, String actionFilter, String storage) {
        if ("cold".equalsIgnoreCase(storage)) {
            return clickHouseAuditRepository.count(tenantId, actionFilter);
        }
        return auditRepository.count(tenantId, actionFilter);
    }
}
