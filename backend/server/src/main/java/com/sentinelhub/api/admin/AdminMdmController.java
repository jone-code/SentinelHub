package com.sentinelhub.api.admin;

import com.sentinelhub.api.admin.dto.AssignMdmProfileRequest;
import com.sentinelhub.api.admin.dto.CreateMdmProfileRequest;
import com.sentinelhub.api.admin.dto.UpdateMdmProfileRequest;
import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.dto.PageResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.mdm.MdmService;
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
@RequestMapping("/api/admin/v1/mdm")
public class AdminMdmController {

    private final MdmService mdmService;

    public AdminMdmController(MdmService mdmService) {
        this.mdmService = mdmService;
    }

    @GetMapping("/profiles")
    public ApiResponse<List<Map<String, Object>>> listProfiles() {
        return ApiResponse.ok(mdmService.listProfilesForAdmin(requireTenant()));
    }

    @PostMapping("/profiles")
    public ApiResponse<Map<String, Object>> createProfile(@Valid @RequestBody CreateMdmProfileRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(mdmService.createProfile(
                ctx.tenantId(), ctx.userId(), request.name(), request.profileType(),
                request.content(), request.resolvedEnabled()));
    }

    @PutMapping("/profiles/{id}")
    public ApiResponse<Map<String, Object>> updateProfile(
            @PathVariable String id,
            @Valid @RequestBody UpdateMdmProfileRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(mdmService.updateProfile(
                ctx.tenantId(), ctx.userId(), id, request.name(), request.profileType(),
                request.content(), request.resolvedEnabled()));
    }

    @PostMapping("/assignments")
    public ApiResponse<Map<String, Object>> assignProfile(@Valid @RequestBody AssignMdmProfileRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(mdmService.assignProfile(
                ctx.tenantId(), ctx.userId(), request.deviceId(), request.profileId()));
    }

    @GetMapping("/assignments")
    public ApiResponse<PageResponse<Map<String, Object>>> listAssignments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        String tenantId = requireTenant();
        List<Map<String, Object>> items = mdmService.listAssignmentsForAdmin(tenantId, page, pageSize);
        return ApiResponse.ok(new PageResponse<>(items, mdmService.countAssignments(tenantId), page, pageSize));
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
