package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.dto.PageResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.asset.AssetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/assets")
public class AdminAssetController {

    private final AssetService assetService;

    public AdminAssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(assetService.getTenantOverview(requireTenant()));
    }

    @GetMapping("/software")
    public ApiResponse<PageResponse<Map<String, Object>>> listSoftware(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        String tenantId = requireTenant();
        List<Map<String, Object>> items = assetService.listSoftwareInventory(tenantId, page, pageSize);
        return ApiResponse.ok(new PageResponse<>(items, assetService.countSoftwareInventory(tenantId), page, pageSize));
    }

    @GetMapping("/hardware")
    public ApiResponse<PageResponse<Map<String, Object>>> listHardware(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        String tenantId = requireTenant();
        List<Map<String, Object>> items = assetService.listHardwareInventory(tenantId, page, pageSize);
        return ApiResponse.ok(new PageResponse<>(items, assetService.countHardwareInventory(tenantId), page, pageSize));
    }

    private static String requireTenant() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        return ctx.tenantId();
    }
}
