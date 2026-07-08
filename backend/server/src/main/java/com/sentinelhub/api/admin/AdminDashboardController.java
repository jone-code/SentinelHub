package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.compliance.ComplianceService;
import com.sentinelhub.module.device.DeviceService;
import com.sentinelhub.module.software.SoftwareService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/dashboard")
public class AdminDashboardController {

    private final DeviceService deviceService;
    private final SoftwareService softwareService;
    private final ComplianceService complianceService;

    public AdminDashboardController(DeviceService deviceService, SoftwareService softwareService,
                                    ComplianceService complianceService) {
        this.deviceService = deviceService;
        this.softwareService = softwareService;
        this.complianceService = complianceService;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        Map<String, Object> base = deviceService.dashboardSummary(ctx.tenantId());
        Map<String, Object> compliance = complianceService.overviewForAdmin(ctx.tenantId());
        return ApiResponse.ok(Map.of(
                "device_total", base.get("device_total"),
                "device_online", base.get("device_online"),
                "alert_open", softwareService.countRecentAlerts(ctx.tenantId()),
                "compliance_avg", compliance.get("average_score")
        ));
    }
}
