package com.sentinelhub.module.remote;

import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.device.DeviceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.RemoteRtcProperties;
import com.sentinelhub.module.identity.UserRepository;
import com.sentinelhub.module.identity.domain.User;
import com.sentinelhub.storage.MinioStorageService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RemoteService {

    private final RemoteRepository remoteRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final MinioStorageService minioStorageService;
    private final RemoteRtcProperties remoteRtcProperties;
    private final ObjectMapper objectMapper;

    public RemoteService(RemoteRepository remoteRepository, DeviceRepository deviceRepository,
                         UserRepository userRepository, AuditService auditService,
                         MinioStorageService minioStorageService, RemoteRtcProperties remoteRtcProperties,
                         ObjectMapper objectMapper) {
        this.remoteRepository = remoteRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.minioStorageService = minioStorageService;
        this.remoteRtcProperties = remoteRtcProperties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> createSession(String tenantId, String userId, String deviceId,
                                             String reason, boolean consentRequired) {
        deviceRepository.findById(tenantId, deviceId)
                .orElseThrow(() -> new IllegalArgumentException("device not found"));
        remoteRepository.findActiveByDevice(deviceId).ifPresent(s -> {
            throw new IllegalArgumentException("device already has an active remote session");
        });

        String operatorName = resolveOperatorName(tenantId, userId);
        String id = remoteRepository.insertSession(tenantId, deviceId, userId, operatorName, reason, consentRequired);
        auditService.log(tenantId, "user", userId, "remote.session.create", "remote_session", id,
                Map.of("device_id", deviceId, "reason", reason != null ? reason : ""), null);
        return getSessionForAdmin(tenantId, id);
    }

    public Map<String, Object> getSessionForAdmin(String tenantId, String id) {
        return remoteRepository.findById(tenantId, id)
                .map(this::toSessionView)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
    }

    public List<Map<String, Object>> listSessionsForAdmin(String tenantId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return remoteRepository.listByTenant(tenantId, pageSize, offset).stream()
                .map(this::toSessionView)
                .toList();
    }

    public int countSessions(String tenantId) {
        return remoteRepository.countByTenant(tenantId);
    }

    public Map<String, Object> endSession(String tenantId, String userId, String id, String recordingKey) {
        remoteRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        Instant now = Instant.now();
        remoteRepository.endSession(tenantId, id, recordingKey, now);
        auditService.log(tenantId, "user", userId, "remote.session.end", "remote_session", id,
                Map.of("recording_key", recordingKey != null ? recordingKey : ""), null);
        return getSessionForAdmin(tenantId, id);
    }

    public Map<String, Object> cancelSession(String tenantId, String userId, String id) {
        remoteRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        Instant now = Instant.now();
        remoteRepository.cancelSession(tenantId, id, now);
        auditService.log(tenantId, "user", userId, "remote.session.cancel", "remote_session", id, Map.of(), null);
        return getSessionForAdmin(tenantId, id);
    }

    public List<Map<String, Object>> getCommandsForClient(String clientId) {
        return deviceRepository.findByAgentIdAny(clientId)
                .map(device -> {
                    List<Map<String, Object>> commands = new ArrayList<>();
                    for (Map<String, Object> session : remoteRepository.findPendingByDevice(device.id())) {
                        Map<String, Object> cmd = new LinkedHashMap<>();
                        cmd.put("type", "remote.request");
                        cmd.put("session_id", session.get("id"));
                        cmd.put("operator_name", session.get("operator_name"));
                        cmd.put("reason", session.get("reason"));
                        cmd.put("consent_required", toBool(session.get("consent_required")));
                        commands.add(cmd);
                    }
                    return commands;
                })
                .orElse(List.of());
    }

    public Map<String, Object> handleConsent(String tenantId, String deviceId, String clientId,
                                             String sessionId, boolean accepted) {
        Map<String, Object> session = remoteRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        if (!deviceId.equals(session.get("device_id").toString())) {
            throw new IllegalArgumentException("session does not belong to device");
        }
        if (!"pending".equals(session.get("status"))) {
            throw new IllegalArgumentException("session is not pending");
        }
        Instant now = Instant.now();
        remoteRepository.markConsented(tenantId, sessionId, accepted, now);
        String action = accepted ? "remote.session.accept" : "remote.session.decline";
        auditService.log(tenantId, "agent", clientId, action, "remote_session", sessionId,
                Map.of("accepted", accepted), null);
        return Map.of("session_id", sessionId, "status", accepted ? "active" : "cancelled");
    }

    public Map<String, Object> reportStatus(String tenantId, String deviceId, String clientId,
                                            String sessionId, String status, String recordingKey) {
        remoteRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        if (!deviceId.equals(remoteRepository.findById(tenantId, sessionId).orElseThrow().get("device_id").toString())) {
            throw new IllegalArgumentException("session does not belong to device");
        }
        Instant now = Instant.now();
        if ("ended".equals(status)) {
            remoteRepository.endSession(tenantId, sessionId, recordingKey, now);
        } else if ("cancelled".equals(status)) {
            remoteRepository.cancelSession(tenantId, sessionId, now);
        }
        auditService.log(tenantId, "agent", clientId, "remote.session." + status, "remote_session", sessionId,
                Map.of("recording_key", recordingKey != null ? recordingKey : ""), null);
        return Map.of("session_id", sessionId, "status", status);
    }

    public Map<String, Object> getActiveForClient(String clientId) {
        return deviceRepository.findByAgentIdAny(clientId)
                .flatMap(d -> remoteRepository.findActiveByDevice(d.id()).map(this::toClientSessionView))
                .orElse(Map.of());
    }

    public Map<String, Object> postSignaling(String tenantId, String userId, String sessionId,
                                             String role, String sdpType, String sdpPayload) {
        remoteRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        remoteRepository.insertSignaling(tenantId, sessionId, role, sdpType, sdpPayload);
        auditService.log(tenantId, "user", userId, "remote.signaling." + role, "remote_session", sessionId,
                Map.of("sdp_type", sdpType), null);
        return Map.of("session_id", sessionId, "role", role, "status", "stored");
    }

    public Map<String, Object> postClientSignaling(String tenantId, String deviceId, String clientId,
                                                   String sessionId, String sdpType, String sdpPayload) {
        Map<String, Object> session = remoteRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        if (!deviceId.equals(session.get("device_id").toString())) {
            throw new IllegalArgumentException("session does not belong to device");
        }
        remoteRepository.insertSignaling(tenantId, sessionId, "client", sdpType, sdpPayload);
        auditService.log(tenantId, "agent", clientId, "remote.signaling.client", "remote_session", sessionId,
                Map.of("sdp_type", sdpType), null);
        return Map.of("session_id", sessionId, "status", "stored");
    }

    public Map<String, Object> getSignalingForRole(String tenantId, String sessionId, String role) {
        remoteRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        return remoteRepository.findLatestSignaling(sessionId, role)
                .map(row -> Map.<String, Object>of(
                        "role", row.get("role"),
                        "sdp_type", row.get("sdp_type"),
                        "sdp_payload", row.get("sdp_payload"),
                        "created_at", row.get("created_at").toString()
                ))
                .orElse(Map.of());
    }

    public Map<String, Object> getRtcConfig() {
        List<Map<String, Object>> iceServers = new ArrayList<>();
        Map<String, Object> stunEntry = new LinkedHashMap<>();
        stunEntry.put("urls", remoteRtcProperties.resolvedStunServers());
        iceServers.add(stunEntry);
        if (remoteRtcProperties.turnUrl() != null && !remoteRtcProperties.turnUrl().isBlank()) {
            Map<String, Object> turn = new LinkedHashMap<>();
            turn.put("urls", remoteRtcProperties.turnUrl());
            if (remoteRtcProperties.turnUsername() != null) {
                turn.put("username", remoteRtcProperties.turnUsername());
            }
            if (remoteRtcProperties.turnCredential() != null) {
                turn.put("credential", remoteRtcProperties.turnCredential());
            }
            iceServers.add(turn);
        }
        return Map.of("ice_servers", iceServers);
    }

    public Map<String, Object> postIceCandidate(String tenantId, String userId, String sessionId,
                                                String role, Map<String, Object> candidate) {
        remoteRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        String json = toJson(candidate);
        remoteRepository.insertIce(tenantId, sessionId, role, json);
        return Map.of("status", "stored");
    }

    public Map<String, Object> postClientIce(String tenantId, String deviceId, String clientId,
                                           String sessionId, Map<String, Object> candidate) {
        Map<String, Object> session = remoteRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        if (!deviceId.equals(session.get("device_id").toString())) {
            throw new IllegalArgumentException("session does not belong to device");
        }
        remoteRepository.insertIce(tenantId, sessionId, "client", toJson(candidate));
        return Map.of("status", "stored");
    }

    public List<Map<String, Object>> listIceForRole(String tenantId, String sessionId, String role) {
        remoteRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        return remoteRepository.listIceBySessionAndRole(sessionId, role).stream()
                .map(row -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", row.get("id"));
                    view.put("candidate", parseJsonString(row.get("sdp_payload").toString()));
                    view.put("created_at", row.get("created_at").toString());
                    return view;
                })
                .toList();
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonString(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public Map<String, Object> uploadRecording(String tenantId, String deviceId, String clientId,
                                               String sessionId, byte[] data, String contentType) {
        remoteRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        if (data.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("recording too large (max 5MB for demo)");
        }
        String objectKey = "remote-recordings/" + tenantId + "/" + sessionId + "/session.dat";
        if (minioStorageService.isEnabled()) {
            minioStorageService.putObject(objectKey, data, contentType);
        }
        Instant now = Instant.now();
        remoteRepository.endSession(tenantId, sessionId, objectKey, now);
        auditService.log(tenantId, "agent", clientId, "remote.recording.upload", "remote_session", sessionId,
                Map.of("object_key", objectKey, "bytes", data.length), null);
        return Map.of("session_id", sessionId, "recording_key", objectKey, "status", "stored");
    }

    public Map<String, Object> getRecordingUrl(String tenantId, String sessionId) {
        Map<String, Object> session = remoteRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        String key = session.get("recording_key") != null ? session.get("recording_key").toString() : null;
        if (key == null || key.isBlank()) {
            return Map.of("available", false);
        }
        if (!minioStorageService.isEnabled()) {
            return Map.of("available", true, "recording_key", key, "presigned_url", null);
        }
        String url = minioStorageService.presignedGetUrl(key, Duration.ofMinutes(15));
        return Map.of("available", true, "recording_key", key, "presigned_url", url);
    }

    private Map<String, Object> toSessionView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.get("id"));
        view.put("device_id", row.get("device_id"));
        view.put("hostname", row.get("hostname"));
        view.put("agent_id", row.get("agent_id"));
        view.put("operator_user_id", row.get("operator_user_id"));
        view.put("operator_name", row.get("operator_name"));
        view.put("status", row.get("status"));
        view.put("reason", row.get("reason"));
        view.put("consent_required", toBool(row.get("consent_required")));
        putTime(view, "consented_at", row.get("consented_at"));
        putTime(view, "started_at", row.get("started_at"));
        putTime(view, "ended_at", row.get("ended_at"));
        view.put("recording_key", row.get("recording_key"));
        view.put("created_at", row.get("created_at").toString());
        view.put("updated_at", row.get("updated_at").toString());
        return view;
    }

    private Map<String, Object> toClientSessionView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("session_id", row.get("id"));
        view.put("operator_name", row.get("operator_name"));
        view.put("status", row.get("status"));
        view.put("reason", row.get("reason"));
        putTime(view, "started_at", row.get("started_at"));
        return view;
    }

    private String resolveOperatorName(String tenantId, String userId) {
        return userRepository.findById(tenantId, userId)
                .map(User::name)
                .filter(name -> name != null && !name.isBlank())
                .orElse("管理员");
    }

    private static void putTime(Map<String, Object> view, String key, Object value) {
        view.put(key, value != null ? value.toString() : null);
    }

    private boolean toBool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
