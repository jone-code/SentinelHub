package main

import (
	"log"
	"os"

	"github.com/jone-code/SentinelHub/services/remote/internal/server"
)

func main() {
	addr := os.Getenv("HTTP_ADDR")
	if addr == "" {
		addr = ":8080"
	}
	srv := server.New()
	log.Printf("remote service listening on %s", addr)
	if err := srv.ListenAndServe(addr); err != nil {
		log.Fatal(err)
	}
}
