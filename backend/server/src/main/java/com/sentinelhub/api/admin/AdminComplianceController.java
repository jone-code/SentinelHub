package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.dto.PageResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.compliance.ComplianceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/compliance")
public class AdminComplianceController {

    private final ComplianceService complianceService;

    public AdminComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(complianceService.overviewForAdmin(requireTenant()));
    }

    @GetMapping("/results")
    public ApiResponse<PageResponse<Map<String, Object>>> results(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        String tenantId = requireTenant();
        List<Map<String, Object>> items = complianceService.listForAdmin(tenantId, page, pageSize);
        int total = complianceService.overviewForAdmin(tenantId).get("device_scanned") instanceof Number n
                ? n.intValue() : items.size();
        return ApiResponse.ok(new PageResponse<>(items, total, page, pageSize));
    }

    private static String requireTenant() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        return ctx.tenantId();
    }
}
