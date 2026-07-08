package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.device.DeviceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/dashboard")
public class AdminDashboardController {

    private final DeviceService deviceService;

    public AdminDashboardController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        int total = deviceService.countDevices(ctx.tenantId());
        return ApiResponse.ok(Map.of(
                "device_total", total,
                "device_online", 0,
                "alert_open", 0,
                "compliance_avg", 0
        ));
    }
}
