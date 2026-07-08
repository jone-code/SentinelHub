package com.sentinelhub.api.client;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.module.asset.AssetService;
import com.sentinelhub.module.device.DeviceService;
import com.sentinelhub.module.identity.IdentityService;
import com.sentinelhub.module.policy.PolicyService;
import com.sentinelhub.module.software.SoftwareService;
import com.sentinelhub.module.compliance.ComplianceService;
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

    public ClientServiceController(IdentityService identityService, DeviceService deviceService,
                                   AssetService assetService, PolicyService policyService,
                                   SoftwareService softwareService, ComplianceService complianceService) {
        this.identityService = identityService;
        this.deviceService = deviceService;
        this.assetService = assetService;
        this.policyService = policyService;
        this.softwareService = softwareService;
        this.complianceService = complianceService;
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
