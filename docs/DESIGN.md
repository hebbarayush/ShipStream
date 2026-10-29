# ShipStream — Design Plan

Real-time logistics & shipment tracking backend. Companion to RentalOps; the goal is to demonstrate
**state machines, concurrency safety, transactional outbox, real-time push, and multi-instance job coordination**.
Source idea: [Project-idea.md](../Project-idea.md).

## 1. Key design decisions

| Concern | Decision | Why |
|---|---|---|
| Structure | Modular monolith, package-by-feature (`shipment`, `hub`, `tracking`, `outbox`, `notification`, `jobs`) | Real modularity without microservice overhead; boundaries visible to a reviewer |
| Schema | Flyway migrations, `ddl-auto=validate` | Reproducible, reviewable DB history |
| State machine | Transition table in the `ShipmentStatus` enum; single entry point `ShipmentService.transition()` | Invalid transitions impossible to bypass |
| Concurrency | `@Version` optimistic locking on `shipments`; idempotency key on status updates deferred to a later pass | Concurrent agents can't corrupt state; losing writer gets a clean 409 to retry on |
| Events | Transactional outbox: business change + `outbox_events` row in one tx; poller publishes | No dual-write problem |
| Multi-instance outbox | `SELECT ... FOR UPDATE SKIP LOCKED` batch claim | Several instances can poll without double-processing |
| Job coordination | `job_locks` table with lease (`locked_until`) | Scheduled jobs run once across instances |
| Real-time | Spring WebSocket + STOMP, topic per shipment (`/topic/shipments/{trackingId}`) | Standard, easy to test, frontend-friendly |
| Multi-instance WS | Postgres `LISTEN/NOTIFY` fan-out (Phase 6) | A location update on instance A reaches sockets on instance B, with no extra infra |
| Cache | Caffeine, in-process cache-aside with TTL; DB stays source of truth | Fast reads without an extra moving part; acceptable since each instance's cache is just a local accelerator, not shared state |
| Errors | RFC 7807 `ProblemDetail` via `@RestControllerAdvice` | Consistent API errors |
| Tests | Unit tests for the state machine; Testcontainers (Postgres) for integration & concurrency | Correctness is the selling point |
| ETA | Direct `routes` row (distance) / assumed speed; no multi-hop path-finding (Phase 8) | Matches the project's explicit non-goal of route optimization; still a believable number |
| GPS simulation | Scheduled job linearly interpolates origin→destination hub coordinates by elapsed time vs ETA (Phase 8) | Drives the real-time pipeline (outbox → NOTIFY → WS) without needing a real agent/device feed |
| Delay detection | Scheduled, lease-guarded job moves any shipment past its ETA into `DELAYED` (Phase 8) | Reuses the same `job_locks` coordination as the other background jobs; no new pattern |
| Auth | Stateless JWT in an httpOnly cookie, one `OPS` role, public tracking stays open (Phase 10) | Matches how real carriers split the audience: a customer can track a package with no account, same as FedEx/DHL; everything that can change data needs a login. httpOnly defeats XSS token theft; SameSite=Lax defeats the CSRF risk that comes with cookie-based auth |

Auth deliberately isn't bigger than this: one role, one seeded demo user, no registration flow, no
password reset. The schema (`users.role`) supports adding `ADMIN`/`DISPATCHER` later without a
migration, but building that now would be scope without a reason - nothing in the app yet needs more
than "logged in or not."

Deliberate deviations from the original doc (improvements):
- `shipment_events` and `outbox_events` are separate: events = permanent audit trail, outbox = transient delivery queue.
- Added a `current_hub_id`/`destination_hub_id` pair on shipments (Phase 8) so ETA and GPS simulation have something to compute against.
- Enum columns stored as `VARCHAR` with `CHECK` constraints.
- Tracking updates are append-only history-light: latest position only (per spec), plus an event for milestones.
- **Redis dropped entirely.** The original idea doc used it for caching and cross-instance WebSocket
  fan-out. Both are replaced with zero-extra-infra alternatives: Caffeine (in-process cache) for reads,
  and Postgres `LISTEN/NOTIFY` for fan-out. This keeps the stack to one datastore (Postgres) while still
  demonstrating the same concurrency/distribution concerns the project is meant to showcase.

## 2. Architecture

```
React SPA ─► REST (Spring MVC) ─► Services ─► PostgreSQL (truth)
     │                │                 │  ├── outbox_events (same tx)
     │                │                 │  └── LISTEN/NOTIFY (WS fan-out)
     │                │                 └────── Caffeine (local cache)
     └── STOMP/WS ◄── broadcaster ◄── OutboxProcessor / TrackingService
                                       Scheduled jobs (job_locks)
```

