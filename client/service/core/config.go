package core

import (
	"os"
	"time"
)

// Config holds client background service configuration.
type Config struct {
	ServerURL         string
	ClientID          string
	TenantToken       string
	Version           string
	HeartbeatInterval time.Duration
}

// LoadConfig reads configuration from environment variables.
func LoadConfig() Config {
	interval, _ := time.ParseDuration(getEnv("CLIENT_HEARTBEAT_INTERVAL", "60s"))
	return Config{
		ServerURL:         getEnv("CLIENT_SERVER_URL", "http://localhost:8080"),
		ClientID:          getEnv("CLIENT_ID", ""),
		TenantToken:       getEnv("CLIENT_TENANT_TOKEN", ""),
		Version:           getEnv("CLIENT_VERSION", "0.1.0-dev"),
		HeartbeatInterval: interval,
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
