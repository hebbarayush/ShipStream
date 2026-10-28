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
`V5` job_locks · `V6` hub coordinates, shipment destination hub + ETA, new status values (Phase 8).

## 5. API surface
```
POST /api/v1/shipments                     create (originHubId, destinationHubId optional)
GET  /api/v1/shipments/{id}
GET  /api/v1/shipments/track/{trackingId}
POST /api/v1/shipments/{id}/status         transition (idempotency key: future work)   [P2]
POST /api/v1/tracking/update                                                    [P3]
GET  /api/v1/tracking/{shipmentId}                                              [P3]
POST /api/v1/hubs (now takes latitude/longitude) · GET /api/v1/hubs · GET /api/v1/routes  [P1]
WS   /ws  → /topic/shipments/{trackingId}                                       [P5]
GET  /api/v1/shipments?status=&page=&size=  paginated list                     [P9]
GET  /api/v1/shipments/{id}/events          audit trail, used by the detail page [P9]
```
`ShipmentResponse` now also carries `destinationHubId` and `estimatedDeliveryAt` (Phase 8); the latter is
set when a shipment enters `IN_TRANSIT` with a direct route to its destination, and cleared on reaching a
terminal status. CORS is enabled for `localhost:5173`/`:4173` (Vite dev/preview) in `WebConfig`.

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
| 10 | Dockerfile, CI (GitHub Actions), deploy (frontend + backend) | Live URL in README |

Frontend before deployment, not after: Phase 10 should ship the complete product, not just the API.
Building the UI in Phase 9, once the backend feature set (through Phase 8) is settled, avoids
re-deploying twice and gives Phase 10 one coherent "visit this URL" demo instead of two.

## 7. Resume angle
Lead with: state machine correctness, outbox + SKIP LOCKED, optimistic-lock concurrency test, WS + Postgres `LISTEN/NOTIFY` fan-out, lease-based job locks, single-datastore design (no Redis). Add a README architecture diagram and load/concurrency test numbers in Phase 8–10.

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
