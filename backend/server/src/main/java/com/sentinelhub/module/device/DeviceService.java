package com.sentinelhub.module.device;

import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.device.domain.Device;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeviceService {

    private static final Duration ONLINE_THRESHOLD = Duration.ofMinutes(2);

    private final DeviceRepository deviceRepository;
    private final AuditService auditService;

    public DeviceService(DeviceRepository deviceRepository, AuditService auditService) {
        this.deviceRepository = deviceRepository;
        this.auditService = auditService;
    }

    public Map<String, Object> register(String tenantId, String tenantToken, Map<String, Object> body) {
        String rawClientId = stringVal(body.get("client_id"));
        final String agentId = (rawClientId == null || rawClientId.isBlank())
                ? "agent-" + UUID.randomUUID() : rawClientId;

        @SuppressWarnings("unchecked")
        Map<String, Object> hardware = body.get("hardware") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        String hostname = firstNonBlank(stringVal(body.get("hostname")), stringVal(hardware.get("hostname")), "unknown");
        String osType = firstNonBlank(stringVal(body.get("os_type")), stringVal(hardware.get("os_type")), "unknown");
        String osVersion = firstNonBlank(stringVal(body.get("os_version")), stringVal(hardware.get("os_version")));
        String hardwareId = firstNonBlank(stringVal(body.get("hardware_id")), stringVal(hardware.get("hardware_id")), hostname);

        Device device = deviceRepository.findByAgentId(tenantId, agentId)
                .orElseGet(() -> deviceRepository.insert(tenantId, agentId, hostname, osType, osVersion, hardwareId));

        auditService.log(tenantId, "agent", agentId, "device.register", "device", device.id(),
                Map.of("hostname", hostname), null);

        return Map.of(
                "client_id", agentId,
                "device_id", device.id(),
                "status", "registered"
        );
    }

    public Map<String, Object> heartbeat(String tenantId, String clientId, String version) {
        deviceRepository.findByAgentId(tenantId, clientId).ifPresent(device ->
                deviceRepository.updateHeartbeat(tenantId, clientId, null, null, null));
        return Map.of(
                "server_time", Instant.now().toString(),
                "commands", List.of()
        );
    }

    public Map<String, Object> heartbeatGlobal(String clientId) {
        deviceRepository.findByAgentIdAny(clientId).ifPresent(device ->
                deviceRepository.updateHeartbeat(device.tenantId(), clientId, null, null, null));
        return Map.of(
                "server_time", Instant.now().toString(),
                "commands", List.of()
        );
    }

    public void touchHeartbeat(String tenantId, String clientId) {
        deviceRepository.updateHeartbeat(tenantId, clientId, null, null, null);
    }

    public List<Map<String, Object>> listDevicesForAdmin(String tenantId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return deviceRepository.listByTenant(tenantId, pageSize, offset).stream()
                .map(this::toAdminView)
                .toList();
    }

    public int countDevices(String tenantId) {
        return deviceRepository.countByTenant(tenantId);
    }

    public java.util.Optional<OptionalDevice> resolveClient(String clientId) {
        return deviceRepository.findByAgentIdAny(clientId)
                .map(d -> new OptionalDevice(d.tenantId(), d.agentId(), d.id()));
    }

    public record OptionalDevice(String tenantId, String agentId, String deviceId) {}

    private Map<String, Object> toAdminView(Device d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.id());
        m.put("agent_id", d.agentId());
        m.put("hostname", d.hostname());
        m.put("os_type", d.osType());
        m.put("os_version", d.osVersion());
        m.put("status", resolveDisplayStatus(d));
        m.put("last_seen_at", d.lastSeenAt() != null ? d.lastSeenAt().toString() : null);
        m.put("compliance_score", d.complianceScore());
        return m;
    }

    private String resolveDisplayStatus(Device d) {
        if (d.lastSeenAt() == null) return d.status();
        boolean online = Instant.now().minus(ONLINE_THRESHOLD).isBefore(d.lastSeenAt());
        return online ? "online" : "offline";
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return values.length > 0 ? values[values.length - 1] : null;
    }
}
