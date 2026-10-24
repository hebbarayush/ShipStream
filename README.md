# ShipStream

Real-time logistics & shipment tracking backend — Spring Boot, PostgreSQL, WebSockets.
Focus: state machines, concurrency safety, transactional outbox, live updates, multi-instance job coordination.
Single datastore by design: no Redis — local (Caffeine) caching and Postgres `LISTEN/NOTIFY` cover what
Redis would have done for caching and cross-instance WebSocket fan-out.

See [docs/DESIGN.md](docs/DESIGN.md) for the architecture and phased roadmap.

## Run locally
```bash
docker compose up -d        # Postgres :5433
./mvnw spring-boot:run
```
Tests (need Docker running — they use Testcontainers): `./mvnw verify`

## Status
- [x] Phase 1 — core setup: hubs, routes, shipments
- [x] Phase 2 — state machine: transitions, `shipment_events`, optimistic locking
- [x] Phase 3 — tracking: `/tracking/update`, `/tracking/{shipmentId}`
- [ ] Phase 4 — outbox
- [ ] Phase 5 — WebSockets
- [ ] Phase 6 — caching (Caffeine) + Postgres `LISTEN/NOTIFY` fan-out
- [ ] Phase 7 — background jobs
- [ ] Phase 8 — advanced
- [ ] Phase 9 — deployment
