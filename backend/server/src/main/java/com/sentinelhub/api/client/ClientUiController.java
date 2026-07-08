package com.sentinelhub.api.client;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.module.compliance.ComplianceService;
import com.sentinelhub.module.device.DeviceService;
import com.sentinelhub.module.zerotrust.ZerotrustService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/client/v1")
public class ClientUiController {

    private final DeviceService deviceService;
    private final ComplianceService complianceService;
    private final ZerotrustService zerotrustService;

    public ClientUiController(DeviceService deviceService, ComplianceService complianceService,
                              ZerotrustService zerotrustService) {
        this.deviceService = deviceService;
        this.complianceService = complianceService;
        this.zerotrustService = zerotrustService;
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, String>> info() {
        return ApiResponse.ok(Map.of(
                "client", "pc-desktop",
                "description", "PC 安全客户端 UI API",
                "version", "0.1.0"
        ));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return ApiResponse.ok(Map.of(
                    "compliance_score", 0,
                    "trust_score", 0,
                    "service_running", false,
                    "pending_items", 0,
                    "unread_notifications", 0
            ));
        }
        return ApiResponse.ok(deviceService.getUiStatus(clientId));
    }

    @GetMapping("/compliance")
    public ApiResponse<Map<String, Object>> compliance(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return ApiResponse.ok(Map.of("score", 0, "items", java.util.List.of()));
        }
        return ApiResponse.ok(complianceService.getComplianceForClient(clientId));
    }

    @GetMapping("/trust")
    public ApiResponse<Map<String, Object>> trust(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return ApiResponse.ok(Map.of("trust_score", 0, "trust_level", "unknown", "protected_apps", List.of()));
        }
        return ApiResponse.ok(zerotrustService.getTrustForClient(clientId));
    }
}
