# ShipStream

Real-time logistics & shipment tracking backend — Spring Boot, PostgreSQL, Redis, WebSockets.
Focus: state machines, concurrency safety, transactional outbox, live updates, multi-instance job coordination.

See [docs/DESIGN.md](docs/DESIGN.md) for the architecture and phased roadmap.

## Run locally
```bash
docker compose up -d        # Postgres :5433, Redis :6379
./mvnw spring-boot:run
```
Tests (need Docker running — they use Testcontainers): `./mvnw verify`

## Status
- [x] Phase 1 — core setup: hubs, routes, shipments
- [ ] Phase 2 — state machine
- [ ] Phase 3 — tracking
- [ ] Phase 4 — outbox
- [ ] Phase 5 — WebSockets
- [ ] Phase 6 — Redis
- [ ] Phase 7 — background jobs
- [ ] Phase 8 — advanced
- [ ] Phase 9 — deployment
