.PHONY: help dev-up dev-down dev-services build test lint proto migrate

help:
	@echo "SentinelHub development commands:"
	@echo "  make dev-up        - Start infrastructure (docker-compose)"
	@echo "  make dev-down      - Stop infrastructure"
	@echo "  make dev-services  - Run platform services locally"
	@echo "  make build         - Build all Go binaries"
	@echo "  make test          - Run all tests"
	@echo "  make lint          - Run linters"
	@echo "  make proto         - Generate protobuf code"

dev-up:
	docker compose -f deploy/docker-compose/docker-compose.yml up -d

dev-down:
	docker compose -f deploy/docker-compose/docker-compose.yml down

build:
	@for svc in gateway identity device asset audit; do \
		echo "Building $$svc..."; \
		go build -o bin/$$svc ./services/$$svc/cmd/$$svc; \
	done
	@echo "Building agent..."
	go build -o bin/sentinel-agent ./agent/cmd/agent

dev-services:
	@echo "Start services individually, e.g.: go run ./services/gateway/cmd/gateway"

test:
	go test ./...

lint:
	golangci-lint run ./...

proto:
	@echo "Protobuf generation - configure buf in proto/buf.yaml"

migrate:
	@echo "Run migrations per service: migrate -path services/identity/migrations -database ... up"
