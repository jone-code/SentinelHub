package com.sentinelhub.module.identity;

import com.sentinelhub.module.identity.domain.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private static final RowMapper<User> ROW_MAPPER = (rs, rowNum) -> new User(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("org_unit_id"),
            rs.getString("email"),
            rs.getString("name"),
            rs.getString("password_hash"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant()
    );

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByEmail(String tenantId, String email) {
        var list = jdbc.query(
                "SELECT * FROM users WHERE tenant_id = ? AND email = ? AND deleted_at IS NULL",
                ROW_MAPPER, tenantId, email);
        return list.stream().findFirst();
    }

    public List<String> findRoleCodes(String userId) {
        return jdbc.query(
                "SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id = r.id WHERE ur.user_id = ?",
                (rs, rowNum) -> rs.getString("code"), userId);
    }

    public void insert(User user) {
        jdbc.update(
                "INSERT INTO users (id, tenant_id, org_unit_id, email, name, password_hash, status) VALUES (?,?,?,?,?,?,?)",
                user.id(), user.tenantId(), user.orgUnitId(), user.email(), user.name(), user.passwordHash(), user.status());
    }

    public String insertRole(String tenantId, String code, String name) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO roles (id, tenant_id, code, name) VALUES (?,?,?,?)", id, tenantId, code, name);
        return id;
    }

    public void assignRole(String userId, String roleId) {
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", userId, roleId);
    }

    public Optional<String> findTenantIdBySlug(String slug) {
        var ids = jdbc.query("SELECT id FROM tenants WHERE slug = ? AND status = 'active'", (rs, n) -> rs.getString("id"), slug);
        return ids.stream().findFirst();
    }

    public String insertTenant(String name, String slug, String registrationToken) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO tenants (id, name, slug, registration_token) VALUES (?,?,?,?)",
                id, name, slug, registrationToken);
        return id;
    }

    public String insertOrgUnit(String tenantId, String name, String path) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO org_units (id, tenant_id, name, path) VALUES (?,?,?,?)", id, tenantId, name, path);
        return id;
    }

    public boolean tenantExists(String slug) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE slug = ?", Integer.class, slug);
        return count != null && count > 0;
    }

    public Optional<String> findTenantIdByRegistrationToken(String token) {
        var ids = jdbc.query(
                "SELECT id FROM tenants WHERE registration_token = ? AND status = 'active'",
                (rs, n) -> rs.getString("id"), token);
        return ids.stream().findFirst();
    }
}
