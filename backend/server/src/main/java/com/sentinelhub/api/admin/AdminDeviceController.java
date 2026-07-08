package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.asset.AssetService;
import com.sentinelhub.module.device.DeviceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sentinelhub.common.dto.PageResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/devices")
public class AdminDeviceController {

    private final DeviceService deviceService;
    private final AssetService assetService;

    public AdminDeviceController(DeviceService deviceService, AssetService assetService) {
        this.deviceService = deviceService;
        this.assetService = assetService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> listDevices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        String tenantId = requireTenant();
        List<Map<String, Object>> items = deviceService.listDevicesForAdmin(tenantId, page, pageSize);
        int total = deviceService.countDevices(tenantId);
        return ApiResponse.ok(new PageResponse<>(items, total, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getDevice(@PathVariable String id) {
        String tenantId = requireTenant();
        return ApiResponse.ok(deviceService.getDeviceForAdmin(tenantId, id));
    }

    @GetMapping("/{id}/assets")
    public ApiResponse<Map<String, Object>> getDeviceAssets(@PathVariable String id) {
        String tenantId = requireTenant();
        return ApiResponse.ok(assetService.getDeviceAssets(tenantId, id));
    }

    private static String requireTenant() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        return ctx.tenantId();
    }
}
