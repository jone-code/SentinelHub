package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.dto.PageResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.ai.AiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/ai")
public class AdminAiController {

    private final AiService aiService;

    public AdminAiController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(aiService.overviewForAdmin(requireTenant()));
    }

    @PostMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyze() {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(aiService.runAnalysis(ctx.tenantId(), ctx.userId()));
    }

    @GetMapping("/insights")
    public ApiResponse<PageResponse<Map<String, Object>>> listInsights(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        String tenantId = requireTenant();
        List<Map<String, Object>> items = aiService.listInsightsForAdmin(tenantId, status, page, pageSize);
        return ApiResponse.ok(new PageResponse<>(items, aiService.countInsights(tenantId, status), page, pageSize));
    }

    @PostMapping("/insights/{id}/resolve")
    public ApiResponse<Map<String, Object>> resolve(@PathVariable String id) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(aiService.resolveInsight(ctx.tenantId(), ctx.userId(), id));
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
