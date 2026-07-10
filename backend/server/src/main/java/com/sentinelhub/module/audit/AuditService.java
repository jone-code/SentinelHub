package com.sentinelhub.module.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.AuditNatsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository auditRepository;
    private final ClickHouseAuditRepository clickHouseAuditRepository;
    private final NatsAuditPublisher natsAuditPublisher;
    private final AuditNatsProperties auditNatsProperties;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRepository auditRepository,
                        ClickHouseAuditRepository clickHouseAuditRepository,
                        NatsAuditPublisher natsAuditPublisher,
                        AuditNatsProperties auditNatsProperties,
                        ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.clickHouseAuditRepository = clickHouseAuditRepository;
        this.natsAuditPublisher = natsAuditPublisher;
        this.auditNatsProperties = auditNatsProperties;
        this.objectMapper = objectMapper;
    }

    public void log(String tenantId, String actorType, String actorId, String action,
                    String resource, String resourceId, Map<String, Object> detail, String ip) {
        String json = toJson(detail);
        if (auditNatsProperties.enabled()) {
            try {
                natsAuditPublisher.publish(tenantId, actorType, actorId, action, resource, resourceId, json, ip);
                return;
            } catch (Exception e) {
                log.warn("NATS audit publish failed, writing synchronously: {}", e.getMessage());
            }
        }
        writeSync(tenantId, actorType, actorId, action, resource, resourceId, json, ip);
    }

    void writeSync(String tenantId, String actorType, String actorId, String action,
                   String resource, String resourceId, String detailJson, String ip) {
        auditRepository.insert(tenantId, actorType, actorId, action, resource, resourceId, detailJson, ip);
        clickHouseAuditRepository.insert(tenantId, actorType, actorId, action, resource, resourceId, detailJson, ip);
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

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail != null ? detail : Map.of());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
