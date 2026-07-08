.PHONY: help dev-up dev-down backend-build backend-run client-service-build test

help:
	@echo "SentinelHub development commands:"
	@echo "  make dev-up                - Start infrastructure (docker-compose)"
	@echo "  make dev-down              - Stop infrastructure"
	@echo "  make backend-build         - Build Java backend (Maven)"
	@echo "  make backend-run           - Run unified API server"
	@echo "  make client-service-build  - Build PC client background service (Go)"

dev-up:
	docker compose -f deploy/docker-compose/docker-compose.yml up -d

dev-down:
	docker compose -f deploy/docker-compose/docker-compose.yml down

backend-build:
	cd backend && ./mvnw clean package -DskipTests

backend-run:
	cd backend && ./mvnw -pl server spring-boot:run

client-service-build:
	cd client/service && go build -o ../../bin/sentinel-service ./cmd/service

test:
	cd backend && ./mvnw test
