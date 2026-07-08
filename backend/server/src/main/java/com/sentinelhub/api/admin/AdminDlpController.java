package com.sentinelhub.api.admin;

import com.sentinelhub.api.admin.dto.CreateDlpRuleRequest;
import com.sentinelhub.api.admin.dto.UpdateDlpRuleRequest;
import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.dto.PageResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.dlp.DlpService;
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
@RequestMapping("/api/admin/v1/dlp")
public class AdminDlpController {

    private final DlpService dlpService;

    public AdminDlpController(DlpService dlpService) {
        this.dlpService = dlpService;
    }

    @GetMapping("/rules")
    public ApiResponse<List<Map<String, Object>>> listRules() {
        return ApiResponse.ok(dlpService.listRulesForAdmin(requireTenant()));
    }

    @PostMapping("/rules")
    public ApiResponse<Map<String, Object>> createRule(@Valid @RequestBody CreateDlpRuleRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(dlpService.createRule(
                ctx.tenantId(), ctx.userId(), request.name(), request.channel(), request.action(),
                request.patterns(), request.resolvedEnabled(), request.resolvedPriority()));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<Map<String, Object>> updateRule(@PathVariable String id,
                                                       @Valid @RequestBody UpdateDlpRuleRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(dlpService.updateRule(
                ctx.tenantId(), ctx.userId(), id, request.name(), request.channel(), request.action(),
                request.patterns(), request.resolvedEnabled(), request.resolvedPriority()));
    }

    @GetMapping("/events")
    public ApiResponse<PageResponse<Map<String, Object>>> listEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        String tenantId = requireTenant();
        List<Map<String, Object>> items = dlpService.listEventsForAdmin(tenantId, page, pageSize);
        return ApiResponse.ok(new PageResponse<>(items, dlpService.countEvents(tenantId), page, pageSize));
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
