package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.dto.PageResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.audit.SecurityTimelineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/timeline")
public class AdminTimelineController {

    private final SecurityTimelineService timelineService;

    public AdminTimelineController(SecurityTimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "auto") String storage) {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        String tenantId = ctx.tenantId();
        List<Map<String, Object>> items = timelineService.listForAdmin(tenantId, page, pageSize, source, storage);
        int total = timelineService.count(tenantId, source, storage);
        return ApiResponse.ok(new PageResponse<>(items, total, page, pageSize));
    }
}
