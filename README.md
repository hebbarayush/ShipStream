# ShipStream

Real-time logistics & shipment tracking backend — Spring Boot, PostgreSQL, WebSockets.
Focus: state machines, concurrency safety, transactional outbox, live updates, multi-instance job coordination.
Single datastore by design: no Redis — local (Caffeine) caching and Postgres `LISTEN/NOTIFY` cover what
Redis would have done for caching and cross-instance WebSocket fan-out.

See [docs/DESIGN.md](docs/DESIGN.md) for the architecture and phased roadmap, and
[frontend/README.md](frontend/README.md) for the UI.

## Structure
```
backend/    Spring Boot API (Java 21, Maven) - Dockerfile included
frontend/   React + TypeScript UI (Vite) - Dockerfile + nginx.conf included
docs/       Design doc and roadmap
.github/    CI (build + test both on every push)
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

### Demo data
`/` is public — anyone can look up a shipment by tracking ID, no login, same as any courier's tracking
page. Everything else (`/app/...`) needs an OPS login. Both are seeded automatically on backend startup
(`DemoUserSeeder`, `DemoDataSeeder`) and shown right on the login/tracking pages, not just here:

- **Ops login:** `demo` / `demo1234` (override via `DEMO_OPS_USERNAME` / `DEMO_OPS_PASSWORD`)
- **Demo tracking ID:** `FT-DEMO00001` — seeded `IN_TRANSIT` with a live ETA and a moving position, and
  self-healing: if it ever drifts into `DELAYED` from sitting unattended, the next backend restart
  resumes it with a fresh ETA

Set `JWT_SECRET` to something real before deploying anywhere beyond local dev — the default in
`application.properties` is intentionally public and documented as such.

## Deploying
Both apps have a production Dockerfile (`backend/Dockerfile`, `frontend/Dockerfile` + `nginx.conf` for
SPA routing) - built and run-tested locally, not just written. See `.env.example` in each for every
variable a deployment needs, in particular `CORS_ALLOWED_ORIGINS` (backend, must list the deployed
frontend's real origin) and `VITE_API_URL` (frontend, a Docker *build* arg - Vite bakes it into the
static bundle, there's no server at runtime to read it from).

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
- [x] Phase 10 — auth: public tracking vs. JWT-authenticated ops dashboard (single `OPS` role)
- [ ] Phase 11 — deployment (frontend + backend)
