package com.sentinelhub.common.web;

import com.sentinelhub.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Shared health check endpoints.
 */
@RestController
public class HealthController {

    private final String serviceName;

    public HealthController(String serviceName) {
        this.serviceName = serviceName;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "service", serviceName,
                "status", "healthy"
        )));
    }

    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<Map<String, String>>> ready() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "ready")));
    }
}
