package com.sentinelhub.module.device;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeviceGroupService {

    private final DeviceScopeRepository deviceScopeRepository;

    public DeviceGroupService(DeviceScopeRepository deviceScopeRepository) {
        this.deviceScopeRepository = deviceScopeRepository;
    }

    public List<Map<String, Object>> listGroups(String tenantId) {
        return deviceScopeRepository.listGroups(tenantId).stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.id());
            m.put("name", g.name());
            m.put("description", g.description());
            m.put("member_count", g.memberCount());
            m.put("created_at", g.createdAt());
            return m;
        }).toList();
    }

    public Map<String, Object> createGroup(String tenantId, String name, String description) {
        String id = deviceScopeRepository.createGroup(tenantId, name, description);
        return Map.of("id", id, "name", name);
    }

    public void addMembers(String tenantId, String groupId, List<String> deviceIds) {
        deviceScopeRepository.addDevicesToGroup(tenantId, groupId, deviceIds);
    }

    public void removeMember(String tenantId, String groupId, String deviceId) {
        deviceScopeRepository.removeDeviceFromGroup(tenantId, groupId, deviceId);
    }

    public List<String> listMembers(String tenantId, String groupId) {
        return deviceScopeRepository.findDeviceIdsInGroup(tenantId, groupId);
    }

    public List<Map<String, Object>> listOrgUnits(String tenantId) {
        return deviceScopeRepository.listOrgUnits(tenantId).stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.id());
            m.put("parent_id", o.parentId());
            m.put("name", o.name());
            m.put("created_at", o.createdAt());
            return m;
        }).toList();
    }

    public Map<String, Object> createOrgUnit(String tenantId, String name, String parentId) {
        String id = deviceScopeRepository.createOrgUnit(tenantId, name, parentId);
        return Map.of("id", id, "name", name);
    }

    public void assignDeviceOrgUnit(String tenantId, String deviceId, String orgUnitId) {
        deviceScopeRepository.assignDeviceOrgUnit(tenantId, deviceId, orgUnitId);
    }
}
