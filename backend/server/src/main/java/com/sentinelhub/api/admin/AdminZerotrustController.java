package com.sentinelhub.api.admin;

import com.sentinelhub.api.admin.dto.CreateZtProtectedAppRequest;
import com.sentinelhub.api.admin.dto.UpdateZtPolicyRequest;
import com.sentinelhub.api.admin.dto.UpdateZtProtectedAppRequest;
import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.dto.PageResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.zerotrust.ZerotrustService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/zerotrust")
public class AdminZerotrustController {

    private final ZerotrustService zerotrustService;

    public AdminZerotrustController(ZerotrustService zerotrustService) {
        this.zerotrustService = zerotrustService;
    }

    @GetMapping("/policy")
    public ApiResponse<Map<String, Object>> getPolicy() {
        return ApiResponse.ok(zerotrustService.getPolicyForAdmin(requireTenant()));
    }

    @PutMapping("/policy")
    public ApiResponse<Map<String, Object>> updatePolicy(@Valid @RequestBody UpdateZtPolicyRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(zerotrustService.updatePolicy(
                ctx.tenantId(), ctx.userId(), request.name(),
                request.complianceWeight(), request.nacWeight(), request.eventWeight(),
                request.minTrustScore(), request.resolvedEnabled()));
    }

    @GetMapping("/protected-apps")
    public ApiResponse<List<Map<String, Object>>> listProtectedApps() {
        return ApiResponse.ok(zerotrustService.listProtectedAppsForAdmin(requireTenant()));
    }

    @PostMapping("/protected-apps")
    public ApiResponse<Map<String, Object>> createProtectedApp(
            @Valid @RequestBody CreateZtProtectedAppRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(zerotrustService.createProtectedApp(
                ctx.tenantId(), ctx.userId(), request.name(), request.appIdentifier(),
                request.minTrustScore(), request.resolvedEnabled()));
    }

    @PutMapping("/protected-apps/{id}")
    public ApiResponse<Map<String, Object>> updateProtectedApp(
            @PathVariable String id,
            @Valid @RequestBody UpdateZtProtectedAppRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(zerotrustService.updateProtectedApp(
                ctx.tenantId(), ctx.userId(), id, request.name(), request.appIdentifier(),
                request.minTrustScore(), request.resolvedEnabled()));
    }

    @GetMapping("/devices")
    public ApiResponse<PageResponse<Map<String, Object>>> listDeviceTrust(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        String tenantId = requireTenant();
        List<Map<String, Object>> items = zerotrustService.listDeviceTrustForAdmin(tenantId, page, pageSize);
        return ApiResponse.ok(new PageResponse<>(items, zerotrustService.countDevices(tenantId), page, pageSize));
    }

    @GetMapping("/devices/{deviceId}/history")
    public ApiResponse<List<Map<String, Object>>> trustHistory(@PathVariable String deviceId) {
        return ApiResponse.ok(zerotrustService.listTrustHistory(requireTenant(), deviceId));
    }

    private static String requireTenant() {
        return requireContext().tenantId();
    }

    private static TenantContext requireContext() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        return ctx;
    }
}
