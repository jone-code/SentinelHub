package com.sentinelhub.module.zerotrust;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.device.DeviceRepository;
import com.sentinelhub.module.device.domain.Device;
import com.sentinelhub.module.nac.NacRepository;
import com.sentinelhub.module.software.ClientEventRepository;
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
public class ZerotrustService {

    private final ZerotrustRepository zerotrustRepository;
    private final DeviceRepository deviceRepository;
    private final NacRepository nacRepository;
    private final ClientEventRepository eventRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ZerotrustService(ZerotrustRepository zerotrustRepository, DeviceRepository deviceRepository,
                            NacRepository nacRepository, ClientEventRepository eventRepository,
                            AuditService auditService, ObjectMapper objectMapper) {
        this.zerotrustRepository = zerotrustRepository;
        this.deviceRepository = deviceRepository;
        this.nacRepository = nacRepository;
        this.eventRepository = eventRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public void seedDemoPolicy(String tenantId) {
        if (zerotrustRepository.hasPolicy(tenantId)) {
            return;
        }
        zerotrustRepository.insertPolicy(tenantId, "默认零信任策略", 60, 25, 15, 70, true);
        zerotrustRepository.insertProtectedApp(tenantId, "企业邮箱", "com.demo.mail", 70, true);
        zerotrustRepository.insertProtectedApp(tenantId, "VPN 客户端", "com.demo.vpn", 80, true);
    }

    public Map<String, Object> getPolicyForAdmin(String tenantId) {
        return zerotrustRepository.findPolicyByTenant(tenantId)
                .map(this::toPolicyView)
                .orElse(Map.of());
    }

    public Map<String, Object> updatePolicy(String tenantId, String userId, String name,
                                            int complianceWeight, int nacWeight, int eventWeight,
                                            int minTrustScore, boolean enabled) {
        Map<String, Object> existing = zerotrustRepository.findPolicyByTenant(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("zt policy not found"));
        String id = existing.get("id").toString();
        int cw = clampWeight(complianceWeight);
        int nw = clampWeight(nacWeight);
        int ew = clampWeight(eventWeight);
        if (cw + nw + ew != 100) {
            throw new IllegalArgumentException("weights must sum to 100");
        }
        zerotrustRepository.updatePolicy(tenantId, id, name, cw, nw, ew, minTrustScore, enabled);
        auditService.log(tenantId, "user", userId, "zt.policy.update", "zt_policy", id,
                Map.of("min_trust_score", minTrustScore, "enabled", enabled), null);
        recomputeAllForTenant(tenantId);
        return getPolicyForAdmin(tenantId);
    }

    public List<Map<String, Object>> listProtectedAppsForAdmin(String tenantId) {
        return zerotrustRepository.listProtectedApps(tenantId).stream()
                .map(this::toProtectedAppView)
                .toList();
    }

    public Map<String, Object> createProtectedApp(String tenantId, String userId, String name,
                                                  String appIdentifier, int minTrustScore, boolean enabled) {
        String id = zerotrustRepository.insertProtectedApp(tenantId, name, appIdentifier, minTrustScore, enabled);
        auditService.log(tenantId, "user", userId, "zt.app.create", "zt_protected_app", id,
                Map.of("name", name, "app_identifier", appIdentifier), null);
        return zerotrustRepository.findProtectedApp(tenantId, id)
                .map(this::toProtectedAppView)
                .orElseThrow();
    }

    public Map<String, Object> updateProtectedApp(String tenantId, String userId, String id, String name,
                                                  String appIdentifier, int minTrustScore, boolean enabled) {
        zerotrustRepository.findProtectedApp(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("protected app not found"));
        zerotrustRepository.updateProtectedApp(tenantId, id, name, appIdentifier, minTrustScore, enabled);
        auditService.log(tenantId, "user", userId, "zt.app.update", "zt_protected_app", id,
                Map.of("name", name), null);
        return zerotrustRepository.findProtectedApp(tenantId, id)
                .map(this::toProtectedAppView)
                .orElseThrow();
    }

    public Map<String, Object> getPolicySummaryForClient(String tenantId) {
        return zerotrustRepository.findPolicyByTenant(tenantId)
                .filter(p -> toBool(p.get("enabled")))
                .map(p -> Map.<String, Object>of(
                        "id", p.get("id"),
                        "hash", policyHash(p),
                        "updated_at", p.get("updated_at").toString()
                ))
                .orElse(Map.of());
    }

    public Map<String, Object> getPolicyForClient(String tenantId) {
        Map<String, Object> policy = zerotrustRepository.findPolicyByTenant(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("no zt policy"));
        if (!toBool(policy.get("enabled"))) {
            throw new IllegalArgumentException("zt policy disabled");
        }
        Map<String, Object> view = toPolicyView(policy);
        view.put("hash", policyHash(policy));
        view.put("protected_apps", listProtectedAppsForClient(tenantId));
        return view;
    }

    public Map<String, Object> getTrustForClient(String clientId) {
        return deviceRepository.findByAgentIdAny(clientId)
                .map(device -> buildTrustView(device))
                .orElse(Map.of("trust_score", 0, "trust_level", "unknown", "protected_apps", List.of()));
    }

    public int recomputeForDevice(String tenantId, String deviceId) {
        Device device = deviceRepository.findById(tenantId, deviceId)
                .orElseThrow(() -> new IllegalArgumentException("device not found"));
        return recomputeForDevice(device);
    }

    public int recomputeForDevice(Device device) {
        Map<String, Object> policy = zerotrustRepository.findPolicyByTenant(device.tenantId()).orElse(Map.of());
        int cw = policy.isEmpty() ? 60 : ((Number) policy.get("compliance_weight")).intValue();
        int nw = policy.isEmpty() ? 25 : ((Number) policy.get("nac_weight")).intValue();
        int ew = policy.isEmpty() ? 15 : ((Number) policy.get("event_weight")).intValue();

        int compliance = device.complianceScore() != null ? device.complianceScore() : 0;
        int nacScore = resolveNacScore(device.id());
        int eventScore = resolveEventScore(device.id());

        int trust = (compliance * cw + nacScore * nw + eventScore * ew) / 100;
        trust = Math.max(0, Math.min(100, trust));

        deviceRepository.updateTrustScore(device.tenantId(), device.id(), trust);

        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("compliance_score", compliance);
        factors.put("nac_score", nacScore);
        factors.put("event_score", eventScore);
        factors.put("weights", Map.of("compliance", cw, "nac", nw, "event", ew));
        zerotrustRepository.insertTrustHistory(device.tenantId(), device.id(), trust, toJson(factors));

        return trust;
    }

    public void recomputeAllForTenant(String tenantId) {
        deviceRepository.listByTenant(tenantId, 10_000, 0).forEach(this::recomputeForDevice);
    }

    public List<Map<String, Object>> listDeviceTrustForAdmin(String tenantId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return zerotrustRepository.listDeviceTrustForAdmin(tenantId, pageSize, offset).stream()
                .map(row -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("device_id", row.get("device_id"));
                    view.put("agent_id", row.get("agent_id"));
                    view.put("hostname", row.get("hostname"));
                    view.put("compliance_score", row.get("compliance_score"));
                    view.put("trust_score", row.get("trust_score"));
                    view.put("trust_level", trustLevel(intVal(row.get("trust_score"))));
                    Object lastSeen = row.get("last_seen_at");
                    view.put("last_seen_at", lastSeen != null ? lastSeen.toString() : null);
                    return view;
                })
                .toList();
    }

