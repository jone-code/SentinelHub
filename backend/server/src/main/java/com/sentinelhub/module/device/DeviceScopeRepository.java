package com.sentinelhub.module.device;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class DeviceScopeRepository {

    private final JdbcTemplate jdbc;

    public DeviceScopeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> findGroupIdsForDevice(String deviceId) {
        List<String> ids = jdbc.queryForList(
                "SELECT device_group_id FROM device_group_members WHERE device_id = ?",
                String.class, deviceId);
        return new HashSet<>(ids);
    }

    public List<String> findDeviceIdsInGroup(String tenantId, String groupId) {
        return jdbc.queryForList(
                "SELECT m.device_id FROM device_group_members m "
                        + "JOIN device_groups g ON g.id = m.device_group_id "
                        + "WHERE g.tenant_id = ? AND g.id = ?",
                String.class, tenantId, groupId);
    }

    public void addDevicesToGroup(String tenantId, String groupId, List<String> deviceIds) {
        for (String deviceId : deviceIds) {
            jdbc.update(
                    "INSERT IGNORE INTO device_group_members (device_group_id, device_id) "
                            + "SELECT g.id, d.id FROM device_groups g, devices d "
                            + "WHERE g.tenant_id = ? AND g.id = ? AND d.tenant_id = ? AND d.id = ?",
                    tenantId, groupId, tenantId, deviceId);
        }
    }

    public void removeDeviceFromGroup(String tenantId, String groupId, String deviceId) {
        jdbc.update(
                "DELETE m FROM device_group_members m "
                        + "JOIN device_groups g ON g.id = m.device_group_id "
                        + "WHERE g.tenant_id = ? AND g.id = ? AND m.device_id = ?",
                tenantId, groupId, deviceId);
    }

    public String createGroup(String tenantId, String name, String description) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO device_groups (id, tenant_id, name, description) VALUES (?,?,?,?)",
                id, tenantId, name, description);
        return id;
    }

    public List<MapRow> listGroups(String tenantId) {
        return jdbc.query(
                "SELECT g.id, g.name, g.description, g.created_at, "
                        + "(SELECT COUNT(*) FROM device_group_members m WHERE m.device_group_id = g.id) AS member_count "
                        + "FROM device_groups g WHERE g.tenant_id = ? ORDER BY g.name",
                (rs, rowNum) -> new MapRow(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("member_count"),
                        rs.getTimestamp("created_at").toInstant().toString()
                ), tenantId);
    }

    public List<OrgUnitRow> listOrgUnits(String tenantId) {
        return jdbc.query(
                "SELECT id, parent_id, name, created_at FROM org_units WHERE tenant_id = ? ORDER BY name",
                (rs, rowNum) -> new OrgUnitRow(
                        rs.getString("id"),
                        rs.getString("parent_id"),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toInstant().toString()
                ), tenantId);
    }

    public String createOrgUnit(String tenantId, String name, String parentId) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO org_units (id, tenant_id, parent_id, name) VALUES (?,?,?,?)",
                id, tenantId, parentId, name);
        return id;
    }

    public void assignDeviceOrgUnit(String tenantId, String deviceId, String orgUnitId) {
        jdbc.update(
                "UPDATE devices SET org_unit_id = ? WHERE tenant_id = ? AND id = ?",
                orgUnitId, tenantId, deviceId);
    }

    public record MapRow(String id, String name, String description, int memberCount, String createdAt) {}

    public record OrgUnitRow(String id, String parentId, String name, String createdAt) {}
}
