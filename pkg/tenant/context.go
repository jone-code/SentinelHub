// Package tenant provides multi-tenant context utilities.
package tenant

import "context"

type contextKey struct{}

// Info holds tenant context extracted from JWT or gRPC metadata.
type Info struct {
	TenantID string
	UserID   string
}

// WithContext attaches tenant info to context.
func WithContext(ctx context.Context, info Info) context.Context {
	return context.WithValue(ctx, contextKey{}, info)
}

// FromContext extracts tenant info from context.
func FromContext(ctx context.Context) (Info, bool) {
	info, ok := ctx.Value(contextKey{}).(Info)
	return info, ok
}
