package com.sentinelhub.module.nac;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.device.DeviceRepository;
import com.sentinelhub.module.device.domain.Device;
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
public class NacService {

    private final NacRepository nacRepository;
    private final DeviceRepository deviceRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public NacService(NacRepository nacRepository, DeviceRepository deviceRepository,
                      AuditService auditService, ObjectMapper objectMapper) {
        this.nacRepository = nacRepository;
        this.deviceRepository = deviceRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public void seedDemoPolicy(String tenantId) {
        if (nacRepository.hasPolicy(tenantId)) {
            return;
        }
        nacRepository.insertPolicy(tenantId, "默认准入策略", 80, "restrict", "quarantine", true);
    }

    public Map<String, Object> getPolicyForAdmin(String tenantId) {
        return nacRepository.findPolicyByTenant(tenantId)
                .map(this::toPolicyView)
                .orElse(Map.of());
    }

    public Map<String, Object> updatePolicy(String tenantId, String userId, String name, int minScore,
                                            String actionOnFail, String isolateVlan, boolean enabled) {
        Map<String, Object> existing = nacRepository.findPolicyByTenant(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("nac policy not found"));
        String id = existing.get("id").toString();
        nacRepository.updatePolicy(tenantId, id, name, minScore, actionOnFail, isolateVlan, enabled);
        auditService.log(tenantId, "user", userId, "nac.policy.update", "nac_policy", id,
                Map.of("min_compliance_score", minScore, "action_on_fail", actionOnFail), null);
        return getPolicyForAdmin(tenantId);
    }

    public Map<String, Object> getPolicySummaryForClient(String tenantId) {
        return nacRepository.findPolicyByTenant(tenantId)
                .filter(p -> toBool(p.get("enabled")))
                .map(p -> Map.<String, Object>of(
                        "id", p.get("id"),
                        "hash", policyHash(p),
                        "updated_at", p.get("updated_at").toString()
                ))
                .orElse(Map.of());
    }

    public Map<String, Object> getPolicyForClient(String tenantId) {
        Map<String, Object> policy = nacRepository.findPolicyByTenant(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("no nac policy"));
        if (!toBool(policy.get("enabled"))) {
            throw new IllegalArgumentException("nac policy disabled");
        }
        Map<String, Object> view = toPolicyView(policy);
        view.put("hash", policyHash(policy));
        return view;
    }

    public Map<String, Object> ingestStatus(String tenantId, String deviceId, String clientId,
                                            Map<String, Object> status) {
        Device device = deviceRepository.findById(tenantId, deviceId)
                .orElseThrow(() -> new IllegalArgumentException("device not found"));
        Map<String, Object> policy = nacRepository.findPolicyByTenant(tenantId).orElse(Map.of());
        String policyId = policy.get("id") != null ? policy.get("id").toString() : null;
        String accessState = stringVal(status.get("access_state"), "unknown");
        Integer score = status.get("compliance_score") instanceof Number n ? n.intValue() : device.complianceScore();
        String detailJson = toJson(status);
        nacRepository.upsertDeviceStatus(tenantId, deviceId, policyId, accessState, score, detailJson, Instant.now());
        if ("denied".equals(accessState) || "restricted".equals(accessState)) {
            auditService.log(tenantId, "agent", clientId, "nac." + accessState, "device", deviceId, status, null);
        }
        return Map.of("access_state", accessState, "status", "accepted");
    }

    public Map<String, Object> evaluateForDevice(String tenantId, String deviceId) {
        Device device = deviceRepository.findById(tenantId, deviceId)
                .orElseThrow(() -> new IllegalArgumentException("device not found"));
        Map<String, Object> policy = nacRepository.findPolicyByTenant(tenantId).orElse(Map.of());
        return evaluate(device.complianceScore(), policy);
    }

    public List<Map<String, Object>> listDeviceStatusForAdmin(String tenantId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return nacRepository.listDeviceStatusByTenant(tenantId, pageSize, offset).stream()
                .map(this::toStatusView).toList();
    }

    public int countDeviceStatus(String tenantId) {
        return nacRepository.countDeviceStatus(tenantId);
    }

    public Map<String, Object> getStatusForClient(String clientId) {
        return deviceRepository.findByAgentIdAny(clientId)
                .flatMap(d -> nacRepository.findDeviceStatus(d.id()).map(this::toStatusView))
                .orElse(Map.of("access_state", "unknown"));
    }

    private Map<String, Object> evaluate(Integer complianceScore, Map<String, Object> policy) {
        if (policy.isEmpty() || !toBool(policy.get("enabled"))) {
            return Map.of("access_state", "allowed", "reason", "policy_disabled");
        }
        int score = complianceScore != null ? complianceScore : 0;
        int min = ((Number) policy.get("min_compliance_score")).intValue();
        if (score >= min) {
            return Map.of(
                    "access_state", "allowed",
                    "reason", "compliance_ok",
                    "compliance_score", score,
                    "min_compliance_score", min
            );
        }
        String action = stringVal(policy.get("action_on_fail"), "restrict");
        String accessState = switch (action) {
            case "deny", "denied" -> "denied";
            case "allow" -> "allowed";
            default -> "restricted";
        };
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_state", accessState);
        result.put("reason", "compliance_below_threshold");
        result.put("compliance_score", score);
        result.put("min_compliance_score", min);
        result.put("action_on_fail", action);
        result.put("isolate_vlan", policy.get("isolate_vlan"));
        return result;
    }

    private Map<String, Object> toPolicyView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.get("id"));
        view.put("name", row.get("name"));
        view.put("min_compliance_score", row.get("min_compliance_score"));
        view.put("action_on_fail", row.get("action_on_fail"));
        view.put("isolate_vlan", row.get("isolate_vlan"));
        view.put("enabled", toBool(row.get("enabled")));
        view.put("created_at", row.get("created_at").toString());
        view.put("updated_at", row.get("updated_at").toString());
        return view;
    }

    private Map<String, Object> toStatusView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("device_id", row.get("device_id"));
        view.put("hostname", row.get("hostname"));
        view.put("agent_id", row.get("agent_id"));
        view.put("access_state", row.get("access_state"));
        view.put("compliance_score", row.get("compliance_score"));
        view.put("evaluated_at", row.get("evaluated_at").toString());
        return view;
    }

    private String policyHash(Map<String, Object> policy) {
        return sha256(toJson(Map.of(
                "min_compliance_score", policy.get("min_compliance_score"),
                "action_on_fail", policy.get("action_on_fail"),
                "isolate_vlan", policy.get("isolate_vlan"),
                "enabled", policy.get("enabled"),
                "updated_at", policy.get("updated_at").toString()
        )));
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
            return "{}";
        }
    }

    private static String stringVal(Object value, String fallback) {
        return value != null && !value.toString().isBlank() ? value.toString() : fallback;
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
