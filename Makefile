.PHONY: help dev-up dev-down backend-build backend-run test

help:
	@echo "SentinelHub development commands:"
	@echo "  make dev-up          - Start infrastructure (docker-compose)"
	@echo "  make dev-down        - Stop infrastructure"
	@echo "  make backend-build   - Build Java backend (Maven)"
	@echo "  make backend-run     - Run gateway service"
	@echo "  make agent-build     - Build Go agent"

dev-up:
	docker compose -f deploy/docker-compose/docker-compose.yml up -d

dev-down:
	docker compose -f deploy/docker-compose/docker-compose.yml down

backend-build:
	cd backend && ./mvnw clean package -DskipTests

backend-run:
	cd backend && ./mvnw -pl gateway spring-boot:run

agent-build:
	go build -o bin/sentinel-agent ./agent/cmd/agent

test:
	cd backend && ./mvnw test
