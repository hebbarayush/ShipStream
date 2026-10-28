# ShipStream

Real-time logistics & shipment tracking backend — Spring Boot, PostgreSQL, WebSockets.
Focus: state machines, concurrency safety, transactional outbox, live updates, multi-instance job coordination.
Single datastore by design: no Redis — local (Caffeine) caching and Postgres `LISTEN/NOTIFY` cover what
Redis would have done for caching and cross-instance WebSocket fan-out.

See [docs/DESIGN.md](docs/DESIGN.md) for the architecture and phased roadmap, and
[frontend/README.md](frontend/README.md) for the UI.

## Structure
```
backend/    Spring Boot API (Java 21, Maven)
frontend/   React + TypeScript UI (Vite)
docs/       Design doc and roadmap
```

## Run locally
```bash
docker compose up -d                  # Postgres :5433, from the repo root
cd backend && ./mvnw spring-boot:run
```
Tests (need Docker running — they use Testcontainers): `cd backend && ./mvnw verify`

Frontend (needs the backend running first):
```bash
cd frontend && npm install && npm run dev   # http://localhost:5173
```

## Status
- [x] Phase 1 — core setup: hubs, routes, shipments
- [x] Phase 2 — state machine: transitions, `shipment_events`, optimistic locking
- [x] Phase 3 — tracking: `/tracking/update`, `/tracking/{shipmentId}`
- [x] Phase 4 — transactional outbox, `SKIP LOCKED` polling
- [x] Phase 5 — WebSockets: STOMP over `/ws`, `/topic/shipments/{trackingId}`
- [x] Phase 6 — caching (Caffeine) + Postgres `LISTEN/NOTIFY` fan-out
- [x] Phase 7 — background jobs: `job_locks` lease + guarded scheduled jobs
- [x] Phase 8 — advanced: ETA calculation, GPS simulator, `DELAYED`/`LOST`/`CANCELLED` handling
- [x] Phase 9 — React frontend (`frontend/`, Vite + TypeScript): shipment list/create/track UI, live via WebSocket
- [ ] Phase 10 — deployment (frontend + backend)
