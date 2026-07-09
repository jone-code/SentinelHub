package com.sentinelhub.api.admin;

import com.sentinelhub.api.admin.dto.CreateRemoteSessionRequest;
import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.dto.PageResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.remote.RemoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/remote")
public class AdminRemoteController {

    private final RemoteService remoteService;

    public AdminRemoteController(RemoteService remoteService) {
        this.remoteService = remoteService;
    }

    @GetMapping("/sessions")
    public ApiResponse<PageResponse<Map<String, Object>>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        String tenantId = requireTenant();
        List<Map<String, Object>> items = remoteService.listSessionsForAdmin(tenantId, page, pageSize);
        return ApiResponse.ok(new PageResponse<>(items, remoteService.countSessions(tenantId), page, pageSize));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<Map<String, Object>> getSession(@PathVariable String id) {
        return ApiResponse.ok(remoteService.getSessionForAdmin(requireTenant(), id));
    }

    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> createSession(@Valid @RequestBody CreateRemoteSessionRequest request) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(remoteService.createSession(
                ctx.tenantId(), ctx.userId(), request.deviceId(),
                request.reason(), request.resolvedConsentRequired()));
    }

    @PostMapping("/sessions/{id}/end")
    public ApiResponse<Map<String, Object>> endSession(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        TenantContext ctx = requireContext();
        String recordingKey = body != null ? body.get("recording_key") : null;
        return ApiResponse.ok(remoteService.endSession(ctx.tenantId(), ctx.userId(), id, recordingKey));
    }

    @PostMapping("/sessions/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancelSession(@PathVariable String id) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(remoteService.cancelSession(ctx.tenantId(), ctx.userId(), id));
    }

    @PostMapping("/sessions/{id}/signal")
    public ApiResponse<Map<String, Object>> postSignal(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        TenantContext ctx = requireContext();
        return ApiResponse.ok(remoteService.postSignaling(
                ctx.tenantId(), ctx.userId(), id,
                "admin", body.getOrDefault("sdp_type", "offer"), body.get("sdp_payload")));
    }

    @GetMapping("/sessions/{id}/signaling")
    public ApiResponse<Map<String, Object>> getClientSignal(@PathVariable String id) {
        return ApiResponse.ok(remoteService.getSignalingForRole(requireTenant(), id, "client"));
    }

    @GetMapping("/sessions/{id}/recording-url")
    public ApiResponse<Map<String, Object>> recordingUrl(@PathVariable String id) {
        return ApiResponse.ok(remoteService.getRecordingUrl(requireTenant(), id));
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
