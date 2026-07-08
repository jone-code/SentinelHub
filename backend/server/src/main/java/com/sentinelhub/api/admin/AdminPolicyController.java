package com.sentinelhub.api.admin;

import com.sentinelhub.api.admin.dto.CreatePolicyRequest;
import com.sentinelhub.api.admin.dto.UpdatePolicyRequest;
import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.policy.PolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/policies")
public class AdminPolicyController {

    private final PolicyService policyService;

    public AdminPolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(policyService.listPolicies(requireTenant()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        return ApiResponse.ok(policyService.getPolicy(requireTenant(), id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreatePolicyRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(policyService.createPolicy(
                ctx.tenantId(), ctx.userId(), request.name(), request.type(),
                request.content(), request.resolvedPriority()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id,
                                                   @Valid @RequestBody UpdatePolicyRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(policyService.updatePolicy(
                ctx.tenantId(), ctx.userId(), id, request.name(),
                request.content(), request.resolvedPriority()));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable String id) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(policyService.publishPolicy(ctx.tenantId(), ctx.userId(), id));
    }

    @GetMapping("/bundle/active")
    public ApiResponse<Map<String, Object>> activeBundle() {
        String tenantId = requireTenant();
        return policyService.listPolicies(tenantId).isEmpty()
                ? ApiResponse.ok(Map.of())
                : ApiResponse.ok(Map.of("message", "use client policy-bundle endpoint for full content"));
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
