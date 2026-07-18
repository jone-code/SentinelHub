package com.sentinelhub.api.admin;

import com.sentinelhub.clickhouse.ClickHouseSchemaMigrationService;
import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.audit.NatsConsumerMetrics;
import com.sentinelhub.module.software.AdminWebSocketSessionRegistry;
import com.sentinelhub.module.software.WebSocketPlanQuotaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/platform")
public class AdminPlatformController {

    private final NatsConsumerMetrics natsConsumerMetrics;
    private final AdminWebSocketSessionRegistry webSocketSessionRegistry;
    private final ClickHouseSchemaMigrationService clickHouseMigrationService;
    private final WebSocketPlanQuotaService webSocketPlanQuotaService;

    public AdminPlatformController(NatsConsumerMetrics natsConsumerMetrics,
                                   AdminWebSocketSessionRegistry webSocketSessionRegistry,
                                   ClickHouseSchemaMigrationService clickHouseMigrationService,
                                   WebSocketPlanQuotaService webSocketPlanQuotaService) {
        this.natsConsumerMetrics = natsConsumerMetrics;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.clickHouseMigrationService = clickHouseMigrationService;
        this.webSocketPlanQuotaService = webSocketPlanQuotaService;
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
        summary.put("clickhouse_migration", clickHouseMigrationService.snapshot());
        summary.put("prometheus_endpoint", "/actuator/prometheus");
        return ApiResponse.ok(summary);
    }

    @GetMapping("/clickhouse-migration")
    public ApiResponse<Map<String, Object>> clickHouseMigration() {
        requireTenant();
        return ApiResponse.ok(clickHouseMigrationService.snapshot());
    }

    @PostMapping("/clickhouse-migration/run")
    public ApiResponse<Map<String, Object>> runClickHouseMigration() {
        requireTenant();
        clickHouseMigrationService.runMigration("api");
        return ApiResponse.ok(clickHouseMigrationService.snapshot());
    }

    @GetMapping("/ws-plan-quota")
    public ApiResponse<Map<String, Object>> wsPlanQuota() {
        TenantContext ctx = requireTenant();
        Map<String, Object> out = new LinkedHashMap<>(webSocketPlanQuotaService.quotaSnapshot(ctx.tenantId()));
        out.put("tier_catalog", webSocketPlanQuotaService.allTierQuotas());
        return ApiResponse.ok(out);
    }

    private static TenantContext requireTenant() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        return ctx;
    }
}
