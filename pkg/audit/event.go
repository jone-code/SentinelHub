// Package audit defines the canonical audit event schema used across all modules.
package audit

import "time"

// Event is the standard audit event structure published to NATS and stored in ClickHouse.
type Event struct {
	EventID      string                 `json:"event_id"`
	TenantID     string                 `json:"tenant_id"`
	Timestamp    time.Time              `json:"timestamp"`
	EventType    string                 `json:"event_type"`
	ActorType    string                 `json:"actor_type"` // user|agent|system
	ActorID      string                 `json:"actor_id"`
	ResourceType string                 `json:"resource_type"`
	ResourceID   string                 `json:"resource_id"`
	DeviceID     string                 `json:"device_id,omitempty"`
	Action       string                 `json:"action"`
	Result       string                 `json:"result"` // success|failure|blocked
	ClientIP     string                 `json:"client_ip,omitempty"`
	Metadata     map[string]interface{} `json:"metadata,omitempty"`
}

// Event type constants.
const (
	EventDeviceRegistered = "device.registered"
	EventPolicyPublished  = "policy.published"
	EventSoftwareBlocked  = "software.blocked"
	EventDLPBlocked       = "dlp.blocked"
	EventComplianceFailed = "compliance.failed"
	EventNACDenied        = "nac.denied"
	EventRemoteSession    = "remote.session"
)
