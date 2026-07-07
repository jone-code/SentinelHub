package com.sentinelhub.api.agent;

import com.sentinelhub.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * Agent API for endpoint clients (Windows/macOS/Linux PC devices).
 * Base path: /agent/v1
 */
@RestController
@RequestMapping("/agent/v1")
public class AgentApiController {

    @GetMapping("/info")
    public ApiResponse<Map<String, String>> info() {
        return ApiResponse.ok(Map.of(
                "client", "agent",
                "description", "终端 Agent API — 供 PC/笔记本客户端调用"
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
}
