package com.sentinelhub.module.compliance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.device.DeviceRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ComplianceService {

    private final ComplianceRepository complianceRepository;
    private final DeviceRepository deviceRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ComplianceService(ComplianceRepository complianceRepository, DeviceRepository deviceRepository,
                             AuditService auditService, ObjectMapper objectMapper) {
        this.complianceRepository = complianceRepository;
        this.deviceRepository = deviceRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public void seedDefaultBaseline(String tenantId) {
        if (complianceRepository.hasBaseline(tenantId)) {
            return;
        }
        String rules = toJson(List.of(
                Map.of("id", "firewall", "name", "防火墙", "weight", 25),
                Map.of("id", "os_updates", "name", "操作系统补丁", "weight", 25),
                Map.of("id", "disk_encryption", "name", "磁盘加密", "weight", 25),
                Map.of("id", "antivirus", "name", "杀毒软件", "weight", 25)
        ));
        complianceRepository.insertBaseline(tenantId, "默认安全基线", "sentinel-basic", rules);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> ingestScan(String tenantId, String deviceId, String clientId,
                                          Map<String, Object> report) {
        String baselineId = complianceRepository.findDefaultBaselineId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("no compliance baseline"));

        int score = intVal(report.get("score"));
        int passed = intVal(report.get("passed"));
        int failed = intVal(report.get("failed"));
        List<Map<String, Object>> items = report.get("items") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();

        Instant scannedAt = parseTime(report.get("scanned_at"));
        Map<String, Object> details = Map.of("items", items);

        complianceRepository.insertResult(tenantId, deviceId, baselineId, score, passed, failed,
                toJson(details), scannedAt);
        deviceRepository.updateComplianceScore(tenantId, deviceId, score);

        auditService.log(tenantId, "agent", clientId, "compliance.scan", "device", deviceId,
                Map.of("score", score, "passed", passed, "failed", failed), null);

        return Map.of("score", score, "status", "accepted");
    }

    public Map<String, Object> getComplianceForClient(String clientId) {
        return deviceRepository.findByAgentIdAny(clientId)
                .flatMap(d -> complianceRepository.findLatestByDevice(d.id())
                        .map(r -> buildComplianceView(r)))
                .orElse(Map.of("score", 0, "items", List.of()));
    }

    public List<Map<String, Object>> listForAdmin(String tenantId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return complianceRepository.listByTenant(tenantId, pageSize, offset);
    }

    public Map<String, Object> overviewForAdmin(String tenantId) {
        return Map.of(
                "device_scanned", complianceRepository.countDevicesWithResults(tenantId),
                "average_score", Math.round(complianceRepository.averageScore(tenantId))
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildComplianceView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("score", row.get("score"));
        view.put("passed", row.get("passed"));
        view.put("failed", row.get("failed"));
        view.put("scanned_at", row.get("scanned_at").toString());

        List<Map<String, Object>> items = List.of();
        try {
            String detailsJson = (String) row.get("details");
            Map<String, Object> details = objectMapper.readValue(detailsJson, new TypeReference<>() {});
            if (details.get("items") instanceof List<?> list) {
                items = (List<Map<String, Object>>) list;
            }
        } catch (JsonProcessingException ignored) {
        }
        view.put("items", items);
        return view;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try {
                return Integer.parseInt(o.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static Instant parseTime(Object o) {
        if (o == null) return Instant.now();
        String s = o.toString();
        if (s.matches("\\d+")) {
            return Instant.ofEpochSecond(Long.parseLong(s));
        }
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString() : Instant.now().toString();
    }
}
