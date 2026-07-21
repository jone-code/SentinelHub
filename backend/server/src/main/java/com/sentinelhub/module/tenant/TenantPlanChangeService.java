package com.sentinelhub.module.tenant;

import com.sentinelhub.config.PlanApprovalProperties;
import com.sentinelhub.config.PlanBillingProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TenantPlanChangeService {

    private final TenantPlanRepository tenantPlanRepository;
    private final TenantPlanChangeRequestRepository requestRepository;
    private final PlanChangeApprovalRepository approvalRepository;
    private final TenantPlanService tenantPlanService;
    private final PlanBillingProperties billingProperties;
    private final PlanApprovalProperties approvalProperties;
    private final BillingWebhookClient billingWebhookClient;

    public TenantPlanChangeService(TenantPlanRepository tenantPlanRepository,
                                   TenantPlanChangeRequestRepository requestRepository,
                                   PlanChangeApprovalRepository approvalRepository,
                                   TenantPlanService tenantPlanService,
                                   PlanBillingProperties billingProperties,
                                   PlanApprovalProperties approvalProperties,
                                   BillingWebhookClient billingWebhookClient) {
        this.tenantPlanRepository = tenantPlanRepository;
        this.requestRepository = requestRepository;
        this.approvalRepository = approvalRepository;
        this.tenantPlanService = tenantPlanService;
        this.billingProperties = billingProperties;
        this.approvalProperties = approvalProperties;
        this.billingWebhookClient = billingWebhookClient;
    }

    public Map<String, Object> submitRequest(String tenantId, String userId, String toTierCode) {
        TenantPlanTier toTier = tenantPlanService.resolveTierPublic(toTierCode);
        TenantPlanTier fromTier = tenantPlanRepository.findPlanTier(tenantId);
        if (fromTier == toTier) {
            throw new IllegalArgumentException("plan_tier unchanged");
        }
        if (requestRepository.hasPending(tenantId)) {
            throw new IllegalStateException("pending plan change request already exists");
        }

        int priceCents = billingProperties.enabled()
                ? billingProperties.priceForTier(toTier.code())
                : 0;
        String billingNote = buildBillingNote(fromTier, toTier, priceCents);

        boolean downgrade = tierRank(toTier) < tierRank(fromTier);
        boolean needsApproval = approvalProperties.enabled()
                && (!downgrade || !approvalProperties.autoApproveDowngrade());

        if (!needsApproval) {
            return applyImmediately(tenantId, toTier, priceCents, billingNote, null);
        }

        String requestId = requestRepository.insert(
                tenantId, userId, fromTier.code(), toTier.code(), priceCents,
                billingProperties.currency(), billingNote);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "pending");
        out.put("request_id", requestId);
        out.put("from_tier", fromTier.code());
        out.put("to_tier", toTier.code());
        out.put("monthly_price_cents", priceCents);
        out.put("currency", billingProperties.currency());
        out.put("billing_note", billingNote);
        out.put("required_approvals", approvalProperties.requiredApprovals());
        out.put("approval_count", 0);
        out.put("message", "plan change submitted for approval");
        return out;
    }

    public List<Map<String, Object>> listRequests(String tenantId, String status) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TenantPlanChangeRequestRepository.PlanChangeRequest req : requestRepository.listByTenant(tenantId, status)) {
            rows.add(toMap(req));
        }
        return rows;
    }

    public Map<String, Object> approve(String tenantId, String reviewerId, String requestId, String reviewNote) {
        var req = requestRepository.findById(tenantId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("request not found"));
        if (!"pending".equals(req.status())) {
            throw new IllegalStateException("request is not pending");
        }
        if (reviewerId.equals(req.requestedBy())) {
            throw new IllegalArgumentException("requester cannot approve own request");
        }
        if (approvalRepository.hasApproved(requestId, reviewerId)) {
            throw new IllegalStateException("already approved by this reviewer");
        }

        approvalRepository.addApproval(requestId, reviewerId);
        int approvalCount = approvalRepository.countApprovals(requestId);
        int required = approvalProperties.requiredApprovals();

        if (approvalCount < required) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "pending");
            out.put("request_id", requestId);
            out.put("approval_count", approvalCount);
            out.put("required_approvals", required);
            out.put("approvers", approvalRepository.listReviewers(requestId));
            out.put("message", "approval recorded, awaiting more reviewers");
            return out;
        }

        requestRepository.approve(tenantId, requestId, reviewerId, reviewNote);
        tenantPlanService.updatePlanTier(tenantId, req.toTier());
        billingWebhookClient.notifyPlanChangeApplied(
                tenantId, requestId, req.fromTier(), req.toTier(),
                req.monthlyPriceCents(), req.currency()
        ).ifPresent(extId -> requestRepository.setBillingExternalId(tenantId, requestId, extId));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "approved");
        out.put("plan_tier", req.toTier());
        out.put("request_id", requestId);
        out.put("approval_count", approvalCount);
        out.put("required_approvals", required);
        out.put("message", "plan change approved and applied");
        return out;
    }

    public Map<String, Object> reject(String tenantId, String reviewerId, String requestId, String reviewNote) {
        var req = requestRepository.findById(tenantId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("request not found"));
        if (!"pending".equals(req.status())) {
            throw new IllegalStateException("request is not pending");
        }
        requestRepository.reject(tenantId, requestId, reviewerId, reviewNote);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "rejected");
        out.put("request_id", requestId);
        out.put("message", "plan change rejected");
        return out;
    }

    private Map<String, Object> applyImmediately(String tenantId, TenantPlanTier toTier,
                                                  int priceCents, String billingNote, String requestId) {
        tenantPlanService.updatePlanTier(tenantId, toTier.code());
        billingWebhookClient.notifyPlanChangeApplied(
                tenantId, requestId != null ? requestId : "immediate", "n/a", toTier.code(),
                priceCents, billingProperties.currency());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "applied");
        out.put("plan_tier", toTier.code());
        out.put("message", "plan tier updated immediately");
        out.put("monthly_price_cents", priceCents);
        out.put("currency", billingProperties.currency());
        out.put("billing_note", billingNote);
        return out;
    }

    private String buildBillingNote(TenantPlanTier from, TenantPlanTier to, int priceCents) {
        if (!billingProperties.enabled()) {
            return "billing disabled";
        }
        return "变更 " + from.code() + " → " + to.code()
                + "，预估月费 " + formatPrice(priceCents) + " " + billingProperties.currency();
    }

    private String formatPrice(int cents) {
        return String.format("%.2f", cents / 100.0);
    }

    private static int tierRank(TenantPlanTier tier) {
        return switch (tier) {
            case STARTER -> 0;
            case BUSINESS -> 1;
            case ENTERPRISE -> 2;
        };
    }

    private Map<String, Object> toMap(TenantPlanChangeRequestRepository.PlanChangeRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", req.id());
        m.put("from_tier", req.fromTier());
        m.put("to_tier", req.toTier());
        m.put("status", req.status());
        m.put("monthly_price_cents", req.monthlyPriceCents());
        m.put("currency", req.currency());
        m.put("billing_note", req.billingNote());
        m.put("billing_external_id", req.billingExternalId());
        m.put("review_note", req.reviewNote());
        m.put("requested_by", req.requestedBy());
        m.put("reviewed_by", req.reviewedBy());
        m.put("created_at", req.createdAt().toString());
        m.put("reviewed_at", req.reviewedAt() != null ? req.reviewedAt().toString() : null);
        if ("pending".equals(req.status())) {
            m.put("approval_count", approvalRepository.countApprovals(req.id()));
            m.put("required_approvals", approvalProperties.requiredApprovals());
            m.put("approvers", approvalRepository.listReviewers(req.id()));
        }
        return m;
    }
}
