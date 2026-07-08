package com.sentinelhub.module.compliance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.device.DeviceRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ComplianceService {

    public static final List<Map<String, Object>> DEFAULT_RULES = List.of(
            Map.of("id", "firewall", "name", "防火墙", "weight", 25, "enabled", true),
            Map.of("id", "os_updates", "name", "操作系统补丁", "weight", 25, "enabled", true),
            Map.of("id", "disk_encryption", "name", "磁盘加密", "weight", 25, "enabled", true),
            Map.of("id", "antivirus", "name", "杀毒软件", "weight", 25, "enabled", true)
    );

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

    public void backfillBaselines(String tenantId) {
        for (Map<String, Object> baseline : complianceRepository.listByTenant(tenantId)) {
            if (baseline.get("content_hash") != null && !stringVal(baseline.get("content_hash")).isBlank()) {
                continue;
            }
            String id = baseline.get("id").toString();
            List<Map<String, Object>> rules = normalizeRules(parseRules((String) baseline.get("rules")));
            String rulesJson = toJson(rules);
            complianceRepository.updateBaseline(tenantId, id, (String) baseline.get("name"), rulesJson,
                    sha256(rulesJson));
        }
    }

    public void seedDefaultBaseline(String tenantId) {
        if (complianceRepository.hasBaseline(tenantId)) {
            return;
        }
        String rulesJson = toJson(DEFAULT_RULES);
        complianceRepository.insertBaseline(tenantId, "默认安全基线", "sentinel-basic", rulesJson,
                sha256(rulesJson));
    }

    public List<Map<String, Object>> listBaselinesForAdmin(String tenantId) {
        return complianceRepository.listByTenant(tenantId).stream().map(this::toBaselineView).toList();
    }

    public Map<String, Object> getBaselineForAdmin(String tenantId, String id) {
        Map<String, Object> row = complianceRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("baseline not found"));
        return toBaselineView(row);
    }

    public Map<String, Object> updateBaseline(String tenantId, String userId, String id, String name,
                                              List<Map<String, Object>> rules) {
        complianceRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("baseline not found"));
        List<Map<String, Object>> normalized = normalizeRules(rules);
        String rulesJson = toJson(normalized);
        String hash = sha256(rulesJson);
        complianceRepository.updateBaseline(tenantId, id, name, rulesJson, hash);
        auditService.log(tenantId, "user", userId, "compliance.baseline.update", "compliance_baseline", id,
                Map.of("name", name, "rule_count", normalized.size()), null);
        return getBaselineForAdmin(tenantId, id);
    }

    public Map<String, Object> getBaselineSummaryForClient(String clientId) {
        return deviceRepository.findByAgentIdAny(clientId)
                .flatMap(device -> complianceRepository.findActiveBaseline(device.tenantId())
                        .map(b -> Map.<String, Object>of(
                                "id", b.get("id"),
                                "hash", stringVal(b.get("content_hash")),
                                "updated_at", b.get("updated_at").toString()
                        )))
                .orElse(Map.of());
    }

    public Map<String, Object> getBaselineForClient(String clientId) {
        var device = deviceRepository.findByAgentIdAny(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        Map<String, Object> baseline = complianceRepository.findActiveBaseline(device.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("no compliance baseline"));
        return Map.of(
                "id", baseline.get("id"),
                "name", baseline.get("name"),
                "framework", baseline.get("framework"),
                "hash", stringVal(baseline.get("content_hash")),
                "updated_at", baseline.get("updated_at").toString(),
                "rules", parseRules((String) baseline.get("rules"))
        );
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> ingestScan(String tenantId, String deviceId, String clientId,
                                          Map<String, Object> report) {
        String baselineId = complianceRepository.findActiveBaselineId(tenantId)
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
                        .map(this::buildComplianceView))
                .orElse(Map.of("score", 0, "items", List.of()));
    }

    public List<Map<String, Object>> listForAdmin(String tenantId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return complianceRepository.listResultsByTenant(tenantId, pageSize, offset);
    }

    public Map<String, Object> overviewForAdmin(String tenantId) {
        return Map.of(
                "device_scanned", complianceRepository.countDevicesWithResults(tenantId),
                "average_score", Math.round(complianceRepository.averageScore(tenantId))
        );
    }

    private Map<String, Object> toBaselineView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.get("id"));
        view.put("name", row.get("name"));
        view.put("framework", row.get("framework"));
        view.put("is_active", row.get("is_active"));
        view.put("rules", parseRules((String) row.get("rules")));
        view.put("content_hash", row.get("content_hash"));
        view.put("created_at", row.get("created_at").toString());
        view.put("updated_at", row.get("updated_at").toString());
        return view;
    }

    private List<Map<String, Object>> normalizeRules(List<Map<String, Object>> rules) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            String id = stringVal(rule.get("id"));
            if (id.isBlank() || !isSupportedRule(id)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("name", firstNonBlank(stringVal(rule.get("name")), defaultName(id)));
            entry.put("weight", Math.max(1, intVal(rule.get("weight"))));
            entry.put("enabled", rule.get("enabled") == null || Boolean.TRUE.equals(rule.get("enabled"))
                    || "true".equalsIgnoreCase(stringVal(rule.get("enabled"))));
            normalized.add(entry);
        }
        if (normalized.isEmpty()) {
            List<Map<String, Object>> fallback = new ArrayList<>();
            for (Map<String, Object> rule : DEFAULT_RULES) {
                fallback.add(new LinkedHashMap<>(rule));
            }
            return fallback;
        }
        return normalized;
    }

    private boolean isSupportedRule(String id) {
        return DEFAULT_RULES.stream().anyMatch(r -> id.equals(r.get("id")));
    }

    private String defaultName(String id) {
        return DEFAULT_RULES.stream()
                .filter(r -> id.equals(r.get("id")))
                .map(r -> stringVal(r.get("name")))
                .findFirst()
                .orElse(id);
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

    private List<Map<String, Object>> parseRules(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
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
        return o != null ? o.toString() : "";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }
}
