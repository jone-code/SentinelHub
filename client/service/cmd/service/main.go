package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/jone-code/SentinelHub/client/service/core"
)

func main() {
	cfg := core.LoadConfig()
	svc := core.NewService(cfg)

	go func() {
		if err := svc.Run(); err != nil {
			log.Fatalf("client service error: %v", err)
		}
	}()

	log.Printf("SentinelHub Client Service %s starting (server: %s)", cfg.Version, cfg.ServerURL)

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
	<-sig
	log.Println("shutting down client service...")
	svc.Stop()
}
