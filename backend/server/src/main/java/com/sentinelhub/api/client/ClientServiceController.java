package com.sentinelhub.api.client;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.module.asset.AssetService;
import com.sentinelhub.module.device.DeviceService;
import com.sentinelhub.module.identity.IdentityService;
import com.sentinelhub.module.policy.PolicyService;
import com.sentinelhub.module.software.SoftwareService;
import com.sentinelhub.module.compliance.ComplianceService;
import com.sentinelhub.module.dlp.DlpService;
import com.sentinelhub.module.nac.NacService;
import com.sentinelhub.module.zerotrust.ZerotrustService;
import com.sentinelhub.module.mdm.MdmService;
import com.sentinelhub.module.remote.RemoteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/client/v1/service")
public class ClientServiceController {

    private final IdentityService identityService;
    private final DeviceService deviceService;
    private final AssetService assetService;
    private final PolicyService policyService;
    private final SoftwareService softwareService;
    private final ComplianceService complianceService;
    private final DlpService dlpService;
    private final NacService nacService;
    private final ZerotrustService zerotrustService;
    private final MdmService mdmService;
    private final RemoteService remoteService;

    public ClientServiceController(IdentityService identityService, DeviceService deviceService,
                                   AssetService assetService, PolicyService policyService,
                                   SoftwareService softwareService, ComplianceService complianceService,
                                   DlpService dlpService, NacService nacService,
                                   ZerotrustService zerotrustService, MdmService mdmService,
                                   RemoteService remoteService) {
        this.identityService = identityService;
        this.deviceService = deviceService;
        this.assetService = assetService;
        this.policyService = policyService;
        this.softwareService = softwareService;
        this.complianceService = complianceService;
        this.dlpService = dlpService;
        this.nacService = nacService;
        this.zerotrustService = zerotrustService;
        this.mdmService = mdmService;
        this.remoteService = remoteService;
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, String>> info() {
        return ApiResponse.ok(Map.of(
                "component", "background-service",
                "description", "PC 客户端后台服务 API — 注册、心跳、上报"
        ));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        String tenantToken = stringVal(body.get("tenant_token"));
        if (tenantToken == null || tenantToken.isBlank()) {
            throw new IllegalArgumentException("tenant_token required");
        }
        String tenantId = identityService.resolveTenantByRegistrationToken(tenantToken);
        return ApiResponse.ok(deviceService.register(tenantId, tenantToken, body));
    }

    @PostMapping("/heartbeat")
    public ApiResponse<Map<String, Object>> heartbeat(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("client_id required");
        }
        return ApiResponse.ok(deviceService.heartbeatGlobal(clientId));
    }

