package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.dto.PageResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.software.SoftwareService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/events")
public class AdminEventController {

    private final SoftwareService softwareService;

    public AdminEventController(SoftwareService softwareService) {
        this.softwareService = softwareService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "event_type", required = false) String eventType,
            @RequestParam(required = false) String severity) {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        List<Map<String, Object>> items = softwareService.listEventsForAdmin(
                ctx.tenantId(), page, pageSize, eventType, severity);
        int total = softwareService.countEvents(ctx.tenantId(), eventType, severity);
        return ApiResponse.ok(new PageResponse<>(items, total, page, pageSize));
    }
}
