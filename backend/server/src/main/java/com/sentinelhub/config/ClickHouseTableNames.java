package com.sentinelhub.config;

public final class ClickHouseTableNames {

    private ClickHouseTableNames() {
    }

    public static String qualified(AuditClickHouseProperties properties, String table) {
        String base = properties.database() + "." + table;
        if (properties.replacingMerge()) {
            return base + " FINAL";
        }
        return base;
    }
}
