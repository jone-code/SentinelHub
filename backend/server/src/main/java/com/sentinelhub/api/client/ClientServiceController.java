package com.sentinelhub.api.client;

import com.sentinelhub.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * PC client background service API — register, heartbeat, report.
 * Base path: /api/client/v1/service
 */
@RestController
@RequestMapping("/api/client/v1/service")
public class ClientServiceController {

    @GetMapping("/info")
    public ApiResponse<Map<String, String>> info() {
        return ApiResponse.ok(Map.of(
                "component", "background-service",
                "description", "PC 客户端后台服务 API — 注册、心跳、上报"
        ));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, String>> register(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(Map.of(
                "status", "pending",
                "message", "registration endpoint ready"
        ));
    }

    @PostMapping("/heartbeat")
    public ApiResponse<Map<String, Object>> heartbeat(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(Map.of(
                "server_time", java.time.Instant.now().toString(),
                "commands", Collections.emptyList()
        ));
    }

    @PostMapping("/report/assets")
    public ApiResponse<Map<String, String>> reportAssets(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(Map.of("status", "accepted"));
    }

    @PostMapping("/report/events")
    public ApiResponse<Map<String, String>> reportEvents(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(Map.of("status", "accepted"));
    }
}
