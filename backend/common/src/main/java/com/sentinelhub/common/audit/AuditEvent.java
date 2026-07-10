package com.sentinelhub.common.audit;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical audit event schema used across all modules.
 */
public record AuditEvent(
        String eventId,
        String tenantId,
        Instant timestamp,
        String eventType,
        String actorType,
        String actorId,
        String resourceType,
        String resourceId,
        String deviceId,
        String action,
        String result,
        String clientIp,
        Map<String, Object> metadata
) {
    public static final String EVENT_DEVICE_REGISTERED = "device.registered";
    public static final String EVENT_POLICY_PUBLISHED = "policy.published";
    public static final String EVENT_SOFTWARE_BLOCKED = "software.blocked";
    public static final String EVENT_DLP_BLOCKED = "dlp.blocked";
    public static final String EVENT_COMPLIANCE_FAILED = "compliance.failed";
    public static final String EVENT_NAC_DENIED = "nac.denied";
    public static final String EVENT_REMOTE_SESSION = "remote.session";
    public static final String EVENT_DRIVER_FILE_BLOCKED = "driver.file_blocked";
    public static final String EVENT_DRIVER_FILE_OPEN = "driver.file_open";
    public static final String EVENT_DRIVER_PROCESS_BLOCKED = "driver.process_blocked";
    public static final String EVENT_DRIVER_PROCESS_EXEC = "driver.process_exec";
}
