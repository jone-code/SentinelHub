package core

import (
	"log"
	"time"
)

// Service is the background service for policy execution, heartbeat, and data collection.
// Runs alongside the Electron desktop UI.
type Service struct {
	cfg    Config
	stopCh chan struct{}
}

// NewService creates a new background service instance.
func NewService(cfg Config) *Service {
	return &Service{cfg: cfg, stopCh: make(chan struct{})}
}

// Run starts the service main loop.
func (s *Service) Run() error {
	ticker := time.NewTicker(s.cfg.HeartbeatInterval)
	defer ticker.Stop()

	for {
		select {
		case <-s.stopCh:
			return nil
		case <-ticker.C:
			s.heartbeat()
		}
	}
}

// Stop gracefully stops the service.
func (s *Service) Stop() {
	close(s.stopCh)
}

func (s *Service) heartbeat() {
	log.Printf("heartbeat to %s (client_id=%s)", s.cfg.ServerURL, s.cfg.ClientID)
}
