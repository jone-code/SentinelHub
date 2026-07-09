.PHONY: help dev-up dev-down backend-build backend-run client-service-build client-native-build test

help:
	@echo "SentinelHub development commands:"
	@echo "  make dev-up                - Start infrastructure (docker-compose)"
	@echo "  make dev-down              - Stop infrastructure"
	@echo "  make backend-build         - Build Java backend (Maven)"
	@echo "  make backend-run           - Run unified API server"
	@echo "  make client-service-build  - Verify PC client background service (Node.js)"
	@echo "  make client-native-build   - Build PC native sidecar (Rust)"

dev-up:
	docker compose -f deploy/docker-compose/docker-compose.yml up -d

dev-down:
	docker compose -f deploy/docker-compose/docker-compose.yml down

backend-build:
	cd backend && ./mvnw clean package -DskipTests

backend-run:
	cd backend && ./mvnw -pl server spring-boot:run

client-service-build:
	cd client/service && node --check src/index.js

client-native-build:
	cd client/native && cargo build --release

test:
	cd backend && ./mvnw test
	cd client/service && npm test
