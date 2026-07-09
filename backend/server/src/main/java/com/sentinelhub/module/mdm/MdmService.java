package com.sentinelhub.module.mdm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.device.DeviceRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MdmService {

    private final MdmRepository mdmRepository;
    private final DeviceRepository deviceRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public MdmService(MdmRepository mdmRepository, DeviceRepository deviceRepository,
                      AuditService auditService, ObjectMapper objectMapper) {
        this.mdmRepository = mdmRepository;
        this.deviceRepository = deviceRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public void seedDemoProfiles(String tenantId) {
        if (mdmRepository.hasProfiles(tenantId)) {
            return;
        }
        Map<String, Object> wifi = Map.of(
                "ssid", "Corp-WiFi",
                "security", "WPA2",
                "hidden", false,
                "auto_join", true
        );
        mdmRepository.insertProfile(tenantId, "企业 Wi-Fi", "wifi", toJson(wifi), true);
    }

    public List<Map<String, Object>> listProfilesForAdmin(String tenantId) {
        return mdmRepository.listProfiles(tenantId).stream().map(this::toProfileView).toList();
    }

    public Map<String, Object> createProfile(String tenantId, String userId, String name, String profileType,
                                             Map<String, Object> content, boolean enabled) {
        String id = mdmRepository.insertProfile(tenantId, name, profileType, toJson(content), enabled);
        auditService.log(tenantId, "user", userId, "mdm.profile.create", "mdm_profile", id,
                Map.of("name", name, "profile_type", profileType), null);
        return mdmRepository.findProfile(tenantId, id).map(this::toProfileView).orElseThrow();
    }

    public Map<String, Object> updateProfile(String tenantId, String userId, String id, String name,
                                             String profileType, Map<String, Object> content, boolean enabled) {
        mdmRepository.findProfile(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("mdm profile not found"));
        mdmRepository.updateProfile(tenantId, id, name, profileType, toJson(content), enabled);
        auditService.log(tenantId, "user", userId, "mdm.profile.update", "mdm_profile", id,
                Map.of("name", name), null);
        return mdmRepository.findProfile(tenantId, id).map(this::toProfileView).orElseThrow();
    }

    public Map<String, Object> assignProfile(String tenantId, String userId, String deviceId, String profileId) {
        deviceRepository.findById(tenantId, deviceId)
                .orElseThrow(() -> new IllegalArgumentException("device not found"));
        mdmRepository.findProfile(tenantId, profileId)
                .orElseThrow(() -> new IllegalArgumentException("mdm profile not found"));
        mdmRepository.assignProfile(tenantId, deviceId, profileId);
        auditService.log(tenantId, "user", userId, "mdm.profile.assign", "mdm_device_assignment",
                deviceId + ":" + profileId, Map.of("device_id", deviceId, "profile_id", profileId), null);
        return Map.of("device_id", deviceId, "profile_id", profileId, "status", "pending");
    }

    public List<Map<String, Object>> listAssignmentsForAdmin(String tenantId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return mdmRepository.listAssignmentsForAdmin(tenantId, pageSize, offset).stream()
                .map(this::toAssignmentView)
                .toList();
    }

    public int countAssignments(String tenantId) {
        return mdmRepository.countAssignments(tenantId);
    }

    public Map<String, Object> getProfilesSummaryForClient(String tenantId) {
        return mdmRepository.latestProfileUpdate(tenantId)
                .map(updatedAt -> Map.<String, Object>of(
                        "hash", profilesHash(tenantId),
                        "updated_at", updatedAt.toString(),
                        "count", mdmRepository.listEnabledProfilesForTenant(tenantId).size()
                ))
                .orElse(Map.of());
    }

    public List<Map<String, Object>> getProfilesForDevice(String clientId) {
        return deviceRepository.findByAgentIdAny(clientId)
                .map(device -> mdmRepository.listAssignmentsForDevice(device.tenantId(), device.id()).stream()
                        .map(this::toClientProfileView)
                        .toList())
                .orElse(List.of());
    }

    public Map<String, Object> reportApplied(String tenantId, String deviceId, String profileId) {
        mdmRepository.markApplied(tenantId, deviceId, profileId);
        return Map.of("status", "accepted");
    }

    public Map<String, Object> reportAppliedForClient(String clientId, String profileId) {
        var device = deviceRepository.findByAgentIdAny(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return reportApplied(device.tenantId(), device.id(), profileId);
    }

    private Map<String, Object> toProfileView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.get("id"));
        view.put("name", row.get("name"));
        view.put("profile_type", row.get("profile_type"));
        view.put("content", parseContent(row.get("content")));
        view.put("enabled", toBool(row.get("enabled")));
        view.put("created_at", row.get("created_at").toString());
        view.put("updated_at", row.get("updated_at").toString());
        return view;
    }

    private Map<String, Object> toAssignmentView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("device_id", row.get("device_id"));
        view.put("profile_id", row.get("profile_id"));
        view.put("profile_name", row.get("profile_name"));
        view.put("profile_type", row.get("profile_type"));
        view.put("hostname", row.get("hostname"));
        view.put("agent_id", row.get("agent_id"));
        view.put("status", row.get("status"));
        view.put("assigned_at", row.get("assigned_at").toString());
        Object appliedAt = row.get("applied_at");
        view.put("applied_at", appliedAt != null ? appliedAt.toString() : null);
        return view;
    }

    private Map<String, Object> toClientProfileView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("profile_id", row.get("profile_id"));
        view.put("name", row.get("name"));
        view.put("profile_type", row.get("profile_type"));
        view.put("content", parseContent(row.get("content")));
        view.put("status", row.get("status"));
        return view;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContent(Object raw) {
        if (raw == null) return Map.of();
        try {
            if (raw instanceof String s) {
                return objectMapper.readValue(s, new TypeReference<>() {});
            }
            return objectMapper.convertValue(raw, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String profilesHash(String tenantId) {
        List<Map<String, Object>> profiles = mdmRepository.listEnabledProfilesForTenant(tenantId);
        return sha256(toJson(profiles));
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
