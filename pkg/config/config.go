// Package config provides shared configuration loading utilities.
package config

import (
	"os"
	"strconv"
)

// Common holds configuration shared across services.
type Common struct {
	Env      string
	LogLevel string
}

// LoadCommon reads common env vars with defaults.
func LoadCommon() Common {
	return Common{
		Env:      getEnv("SENTINEL_ENV", "dev"),
		LogLevel: getEnv("SENTINEL_LOG_LEVEL", "info"),
	}
}

// Database holds PostgreSQL connection settings.
type Database struct {
	Host     string
	Port     int
	User     string
	Password string
	DBName   string
	SSLMode  string
}

// LoadDatabase reads database config from environment.
func LoadDatabase() Database {
	port, _ := strconv.Atoi(getEnv("DB_PORT", "5432"))
	return Database{
		Host:     getEnv("DB_HOST", "localhost"),
		Port:     port,
		User:     getEnv("DB_USER", "sentinel"),
		Password: getEnv("DB_PASSWORD", "sentinel"),
		DBName:   getEnv("DB_NAME", "sentinelhub"),
		SSLMode:  getEnv("DB_SSLMODE", "disable"),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
