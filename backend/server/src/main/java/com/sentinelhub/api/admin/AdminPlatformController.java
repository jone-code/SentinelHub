package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.audit.NatsConsumerMetrics;
import com.sentinelhub.module.software.AdminWebSocketSessionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/platform")
public class AdminPlatformController {

    private final NatsConsumerMetrics natsConsumerMetrics;
    private final AdminWebSocketSessionRegistry webSocketSessionRegistry;

    public AdminPlatformController(NatsConsumerMetrics natsConsumerMetrics,
                                   AdminWebSocketSessionRegistry webSocketSessionRegistry) {
        this.natsConsumerMetrics = natsConsumerMetrics;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    @GetMapping("/nats-metrics")
    public ApiResponse<Map<String, Object>> natsMetrics() {
        requireTenant();
        return ApiResponse.ok(natsConsumerMetrics.snapshot());
    }

    @GetMapping("/ws-stats")
    public ApiResponse<Map<String, Object>> wsStats() {
        requireTenant();
        return ApiResponse.ok(webSocketSessionRegistry.stats());
    }

    @GetMapping("/metrics-summary")
    public ApiResponse<Map<String, Object>> metricsSummary() {
        requireTenant();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("nats", natsConsumerMetrics.snapshot());
        summary.put("websocket", webSocketSessionRegistry.stats());
        summary.put("prometheus_endpoint", "/actuator/prometheus");
        return ApiResponse.ok(summary);
    }

    private static void requireTenant() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
    }
}
