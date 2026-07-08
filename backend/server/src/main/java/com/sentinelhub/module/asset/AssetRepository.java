package com.sentinelhub.module.asset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AssetRepository {

    private final JdbcTemplate jdbc;

    public AssetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertHardware(String tenantId, String deviceId, Map<String, Object> hw, String collectedAt) {
        String hostname = str(hw.get("hostname"));
        String osType = str(hw.get("os_type"));
        String osVersion = str(hw.get("os_version"));
        String arch = str(hw.get("arch"));
        String cpuModel = str(hw.get("cpu_model"));
        Integer cpuCores = intVal(hw.get("cpu_cores"));
        Integer memoryMb = intVal(hw.get("memory_total_mb"));

        int updated = jdbc.update(
                "UPDATE asset_hardware SET hostname=?, os_type=?, os_version=?, arch=?, cpu_model=?, cpu_cores=?, "
                        + "memory_mb=?, raw_data=CAST(? AS JSON), collected_at=? WHERE device_id=?",
                hostname, osType, osVersion, arch, cpuModel, cpuCores, memoryMb,
                toJson(hw), collectedAt, deviceId);

        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO asset_hardware (id, tenant_id, device_id, hostname, os_type, os_version, arch, "
                            + "cpu_model, cpu_cores, memory_mb, raw_data, collected_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),?)",
                    UUID.randomUUID().toString(), tenantId, deviceId, hostname, osType, osVersion, arch,
                    cpuModel, cpuCores, memoryMb, toJson(hw), collectedAt);
        }
    }

    public void replaceSoftware(String tenantId, String deviceId, List<Map<String, Object>> software, String collectedAt) {
        jdbc.update("DELETE FROM asset_software WHERE device_id = ?", deviceId);
        for (Map<String, Object> item : software) {
            jdbc.update(
                    "INSERT INTO asset_software (id, tenant_id, device_id, name, version, collected_at) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), tenantId, deviceId,
                    str(item.get("name")), str(item.get("version")), collectedAt);
        }
    }

    public Map<String, Object> getHardware(String deviceId) {
        var list = jdbc.queryForList("SELECT * FROM asset_hardware WHERE device_id = ? LIMIT 1", deviceId);
        return list.isEmpty() ? null : list.getFirst();
    }

    public List<Map<String, Object>> getSoftware(String deviceId) {
        return jdbc.queryForList(
                "SELECT name, version, collected_at FROM asset_software WHERE device_id = ? ORDER BY name LIMIT 200",
                deviceId);
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private static Integer intVal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
