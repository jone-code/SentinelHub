package com.sentinelhub.module.dlp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.audit.AuditService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DlpService {

    private final DlpRepository dlpRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public DlpService(DlpRepository dlpRepository, AuditService auditService, ObjectMapper objectMapper) {
        this.dlpRepository = dlpRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public void seedDemoRules(String tenantId) {
        if (dlpRepository.hasRules(tenantId)) {
            return;
        }
        createRule(tenantId, "system", "USB 外设管控", "usb", "block",
                List.of("removable"), true, 100);
        createRule(tenantId, "system", "敏感文件外发", "sensitive_path", "alert",
                List.of("*.pem", "*.key", "*.p12", "*.pfx"), true, 90);
    }

    public List<Map<String, Object>> listRulesForAdmin(String tenantId) {
        return dlpRepository.listByTenant(tenantId).stream().map(this::toRuleView).toList();
    }

    public Map<String, Object> createRule(String tenantId, String userId, String name, String channel,
                                          String action, List<String> patterns, boolean enabled, int priority) {
        String patternsJson = toJson(patterns != null ? patterns : List.of());
        String id = dlpRepository.insert(tenantId, name, channel, action, patternsJson, enabled, priority);
        auditService.log(tenantId, "user", userId, "dlp.rule.create", "dlp_rule", id,
                Map.of("name", name, "channel", channel), null);
        return getRuleForAdmin(tenantId, id);
    }

    public Map<String, Object> updateRule(String tenantId, String userId, String id, String name, String channel,
                                          String action, List<String> patterns, boolean enabled, int priority) {
        dlpRepository.findById(tenantId, id).orElseThrow(() -> new IllegalArgumentException("dlp rule not found"));
        dlpRepository.update(tenantId, id, name, channel, action, toJson(patterns), enabled, priority);
        auditService.log(tenantId, "user", userId, "dlp.rule.update", "dlp_rule", id, Map.of("name", name), null);
        return getRuleForAdmin(tenantId, id);
    }

    public Map<String, Object> getRuleForAdmin(String tenantId, String id) {
        Map<String, Object> row = dlpRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("dlp rule not found"));
        return toRuleView(row);
    }

    public Map<String, Object> getRulesSummaryForClient(String tenantId) {
        List<Map<String, Object>> rules = dlpRepository.listEnabled(tenantId);
        if (rules.isEmpty()) {
            return Map.of();
        }
        String payload = toJson(Map.of("rules", rules.stream().map(this::toClientRule).toList()));
        return Map.of(
                "hash", sha256(payload),
                "updated_at", Instant.now().toString(),
                "rule_count", rules.size()
        );
    }

    public Map<String, Object> getRulesForClient(String tenantId) {
        List<Map<String, Object>> rules = dlpRepository.listEnabled(tenantId).stream()
                .map(this::toClientRule).toList();
        String payload = toJson(Map.of("rules", rules));
        return Map.of(
                "hash", sha256(payload),
                "updated_at", Instant.now().toString(),
                "rules", rules
        );
    }

    public List<Map<String, Object>> listEventsForAdmin(String tenantId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return dlpRepository.listEvents(tenantId, pageSize, offset);
    }

    public int countEvents(String tenantId) {
        return dlpRepository.countEvents(tenantId);
    }

    private Map<String, Object> toRuleView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.get("id"));
        view.put("name", row.get("name"));
        view.put("channel", row.get("channel"));
        view.put("action", row.get("action"));
        view.put("patterns", parsePatterns((String) row.get("patterns")));
        view.put("enabled", toBool(row.get("enabled")));
        view.put("priority", row.get("priority"));
        view.put("created_at", row.get("created_at").toString());
        view.put("updated_at", row.get("updated_at").toString());
        return view;
    }

    private Map<String, Object> toClientRule(Map<String, Object> row) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", row.get("id"));
        rule.put("name", row.get("name"));
        rule.put("channel", row.get("channel"));
        rule.put("action", row.get("action"));
        rule.put("patterns", parsePatterns((String) row.get("patterns")));
        rule.put("priority", row.get("priority"));
        return rule;
    }

    private List<String> parsePatterns(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private boolean toBool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(value));
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
}
