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
| Tests | Unit tests for the state machine; Testcontainers (Postgres/Redis) for integration & concurrency | Correctness is the selling point |

Deliberate deviations from the original doc (improvements):
- `shipment_events` and `outbox_events` are separate: events = permanent audit trail, outbox = transient delivery queue.
- Added a `sender_hub_id`/`destination_hub_id` pair so routing has something to work with.
- Enum columns stored as `VARCHAR` with `CHECK` constraints.
- Tracking updates are append-only history-light: latest position only (per spec), plus an event for milestones.
- **Redis dropped entirely.** The original idea doc used it for caching and cross-instance WebSocket
  fan-out. Both are replaced with zero-extra-infra alternatives: Caffeine (in-process cache) for reads,
  and Postgres `LISTEN/NOTIFY` for fan-out. This keeps the stack to one datastore (Postgres) while still
  demonstrating the same concurrency/distribution concerns the project is meant to showcase.

## 2. Architecture

```
Clients ─► REST (Spring MVC) ─► Services ─► PostgreSQL (truth)
                 │                 │  ├── outbox_events (same tx)
                 │                 │  └── LISTEN/NOTIFY (WS fan-out)
                 │                 └────── Caffeine (local cache)
   WebSocket/STOMP ◄── broadcaster ◄── OutboxProcessor / TrackingService
                                        Scheduled jobs (job_locks)
```

## 3. Shipment state machine

```
CREATED → PICKED_UP → IN_TRANSIT → ARRIVED_AT_HUB → OUT_FOR_DELIVERY → DELIVERED
                          ▲              │
                          └──────────────┘   (hub-to-hub: multi-leg journeys)
```
Extras (Phase 8): `DELAYED`, `LOST`, `CANCELLED` (only from CREATED/PICKED_UP). Terminal: DELIVERED, LOST, CANCELLED.
Every accepted transition writes a `shipment_events` row and an `outbox_events` row in the same transaction.

## 4. Data model
Per spec section 5, delivered incrementally by Flyway migrations:
`V1` hubs, routes, shipments · `V2` shipment_events · `V3` shipment_tracking · `V4` outbox_events · `V5` job_locks.

## 5. API surface
```
POST /api/v1/shipments                     create
GET  /api/v1/shipments/{id}
GET  /api/v1/shipments/track/{trackingId}
POST /api/v1/shipments/{id}/status         transition (idempotency key: future work)   [P2]
POST /api/v1/tracking/update                                                    [P3]
GET  /api/v1/tracking/{shipmentId}                                              [P3]
POST /api/v1/hubs · GET /api/v1/hubs · GET /api/v1/routes                       [P1]
WS   /ws  → /topic/shipments/{trackingId}                                       [P5]
```

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
| 9 | Dockerfile, CI (GitHub Actions), deploy | Live URL in README |

## 7. Resume angle
Lead with: state machine correctness, outbox + SKIP LOCKED, optimistic-lock concurrency test, WS + Postgres `LISTEN/NOTIFY` fan-out, lease-based job locks, single-datastore design (no Redis). Add a README architecture diagram and load/concurrency test numbers in Phase 8–9.
