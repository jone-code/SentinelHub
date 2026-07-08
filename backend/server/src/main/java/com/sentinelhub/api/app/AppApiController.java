package com.sentinelhub.api.app;

import com.sentinelhub.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * App API for mobile clients (iOS/Android management app).
 * Base path: /api/app/v1
 */
@RestController
@RequestMapping("/api/app/v1")
public class AppApiController {

    @GetMapping("/info")
    public ApiResponse<Map<String, String>> info() {
        return ApiResponse.ok(Map.of(
                "client", "app",
                "description", "移动端 API — 供手机 App 调用"
        ));
    }
}
