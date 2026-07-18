package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.audit.NatsConsumerMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/platform")
public class AdminPlatformController {

    private final NatsConsumerMetrics natsConsumerMetrics;

    public AdminPlatformController(NatsConsumerMetrics natsConsumerMetrics) {
        this.natsConsumerMetrics = natsConsumerMetrics;
    }

    @GetMapping("/nats-metrics")
    public ApiResponse<Map<String, Object>> natsMetrics() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        return ApiResponse.ok(natsConsumerMetrics.snapshot());
    }
}
