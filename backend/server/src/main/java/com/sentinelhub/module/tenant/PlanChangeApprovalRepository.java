package com.sentinelhub.module.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PlanChangeApprovalRepository {

    private final JdbcTemplate jdbc;

    public PlanChangeApprovalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void addApproval(String requestId, String reviewerId) {
        jdbc.update(
                "INSERT INTO plan_change_approvals (request_id, reviewer_id) VALUES (?, ?)",
                requestId, reviewerId);
    }

    public int countApprovals(String requestId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM plan_change_approvals WHERE request_id = ?",
                Integer.class, requestId);
        return count != null ? count : 0;
    }

    public boolean hasApproved(String requestId, String reviewerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM plan_change_approvals WHERE request_id = ? AND reviewer_id = ?",
                Integer.class, requestId, reviewerId);
        return count != null && count > 0;
    }

    public List<String> listReviewers(String requestId) {
        return jdbc.query(
                "SELECT reviewer_id FROM plan_change_approvals WHERE request_id = ? ORDER BY created_at",
                (rs, rowNum) -> rs.getString("reviewer_id"),
                requestId);
    }
}
