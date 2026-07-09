package com.sentinelhub.api.admin;

import com.sentinelhub.api.admin.dto.AddGroupMembersRequest;
import com.sentinelhub.api.admin.dto.CreateDeviceGroupRequest;
import com.sentinelhub.api.admin.dto.CreateOrgUnitRequest;
import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.common.tenant.TenantContext;
import com.sentinelhub.module.device.DeviceGroupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1")
public class AdminDeviceGroupController {

    private final DeviceGroupService deviceGroupService;

    public AdminDeviceGroupController(DeviceGroupService deviceGroupService) {
        this.deviceGroupService = deviceGroupService;
    }

    @GetMapping("/device-groups")
    public ApiResponse<List<Map<String, Object>>> listGroups() {
        return ApiResponse.ok(deviceGroupService.listGroups(requireTenant()));
    }

    @PostMapping("/device-groups")
    public ApiResponse<Map<String, Object>> createGroup(@Valid @RequestBody CreateDeviceGroupRequest request) {
        return ApiResponse.ok(deviceGroupService.createGroup(
                requireTenant(), request.name(), request.description()));
    }

    @GetMapping("/device-groups/{id}/members")
    public ApiResponse<List<String>> listMembers(@PathVariable String id) {
        return ApiResponse.ok(deviceGroupService.listMembers(requireTenant(), id));
    }

    @PostMapping("/device-groups/{id}/members")
    public ApiResponse<Map<String, Object>> addMembers(@PathVariable String id,
                                                       @Valid @RequestBody AddGroupMembersRequest request) {
        deviceGroupService.addMembers(requireTenant(), id, request.deviceIds());
        return ApiResponse.ok(Map.of("added", request.deviceIds().size()));
    }

    @DeleteMapping("/device-groups/{groupId}/members/{deviceId}")
    public ApiResponse<Void> removeMember(@PathVariable String groupId, @PathVariable String deviceId) {
        deviceGroupService.removeMember(requireTenant(), groupId, deviceId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/org-units")
    public ApiResponse<List<Map<String, Object>>> listOrgUnits() {
        return ApiResponse.ok(deviceGroupService.listOrgUnits(requireTenant()));
    }

    @PostMapping("/org-units")
    public ApiResponse<Map<String, Object>> createOrgUnit(@Valid @RequestBody CreateOrgUnitRequest request) {
        return ApiResponse.ok(deviceGroupService.createOrgUnit(
                requireTenant(), request.name(), request.parentId()));
    }

    private static String requireTenant() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalArgumentException("unauthorized");
        }
        return ctx.tenantId();
    }
}
