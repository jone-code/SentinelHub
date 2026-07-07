package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin API for management console (PC browser).
 * Base path: /api/admin/v1
 */
@RestController
@RequestMapping("/api/admin/v1")
public class AdminApiController {

    @GetMapping("/info")
    public ApiResponse<Map<String, String>> info() {
        return ApiResponse.ok(Map.of(
                "client", "admin",
                "description", "管理端 API — 供 Web 控制台调用"
        ));
    }
}
