package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/jone-code/SentinelHub/agent/core"
)

func main() {
	cfg := core.LoadConfig()
	agent := core.NewAgent(cfg)

	go func() {
		if err := agent.Run(); err != nil {
			log.Fatalf("agent error: %v", err)
		}
	}()

	log.Printf("SentinelHub Agent %s starting (server: %s)", cfg.Version, cfg.ServerURL)

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
	<-sig
	log.Println("shutting down agent...")
	agent.Stop()
}
