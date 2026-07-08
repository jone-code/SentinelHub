package com.sentinelhub.api.client;

import com.sentinelhub.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * PC client UI API — for desktop application pages.
 * Base path: /api/client/v1
 */
@RestController
@RequestMapping("/api/client/v1")
public class ClientUiController {

    @GetMapping("/info")
    public ApiResponse<Map<String, String>> info() {
        return ApiResponse.ok(Map.of(
                "client", "pc-desktop",
                "description", "PC 安全客户端 UI API — 供桌面客户端界面调用"
        ));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "compliance_score", 0,
                "trust_score", 0,
                "service_running", true,
                "pending_items", 0,
                "unread_notifications", 0
        ));
    }

    @GetMapping("/compliance")
    public ApiResponse<Map<String, Object>> compliance() {
        return ApiResponse.ok(Map.of(
                "score", 0,
                "items", java.util.Collections.emptyList()
        ));
    }
}
