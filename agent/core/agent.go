package core

import (
	"log"
	"time"
)

// Agent is the main agent lifecycle manager.
type Agent struct {
	cfg    Config
	stopCh chan struct{}
}

// NewAgent creates a new Agent instance.
func NewAgent(cfg Config) *Agent {
	return &Agent{cfg: cfg, stopCh: make(chan struct{})}
}

// Run starts the agent main loop (heartbeat, policy sync).
func (a *Agent) Run() error {
	ticker := time.NewTicker(a.cfg.HeartbeatInterval)
	defer ticker.Stop()

	for {
		select {
		case <-a.stopCh:
			return nil
		case <-ticker.C:
			a.heartbeat()
		}
	}
}

// Stop gracefully stops the agent.
func (a *Agent) Stop() {
	close(a.stopCh)
}

func (a *Agent) heartbeat() {
	log.Printf("heartbeat to %s (agent_id=%s)", a.cfg.ServerURL, a.cfg.AgentID)
}
