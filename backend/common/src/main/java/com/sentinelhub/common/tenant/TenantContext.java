package com.sentinelhub.common.tenant;

/**
 * Tenant context holder for multi-tenant request scope.
 */
public record TenantContext(
        String tenantId,
        String userId
) {
    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();

    public static void set(TenantContext context) {
        HOLDER.set(context);
    }

    public static TenantContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