## 3. Shipment state machine

```
CREATED → PICKED_UP → IN_TRANSIT → ARRIVED_AT_HUB → OUT_FOR_DELIVERY → DELIVERED
   │          │            │ ▲            │ ▲              │ ▲
   │          │            └─┴────────────┘ │  (hub-to-hub: multi-leg journeys)
   │          │            └────── DELAYED ─┴──────────────┘  (ETA passed; resumes wherever it stalled)
   ▼          ▼            ▼
CANCELLED   CANCELLED     LOST  (and LOST from ARRIVED_AT_HUB/OUT_FOR_DELIVERY/DELAYED too)
```
`DELAYED`, `LOST`, `CANCELLED` landed in Phase 8. `CANCELLED` is only reachable before pickup (you can't
un-ship something already moving); `LOST` is reachable from any in-flight state, as an ops action; `DELAYED`
is recoverable — the delay-detection job (§6, Phase 8) drops an overdue shipment into it, and normal
progress out of it resumes the flow. Terminal: `DELIVERED`, `LOST`, `CANCELLED`.
Every accepted transition writes a `shipment_events` row and an `outbox_events` row in the same transaction.

## 4. Data model
Per spec section 5, delivered incrementally by Flyway migrations:
`V1` hubs, routes, shipments · `V2` shipment_events · `V3` shipment_tracking · `V4` outbox_events ·
`V5` job_locks · `V6` hub coordinates, shipment destination hub + ETA, new status values (Phase 8) ·
`V7` users (Phase 10).

## 5. API surface
```
PUBLIC (no login)
GET  /api/v1/shipments/track/{trackingId}                                      [P1]
GET  /api/v1/tracking/{shipmentId}                                             [P3]
WS   /ws  → /topic/shipments/{trackingId}                                      [P5]
POST /api/v1/auth/login                                                        [P10]

OPS LOGIN REQUIRED (everything else)
POST /api/v1/shipments                     create (originHubId, destinationHubId optional)
GET  /api/v1/shipments/{id}
POST /api/v1/shipments/{id}/status         transition (idempotency key: future work)   [P2]
POST /api/v1/tracking/update                                                    [P3]
POST /api/v1/hubs (now takes latitude/longitude) · GET /api/v1/hubs · GET /api/v1/routes  [P1]
GET  /api/v1/shipments?status=&page=&size=  paginated list                     [P9]
GET  /api/v1/shipments/{id}/events          audit trail, used by the detail page [P9]
GET  /api/v1/auth/me · POST /api/v1/auth/logout                                [P10]
```
`ShipmentResponse` now also carries `destinationHubId` and `estimatedDeliveryAt` (Phase 8); the latter is
set when a shipment enters `IN_TRANSIT` with a direct route to its destination, and cleared on reaching a
terminal status. CORS is configured once, as a `CorsConfigurationSource` bean Spring Security's filter
chain uses directly (`SecurityConfig`) - the earlier `WebConfig`/`WebMvcConfigurer` approach was removed
in Phase 10, since MVC-level CORS runs too late to matter once the Security filter chain is in front of it.

## 6. Phases & GitHub milestones
Each phase = one branch/PR-sized chunk merged to `main` with a tag (`phase-1` …) and README/docs updated.

| Phase | Deliverable | Proof of done |
|---|---|---|
| 1 | Project setup, Postgres via docker-compose, Hub/Route/Shipment CRUD-lite | Integration test creates + fetches shipment |
| 2 | State machine + `shipment_events` + optimistic lock | Unit tests for every valid/invalid transition; concurrent-update test |
| 3 | Tracking update/get | Location persisted, validated |
| 4 | Outbox + processor w/ retries, SKIP LOCKED | Test: event delivered exactly once with 2 pollers; retry on failure |
| 5 | WebSocket push | Test client receives location update |
| 6 | Caffeine cache + Postgres `LISTEN/NOTIFY` fan-out | Cache hit test, TTL, invalidation; two-instance WS fan-out test |
| 7 | `job_locks` + scheduled jobs | Two instances, one runs the job |
| 8 | ETA, GPS simulator, delayed/lost handling | Simulator drives a shipment end-to-end |
| 9 | React frontend (see §8) | UI creates a shipment, tracks it by ID, and shows a live status/location update without a page refresh |
| 10 | Auth: public tracking vs. authenticated ops (see §9) | Logged-out requests to write/internal endpoints get 401; the public tracking page needs no login |
| 11 | Dockerfile, CI (GitHub Actions), deploy (frontend + backend) | Live URL in README |

