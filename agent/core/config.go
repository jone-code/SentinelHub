package core

import (
	"os"
	"time"
)

// Config holds agent runtime configuration.
type Config struct {
	ServerURL         string
	AgentID           string
	TenantToken       string
	Version           string
	HeartbeatInterval time.Duration
}

// LoadConfig reads configuration from environment variables.
func LoadConfig() Config {
	interval, _ := time.ParseDuration(getEnv("AGENT_HEARTBEAT_INTERVAL", "60s"))
	return Config{
		ServerURL:         getEnv("AGENT_SERVER_URL", "https://localhost:8080"),
		AgentID:           getEnv("AGENT_ID", ""),
		TenantToken:       getEnv("AGENT_TENANT_TOKEN", ""),
		Version:           getEnv("AGENT_VERSION", "0.1.0-dev"),
		HeartbeatInterval: interval,
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
