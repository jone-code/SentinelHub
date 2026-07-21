package com.sentinelhub.api.admin;

import com.sentinelhub.api.admin.dto.PlanChangeReviewRequest;
import com.sentinelhub.api.admin.dto.UpdatePlanTierRequest;
import com.sentinelhub.clickhouse.ClickHouseMigrationTaskService;
import com.sentinelhub.clickhouse.ClickHouseSchemaMigrationService;
import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.audit.NatsConsumerMetrics;
import com.sentinelhub.module.software.AdminWebSocketSessionRegistry;
import com.sentinelhub.module.software.WebSocketPlanQuotaService;
import com.sentinelhub.module.platform.PrometheusMetricsService;
import com.sentinelhub.module.tenant.TenantPlanChangeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/platform")
public class AdminPlatformController {

    private final NatsConsumerMetrics natsConsumerMetrics;
    private final AdminWebSocketSessionRegistry webSocketSessionRegistry;
    private final ClickHouseSchemaMigrationService clickHouseMigrationService;
    private final ClickHouseMigrationTaskService clickHouseMigrationTaskService;
    private final WebSocketPlanQuotaService webSocketPlanQuotaService;
    private final TenantPlanChangeService tenantPlanChangeService;
    private final PrometheusMetricsService prometheusMetricsService;

    public AdminPlatformController(NatsConsumerMetrics natsConsumerMetrics,
                                   AdminWebSocketSessionRegistry webSocketSessionRegistry,
                                   ClickHouseSchemaMigrationService clickHouseMigrationService,
                                   ClickHouseMigrationTaskService clickHouseMigrationTaskService,
                                   WebSocketPlanQuotaService webSocketPlanQuotaService,
                                   TenantPlanChangeService tenantPlanChangeService,
                                   PrometheusMetricsService prometheusMetricsService) {
        this.natsConsumerMetrics = natsConsumerMetrics;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.clickHouseMigrationService = clickHouseMigrationService;
        this.clickHouseMigrationTaskService = clickHouseMigrationTaskService;
        this.webSocketPlanQuotaService = webSocketPlanQuotaService;
        this.tenantPlanChangeService = tenantPlanChangeService;
        this.prometheusMetricsService = prometheusMetricsService;
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
        return ApiResponse.ok(clickHouseMigrationTaskService.submit("api"));
    }

    @PostMapping("/plan-tier/requests")
    public ApiResponse<Map<String, Object>> submitPlanChange(@RequestBody UpdatePlanTierRequest request) {
        TenantContext ctx = requireTenant();
        if (request == null || request.planTier() == null) {
            throw new IllegalArgumentException("plan_tier is required");
        }
        Map<String, Object> result = tenantPlanChangeService.submitRequest(
                ctx.tenantId(), ctx.userId(), request.planTier());
        if ("applied".equals(result.get("status"))) {
            result.putAll(webSocketPlanQuotaService.quotaSnapshot(ctx.tenantId()));
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/plan-tier/requests")
    public ApiResponse<List<Map<String, Object>>> listPlanChangeRequests(
            @RequestParam(required = false) String status) {
        TenantContext ctx = requireTenant();
        return ApiResponse.ok(tenantPlanChangeService.listRequests(ctx.tenantId(), status));
    }

    @PostMapping("/plan-tier/requests/{id}/approve")
    public ApiResponse<Map<String, Object>> approvePlanChange(
            @PathVariable String id,
            @RequestBody(required = false) PlanChangeReviewRequest request) {
        TenantContext ctx = requireTenant();
        String note = request != null ? request.reviewNote() : null;
        Map<String, Object> result = tenantPlanChangeService.approve(ctx.tenantId(), ctx.userId(), id, note);
        if ("approved".equals(result.get("status"))) {
            result.putAll(webSocketPlanQuotaService.quotaSnapshot(ctx.tenantId()));
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/plan-tier/requests/{id}/reject")
    public ApiResponse<Map<String, Object>> rejectPlanChange(
            @PathVariable String id,
            @RequestBody(required = false) PlanChangeReviewRequest request) {
        TenantContext ctx = requireTenant();
        String note = request != null ? request.reviewNote() : null;
        return ApiResponse.ok(tenantPlanChangeService.reject(ctx.tenantId(), ctx.userId(), id, note));
    }

    @PutMapping("/plan-tier")
    public ApiResponse<Map<String, Object>> updatePlanTier(@RequestBody UpdatePlanTierRequest request) {
        TenantContext ctx = requireTenant();
        if (request == null || request.planTier() == null) {
            throw new IllegalArgumentException("plan_tier is required");
        }
        Map<String, Object> result = tenantPlanChangeService.submitRequest(
                ctx.tenantId(), ctx.userId(), request.planTier());
        if ("applied".equals(result.get("status"))) {
            result.putAll(webSocketPlanQuotaService.quotaSnapshot(ctx.tenantId()));
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/prometheus-metrics")
    public ApiResponse<Map<String, Object>> prometheusMetrics() {
        requireTenant();
        Map<String, Object> out = new LinkedHashMap<>(prometheusMetricsService.snapshot());
        out.put("chart_point", prometheusMetricsService.chartPoint());
        return ApiResponse.ok(out);
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