    @GetMapping("/policy-bundle")
    public ApiResponse<Map<String, Object>> policyBundle(@RequestParam("client_id") String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("client_id required");
        }
        return ApiResponse.ok(policyService.getFullBundleForClient(clientId));
    }

    @GetMapping("/compliance-baseline")
    public ApiResponse<Map<String, Object>> complianceBaseline(@RequestParam("client_id") String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("client_id required");
        }
        return ApiResponse.ok(complianceService.getBaselineForClient(clientId));
    }

    @GetMapping("/dlp-rules")
    public ApiResponse<Map<String, Object>> dlpRules(@RequestParam("client_id") String clientId) {
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(dlpService.getRulesForClient(device.tenantId()));
    }

    @GetMapping("/nac-policy")
    public ApiResponse<Map<String, Object>> nacPolicy(@RequestParam("client_id") String clientId) {
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(nacService.getPolicyForClient(device.tenantId()));
    }

    @GetMapping("/nac-radius")
    public ApiResponse<Map<String, Object>> nacRadius(@RequestParam("client_id") String clientId) {
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(nacService.getRadiusForClient(device.tenantId()));
    }

    @GetMapping("/zt-policy")
    public ApiResponse<Map<String, Object>> ztPolicy(@RequestParam("client_id") String clientId) {
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(zerotrustService.getPolicyForClient(device.tenantId()));
    }

    @GetMapping("/mdm-profiles")
    public ApiResponse<List<Map<String, Object>>> mdmProfiles(@RequestParam("client_id") String clientId) {
        return ApiResponse.ok(mdmService.getProfilesForDevice(clientId));
    }

    @PostMapping("/report/mdm-applied")
    public ApiResponse<Map<String, Object>> reportMdmApplied(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        String profileId = stringVal(body.get("profile_id"));
        if (clientId == null || profileId == null) {
            throw new IllegalArgumentException("client_id and profile_id required");
        }
        return ApiResponse.ok(mdmService.reportAppliedForClient(clientId, profileId));
    }

    @GetMapping("/remote/active")
    public ApiResponse<Map<String, Object>> remoteActive(@RequestParam("client_id") String clientId) {
        return ApiResponse.ok(remoteService.getActiveForClient(clientId));
    }

    @PostMapping("/remote/consent")
    public ApiResponse<Map<String, Object>> remoteConsent(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        String sessionId = stringVal(body.get("session_id"));
        if (clientId == null || sessionId == null) {
            throw new IllegalArgumentException("client_id and session_id required");
        }
        boolean accepted = body.get("accepted") == null || Boolean.TRUE.equals(body.get("accepted"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("accepted")));
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(remoteService.handleConsent(
                device.tenantId(), device.deviceId(), clientId, sessionId, accepted));
    }

    @PostMapping("/remote/status")
    public ApiResponse<Map<String, Object>> remoteStatus(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        String sessionId = stringVal(body.get("session_id"));
        String status = stringVal(body.get("status"));
        if (clientId == null || sessionId == null || status == null) {
            throw new IllegalArgumentException("client_id, session_id and status required");
        }
        String recordingKey = stringVal(body.get("recording_key"));
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(remoteService.reportStatus(
                device.tenantId(), device.deviceId(), clientId, sessionId, status, recordingKey));
    }

    @PostMapping("/remote/signal")
    public ApiResponse<Map<String, Object>> remoteSignal(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        String sessionId = stringVal(body.get("session_id"));
        String sdpType = stringVal(body.get("sdp_type"));
        String sdpPayload = stringVal(body.get("sdp_payload"));
        if (clientId == null || sessionId == null || sdpPayload == null) {
            throw new IllegalArgumentException("client_id, session_id and sdp_payload required");
        }
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(remoteService.postClientSignaling(
                device.tenantId(), device.deviceId(), clientId, sessionId,
                sdpType != null ? sdpType : "answer", sdpPayload));
    }

    @GetMapping("/remote/signal")
    public ApiResponse<Map<String, Object>> getAdminSignal(
            @RequestParam("client_id") String clientId,
            @RequestParam("session_id") String sessionId) {
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(remoteService.getSignalingForRole(device.tenantId(), sessionId, "admin"));
    }

    @GetMapping("/remote/rtc-config")
    public ApiResponse<Map<String, Object>> remoteRtcConfig() {
        return ApiResponse.ok(remoteService.getRtcConfig());
    }

    @PostMapping("/remote/ice")
    public ApiResponse<Map<String, Object>> remoteIce(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        String sessionId = stringVal(body.get("session_id"));
        if (clientId == null || sessionId == null) {
            throw new IllegalArgumentException("client_id and session_id required");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> candidate = body.get("candidate") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : body;
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(remoteService.postClientIce(
                device.tenantId(), device.deviceId(), clientId, sessionId, candidate));
    }

    @GetMapping("/remote/ice")
    public ApiResponse<List<Map<String, Object>>> remoteIceList(
            @RequestParam("client_id") String clientId,
            @RequestParam("session_id") String sessionId) {
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(remoteService.listIceForRole(device.tenantId(), sessionId, "admin"));
    }

    @PostMapping("/remote/recording")
    public ApiResponse<Map<String, Object>> remoteRecording(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        String sessionId = stringVal(body.get("session_id"));
        if (clientId == null || sessionId == null) {
            throw new IllegalArgumentException("client_id and session_id required");
        }
        String contentB64 = stringVal(body.get("content_base64"));
        if (contentB64 == null) {
            throw new IllegalArgumentException("content_base64 required");
        }
        byte[] data = java.util.Base64.getDecoder().decode(contentB64);
        String contentType = stringVal(body.get("content_type"));
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(remoteService.uploadRecording(
                device.tenantId(), device.deviceId(), clientId, sessionId, data,
                contentType != null ? contentType : "application/octet-stream"));
    }

    @PostMapping("/report/dlp-evidence")
    public ApiResponse<Map<String, Object>> reportDlpEvidence(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("client_id required");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = body.get("evidence") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : body;
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(dlpService.ingestEvidence(
                device.tenantId(), device.deviceId(), clientId, evidence));
    }

    @PostMapping("/report/nac-status")
    public ApiResponse<Map<String, Object>> reportNacStatus(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("client_id required");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> status = body.get("status") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : body;
        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        return ApiResponse.ok(nacService.ingestStatus(
                device.tenantId(), device.deviceId(), clientId, status));
    }

    @PostMapping("/report/assets")
    public ApiResponse<Map<String, String>> reportAssets(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("client_id required");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> assets = body.get("assets") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        assetService.ingestReport(device.tenantId(), device.deviceId(), assets);
        return ApiResponse.ok(Map.of("status", "accepted"));
    }

    @PostMapping("/report/compliance")
    public ApiResponse<Map<String, Object>> reportCompliance(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("client_id required");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> report = body.get("report") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : body;

        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        Map<String, Object> result = complianceService.ingestScan(
                device.tenantId(), device.deviceId(), clientId, report);
        return ApiResponse.ok(result);
    }

    @PostMapping("/report/events")
    public ApiResponse<Map<String, String>> reportEvents(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("client_id required");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = body.get("events") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();

        DeviceService.OptionalDevice device = deviceService.resolveClient(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        if (!events.isEmpty()) {
            softwareService.ingestEvents(device.tenantId(), device.deviceId(), clientId, events);
        }
        return ApiResponse.ok(Map.of("status", "accepted", "count", String.valueOf(events.size())));
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString() : null;
    }
}