Frontend before deployment, not after: Phase 11 should ship the complete product, not just the API.
Building the UI in Phase 9 and locking down who can do what in Phase 10, before deployment, means Phase 11
puts one coherent, defensible product behind a public URL - not an open API with a UI bolted on that
anyone could reach and mutate.

**Pre-deployment audit** (before actually standing up a live URL, target: Render): checked what existed
against what a deployment needs, not just re-read the roadmap.

- **CORS/cookies were hardcoded to `localhost`** (`SecurityConfig`) - a real blocker, not polish: any
  deployed frontend would've had every request rejected. Fixed: `CORS_ALLOWED_ORIGINS` (comma-separated)
  and the datasource URL now also builds from `PGHOST`/`PGPORT`/`PGDATABASE` if `DB_URL` isn't set
  directly, so a platform that injects Postgres by individual field (Render's `fromDatabase`) doesn't
  need anyone hand-assembling a JDBC URL.
- **Dockerfiles** (`backend/Dockerfile`, `frontend/Dockerfile` + `nginx.conf`), both multi-stage,
  non-root runtime user, built and run-tested locally (not just written): confirmed the backend image
  connects to Postgres and passes its health check, confirmed the frontend image's nginx serves both
  the root path and a deep client-side route (`/track/FT-DEMO00001`) with 200, not a bare 404.
- **CI** (`.github/workflows/ci.yml`): backend `mvn verify` (Testcontainers, needs Docker - GitHub-hosted
  runners have it preinstalled) and frontend typecheck+build, both building their container image too.
- **React `ErrorBoundary`**: there wasn't one. An uncaught render error would've white-screened with
  nothing recoverable on screen - exactly the failure mode that made two earlier bugs this project
  confusing to diagnose from the outside.
- **`.env.example`** in both `backend/` and `frontend/`, documenting every variable a deployment needs.
- **`LICENSE`** (MIT) at the repo root.
- Found and fixed a real, live race along the way (not deployment-related, just caught by actually
  running the built backend image against Postgres instead of assuming it'd work): `cleanupStaleTracking`
  deleted `shipment_tracking` rows purely by age, including for shipments still `IN_TRANSIT` - if the
  app was offline long enough, the cleanup job and `GpsSimulator` could race on the same row (Hibernate's
  `StaleStateException`, already caught and self-healing next tick, but noisy and avoidable). Scoped the
  delete to shipments in a terminal status instead; an active shipment's tracking row is never "stale"
  just because nobody was writing to it.
- Deliberately not done now: actually creating the Render services and getting a live URL. That's
  Render-account-specific work only the project owner can do; everything above is what makes doing it
  mechanical rather than full of surprises once they do.

## 7. Resume angle
Lead with: state machine correctness, outbox + SKIP LOCKED, optimistic-lock concurrency test, WS + Postgres `LISTEN/NOTIFY` fan-out, lease-based job locks, single-datastore design (no Redis), public/authenticated split matching real carrier UX. Add a README architecture diagram and load/concurrency test numbers in Phase 8–11.

## 8. Phase 9: React frontend

Backend-only up to here was deliberate (resume-angle work is in the services, not the UI), but the
project isn't demoable without a UI. Scope, kept proportionate to a backend-focused project:

| Concern | Decision | Why |
|---|---|---|
| Tooling | Vite + React + TypeScript, under `frontend/` in this repo | No backend framework coupling; fast dev loop; one repo to clone |
| Data fetching | `fetch` + TanStack Query (cache, retry, loading/error states for free) | Avoids hand-rolled request state plumbing |
| Real-time | `@stomp/stompjs` + `sockjs-client` against `/ws`, subscribed per open shipment | Matches the backend's existing STOMP/SockJS endpoint, no backend change needed |
| Styling | Plain CSS, one token file (`theme.css`) | No component library or utility-class framework to fight with; every page reads the same tokens, so there's exactly one visual theme, not an accretion of per-page styling |
| State | Local component state + TanStack Query cache; no Redux | App is small: a list view, a detail/tracking view, a create form, an admin screen |

### Pages (all shipped)
1. **Shipments list** (`/`) — paginated table, filter by status.
2. **Shipment detail / live tracking** (`/shipments/:id`) — details, ETA, live position, status-transition
   buttons (only the valid next steps for the current status are shown), audit event history, a live/offline
   indicator for the WebSocket subscription.
3. **Create shipment** (`/shipments/new`) — form wrapping `POST /api/v1/shipments`, with hub pickers.
4. **Hubs & routes admin** (`/hubs`) — list + create forms for both, side by side.

### Backend prerequisites (delivered alongside the frontend, additive — no existing endpoint changed)
- `GET /api/v1/shipments` (paginated, optional status filter).
- `GET /api/v1/shipments/{id}/events` (audit trail for the detail page).
- CORS (`WebConfig`) for `localhost:5173` (dev) and `:4173` (`npm run preview`, the production build served
  locally).

### Proof of done
Verified live, not just built: ran the backend + `npm run preview` together, hit every page through a
real HTTP round trip (not mocked), and confirmed the JSON each endpoint returns matches the frontend's
TypeScript types exactly. Screenshotted all four pages to check the single-theme requirement held up in
practice, not just in the CSS source.

## 9. Phase 10: Auth - public tracking vs. authenticated ops

The question that forced this phase: deployed with no login, who's the "user" of the shipments list,
the create form, the hub/route admin? Anyone with the URL - which makes it an open database with a UI
on it, not an ops tool. Real carriers split the audience in two, and it maps directly onto pages already
built in Phase 9:

| Audience | Access | Pages |
|---|---|---|
| Public (a customer checking a package) | No login, read-only, one lookup | `/`, `/track/:trackingId` |
| Internal (dispatchers/warehouse staff) | Must log in | everything under `/app` (list, create, transitions, hub/route admin) |

| Concern | Decision | Why |
|---|---|---|
| Token | JWT (HS256, `io.jsonwebtoken`/jjwt), `sub`=username, `role` claim, 12h expiry | Stateless - no server-side session table, no extra DB round trip per request to check who's logged in |
| Transport | httpOnly, `SameSite=Lax` cookie, not a header the frontend attaches itself | Defeats XSS token theft (JS can't read the cookie); Lax defeats the CSRF risk that cookie auth otherwise reintroduces, since the frontend and API are same-site |
| Password storage | BCrypt (`spring-boot-starter-security`'s `BCryptPasswordEncoder`) | Standard, no reason to roll anything else |
| Demo access | `DemoUserSeeder` seeds `demo`/`demo1234` (OPS); `DemoDataSeeder` seeds a shipment at a fixed tracking ID (`FT-DEMO00001`), both idempotent `ApplicationRunner`s, both shown directly on the login/tracking pages | A live/shared demo needs something to log in with *and* something to track, without anyone running SQL by hand, using real credentials, or hunting through a README to find them |
| Error shape | 401/403 from the security filter chain return the same `ProblemDetail` JSON as every other API error (`ProblemDetailAuthHandler`) | Spring Security's default is an empty body; `@ExceptionHandler` can't reach filter-chain exceptions, so this needed its own handler |
| CORS | Moved from a `WebMvcConfigurer` (`WebConfig`, removed) to a `CorsConfigurationSource` bean the Security filter chain uses directly | MVC-level CORS runs after the Security filter chain; with Security in front, the old approach would silently stop handling preflight requests correctly |

### What's public vs. protected
See §5. The only three things reachable without a login: shipment lookup by tracking ID, that
shipment's live position, and the WebSocket topic that pushes its updates - exactly what a courier's
public tracking page shows, nothing more (no internal hub IDs beyond what `ShipmentResponse` already
exposes, no audit trail, no admin data).

### Deliberately not built
One role (`OPS`), not role-depth (`ADMIN` vs `DISPATCHER`) - the `users.role` column exists for that
later, but nothing in the app today needs the distinction. No registration flow, no password reset,
no "forgot password" email - this is a demo with one seeded login, not a product with a user base.

### Proof of done
Verified the full pipeline live, not just unit-tested: confirmed a logged-out request to a protected
endpoint gets 401, `POST /auth/login` issues a real cookie, that cookie authenticates a subsequent
request, and the public tracking/live-position endpoints work with no cookie at all - all over real
HTTP, not mocked security context. Frontend: confirmed via DOM inspection that visiting `/app` logged
out redirects to `/login` (React Router client-side redirect, not a server one). 40/40 backend tests
pass, including dedicated tests for the unauthenticated-401 case and the real login round trip.

`DemoDataSeeder` (same phase, demo-usability follow-up): confirmed the seeded shipment reaches
`IN_TRANSIT` with a real ETA on first boot, confirmed restart idempotency (same row, same ID, no
duplicate, no re-seed log line), and confirmed the API responses it produces match exactly what the
frontend's hardcoded `FT-DEMO00001` constant expects.
