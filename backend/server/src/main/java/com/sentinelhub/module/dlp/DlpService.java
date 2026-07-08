package com.sentinelhub.module.dlp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.storage.MinioStorageService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DlpService {

    private static final int MAX_EVIDENCE_BYTES = 65_536;

    private final DlpRepository dlpRepository;
    private final DlpEvidenceRepository evidenceRepository;
    private final MinioStorageService minioStorageService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public DlpService(DlpRepository dlpRepository, DlpEvidenceRepository evidenceRepository,
                      MinioStorageService minioStorageService, AuditService auditService,
                      ObjectMapper objectMapper) {
        this.dlpRepository = dlpRepository;
        this.evidenceRepository = evidenceRepository;
        this.minioStorageService = minioStorageService;
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

    public Map<String, Object> ingestEvidence(String tenantId, String deviceId, String clientId,
                                              Map<String, Object> body) {
        String ruleId = stringVal(body.get("rule_id"));
        String filename = stringVal(body.get("filename"));
        String contentBase64 = stringVal(body.get("content_base64"));
        String channel = stringVal(body.get("channel"));
        if (filename.isBlank() || contentBase64.isBlank()) {
            throw new IllegalArgumentException("filename and content_base64 required");
        }
        byte[] data = Base64.getDecoder().decode(contentBase64);
        if (data.length > MAX_EVIDENCE_BYTES) {
            throw new IllegalArgumentException("evidence exceeds 64KB limit");
        }
        String hash = sha256Bytes(data);
        String expected = stringVal(body.get("sha256"));
        if (!expected.isBlank() && !hash.equals(expected) && !("sha256:" + expected).equals(hash)) {
            throw new IllegalArgumentException("sha256 mismatch");
        }

        String evidenceId = UUID.randomUUID().toString();
        String objectKey = tenantId + "/" + deviceId + "/" + evidenceId + "/" + sanitizeFilename(filename);
        if (minioStorageService.isEnabled()) {
            minioStorageService.putObject(objectKey, data, guessContentType(filename));
        } else {
            objectKey = "local-disabled/" + objectKey;
        }

        String id = evidenceRepository.insert(tenantId, deviceId, ruleId.isBlank() ? null : ruleId,
                null, objectKey, filename, guessContentType(filename), data.length, hash, channel);
        auditService.log(tenantId, "agent", clientId, "dlp.evidence.uploaded", "dlp_evidence", id,
                Map.of("filename", filename, "size_bytes", data.length, "channel", channel), null);
        return Map.of("evidence_id", id, "sha256", hash, "status", "stored");
    }

    public List<Map<String, Object>> listEvidenceForAdmin(String tenantId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return evidenceRepository.listByTenant(tenantId, pageSize, offset).stream()
                .map(this::toEvidenceView).toList();
    }

    public int countEvidence(String tenantId) {
        return evidenceRepository.countByTenant(tenantId);
    }

    public Map<String, Object> getEvidenceDownloadUrl(String tenantId, String id) {
        Map<String, Object> row = evidenceRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("evidence not found"));
        if (!minioStorageService.isEnabled()) {
            return Map.of("message", "minio disabled", "object_key", row.get("object_key"));
        }
        String url = minioStorageService.presignedGetUrl(row.get("object_key").toString(), Duration.ofMinutes(15));
        return Map.of(
                "evidence_id", id,
                "download_url", url,
                "expires_in_seconds", 900,
                "filename", row.get("filename")
        );
    }

    private Map<String, Object> toEvidenceView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.get("id"));
        view.put("rule_id", row.get("rule_id"));
        view.put("filename", row.get("filename"));
        view.put("size_bytes", row.get("size_bytes"));
        view.put("sha256", row.get("sha256"));
        view.put("channel", row.get("channel"));
        view.put("hostname", row.get("hostname"));
        view.put("agent_id", row.get("agent_id"));
        view.put("created_at", row.get("created_at").toString());
        return view;
    }

    private static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String guessContentType(String filename) {
        if (filename.endsWith(".pem") || filename.endsWith(".key")) {
            return "application/x-pem-file";
        }
        if (filename.endsWith(".json")) {
            return "application/json";
        }
        return "application/octet-stream";
    }

    private static String sha256Bytes(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString() : "";
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
