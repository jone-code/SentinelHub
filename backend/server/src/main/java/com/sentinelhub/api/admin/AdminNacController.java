package com.sentinelhub.api.admin;

import com.sentinelhub.api.admin.dto.UpdateNacPolicyRequest;
import com.sentinelhub.api.admin.dto.UpdateNacRadiusRequest;
import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.dto.PageResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.nac.NacService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/nac")
public class AdminNacController {

    private final NacService nacService;

    public AdminNacController(NacService nacService) {
        this.nacService = nacService;
    }

    @GetMapping("/policy")
    public ApiResponse<Map<String, Object>> getPolicy() {
        return ApiResponse.ok(nacService.getPolicyForAdmin(requireTenant()));
    }

    @PutMapping("/policy")
    public ApiResponse<Map<String, Object>> updatePolicy(@Valid @RequestBody UpdateNacPolicyRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(nacService.updatePolicy(
                ctx.tenantId(), ctx.userId(), request.name(), request.minComplianceScore(),
                request.actionOnFail(), request.isolateVlan(), request.resolvedEnabled()));
    }

    @GetMapping("/devices")
    public ApiResponse<PageResponse<Map<String, Object>>> listDeviceStatus(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        String tenantId = requireTenant();
        List<Map<String, Object>> items = nacService.listDeviceStatusForAdmin(tenantId, page, pageSize);
        return ApiResponse.ok(new PageResponse<>(items, nacService.countDeviceStatus(tenantId), page, pageSize));
    }

    @GetMapping("/radius")
    public ApiResponse<Map<String, Object>> getRadius() {
        return ApiResponse.ok(nacService.getRadiusForAdmin(requireTenant()));
    }

    @PutMapping("/radius")
    public ApiResponse<Map<String, Object>> updateRadius(@Valid @RequestBody UpdateNacRadiusRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(nacService.updateRadius(
                ctx.tenantId(), ctx.userId(), request.resolvedEnabled(),
                request.serverHost() != null ? request.serverHost() : "radius.example.local",
                request.resolvedAuthPort(), request.resolvedAcctPort(), request.secret(),
                request.nasIdentifier(), request.vlanAllowed(), request.vlanRestricted(), request.vlanDenied()));
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
