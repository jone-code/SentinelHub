package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.ai.AiService;
import com.sentinelhub.module.compliance.ComplianceService;
import com.sentinelhub.module.device.DeviceRepository;
import com.sentinelhub.module.device.DeviceService;
import com.sentinelhub.module.remote.RemoteRepository;
import com.sentinelhub.module.software.SoftwareService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/dashboard")
public class AdminDashboardController {

    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;
    private final SoftwareService softwareService;
    private final ComplianceService complianceService;
    private final AiService aiService;
    private final RemoteRepository remoteRepository;

    public AdminDashboardController(DeviceService deviceService, DeviceRepository deviceRepository,
                                    SoftwareService softwareService, ComplianceService complianceService,
                                    AiService aiService, RemoteRepository remoteRepository) {
        this.deviceService = deviceService;
        this.deviceRepository = deviceRepository;
        this.softwareService = softwareService;
        this.complianceService = complianceService;
        this.aiService = aiService;
        this.remoteRepository = remoteRepository;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        String tenantId = ctx.tenantId();
        Map<String, Object> base = deviceService.dashboardSummary(tenantId);
        Map<String, Object> compliance = complianceService.overviewForAdmin(tenantId);

        Map<String, Object> result = new HashMap<>();
        result.put("device_total", base.get("device_total"));
        result.put("device_online", base.get("device_online"));
        result.put("alert_open", softwareService.countRecentAlerts(tenantId));
        result.put("compliance_avg", compliance.get("average_score"));
        result.put("trust_avg", Math.round(deviceRepository.averageTrustScore(tenantId)));
        result.put("ai_open", aiService.countInsights(tenantId, "open"));
        result.put("remote_active", remoteRepository.countActiveByTenant(tenantId));
        result.put("recent_insights", aiService.listInsightsForAdmin(tenantId, "open", 1, 5));
        return ApiResponse.ok(result);
    }
}