    public int countDevices(String tenantId) {
        return zerotrustRepository.countDevices(tenantId);
    }

    public List<Map<String, Object>> listTrustHistory(String tenantId, String deviceId) {
        return zerotrustRepository.listTrustHistory(tenantId, deviceId, 20).stream()
                .map(row -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("trust_score", row.get("trust_score"));
                    view.put("factors", parseJsonMap(row.get("factors")));
                    view.put("created_at", row.get("created_at").toString());
                    return view;
                })
                .toList();
    }

    private Map<String, Object> buildTrustView(Device device) {
        int trust = device.trustScore() != null ? device.trustScore() : recomputeForDevice(device);
        int minTrust = zerotrustRepository.findPolicyByTenant(device.tenantId())
                .map(p -> ((Number) p.get("min_trust_score")).intValue())
                .orElse(70);

        List<Map<String, Object>> apps = new ArrayList<>();
        for (Map<String, Object> app : zerotrustRepository.listProtectedApps(device.tenantId())) {
            if (!toBool(app.get("enabled"))) {
                continue;
            }
            int required = ((Number) app.get("min_trust_score")).intValue();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", app.get("name"));
            entry.put("app_identifier", app.get("app_identifier"));
            entry.put("min_trust_score", required);
            entry.put("allowed", trust >= required);
            apps.add(entry);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("trust_score", trust);
        view.put("trust_level", trustLevel(trust));
        view.put("min_trust_score", minTrust);
        view.put("meets_baseline", trust >= minTrust);
        view.put("protected_apps", apps);
        view.put("compliance_score", device.complianceScore() != null ? device.complianceScore() : 0);
        return view;
    }

    private List<Map<String, Object>> listProtectedAppsForClient(String tenantId) {
        return zerotrustRepository.listProtectedApps(tenantId).stream()
                .filter(app -> toBool(app.get("enabled")))
                .map(app -> Map.<String, Object>of(
                        "name", app.get("name"),
                        "app_identifier", app.get("app_identifier"),
                        "min_trust_score", app.get("min_trust_score")
                ))
                .toList();
    }

    private int resolveNacScore(String deviceId) {
        return nacRepository.findDeviceStatus(deviceId)
                .map(row -> nacScoreForState(stringVal(row.get("access_state"), "unknown")))
                .orElse(70);
    }

    private static int nacScoreForState(String state) {
        return switch (state) {
            case "allowed" -> 100;
            case "restricted" -> 50;
            case "denied" -> 0;
            default -> 70;
        };
    }

    private int resolveEventScore(String deviceId) {
        int high = eventRepository.countRecentHighSeverity(deviceId, 24);
        int warning = eventRepository.countRecentWarnings(deviceId, 24);
        int deduction = high * 15 + warning * 5;
        return Math.max(0, 100 - Math.min(100, deduction));
    }

    private static String trustLevel(int trust) {
        if (trust >= 80) return "high";
        if (trust >= 50) return "medium";
        return "low";
    }

    private Map<String, Object> toPolicyView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.get("id"));
        view.put("name", row.get("name"));
        view.put("compliance_weight", row.get("compliance_weight"));
        view.put("nac_weight", row.get("nac_weight"));
        view.put("event_weight", row.get("event_weight"));
        view.put("min_trust_score", row.get("min_trust_score"));
        view.put("enabled", toBool(row.get("enabled")));
        view.put("created_at", row.get("created_at").toString());
        view.put("updated_at", row.get("updated_at").toString());
        return view;
    }

    private Map<String, Object> toProtectedAppView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.get("id"));
        view.put("name", row.get("name"));
        view.put("app_identifier", row.get("app_identifier"));
        view.put("min_trust_score", row.get("min_trust_score"));
        view.put("enabled", toBool(row.get("enabled")));
        view.put("created_at", row.get("created_at").toString());
        view.put("updated_at", row.get("updated_at").toString());
        return view;
    }

    private String policyHash(Map<String, Object> policy) {
        return sha256(toJson(Map.of(
                "compliance_weight", policy.get("compliance_weight"),
                "nac_weight", policy.get("nac_weight"),
                "event_weight", policy.get("event_weight"),
                "min_trust_score", policy.get("min_trust_score"),
                "enabled", policy.get("enabled"),
                "updated_at", policy.get("updated_at").toString()
        )));
    }

    private static int clampWeight(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static int intVal(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(Object raw) {
        if (raw == null) return Map.of();
        try {
            if (raw instanceof String s) {
                return objectMapper.readValue(s, Map.class);
            }
            return objectMapper.convertValue(raw, Map.class);
        } catch (Exception e) {
            return Map.of();
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
