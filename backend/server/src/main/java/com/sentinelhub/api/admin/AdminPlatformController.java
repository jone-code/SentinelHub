package com.sentinelhub.api.admin;

import com.sentinelhub.api.admin.dto.UpdatePlanTierRequest;
import com.sentinelhub.clickhouse.ClickHouseSchemaMigrationService;
import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.audit.NatsConsumerMetrics;
import com.sentinelhub.module.software.AdminWebSocketSessionRegistry;
import com.sentinelhub.module.software.WebSocketPlanQuotaService;
import com.sentinelhub.module.tenant.TenantPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final TenantPlanService tenantPlanService;

    public AdminPlatformController(NatsConsumerMetrics natsConsumerMetrics,
                                   AdminWebSocketSessionRegistry webSocketSessionRegistry,
                                   ClickHouseSchemaMigrationService clickHouseMigrationService,
                                   WebSocketPlanQuotaService webSocketPlanQuotaService,
                                   TenantPlanService tenantPlanService) {
        this.natsConsumerMetrics = natsConsumerMetrics;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.clickHouseMigrationService = clickHouseMigrationService;
        this.webSocketPlanQuotaService = webSocketPlanQuotaService;
        this.tenantPlanService = tenantPlanService;
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

    @PutMapping("/plan-tier")
    public ApiResponse<Map<String, Object>> updatePlanTier(@RequestBody UpdatePlanTierRequest request) {
        TenantContext ctx = requireTenant();
        if (request == null || request.planTier() == null) {
            throw new IllegalArgumentException("plan_tier is required");
        }
        Map<String, Object> updated = tenantPlanService.updatePlanTier(ctx.tenantId(), request.planTier());
        updated.putAll(webSocketPlanQuotaService.quotaSnapshot(ctx.tenantId()));
        return ApiResponse.ok(updated);
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
