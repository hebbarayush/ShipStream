# 🚀 Real-Time Logistics & Shipment Tracking System

## 1. Overview

A backend-focused, production-grade system to manage shipments across hubs with **real-time tracking**, **event-driven processing**, and **concurrency-safe design**.

This project complements RentalOps by demonstrating:

* Real-time updates (WebSockets)
* Event-driven architecture (Outbox pattern)
* Distributed job handling (DB locks)
* State machines & idempotency

---

## 2. System Goals

### Core Goals

* Track shipment lifecycle end-to-end
* Provide real-time location updates
* Ensure correct state transitions
* Handle concurrent updates safely
* Work in multi-instance deployment

### Non-Goals

* Advanced route optimization (keep basic)
* External integrations (optional later)

---

## 3. High-Level Architecture

```
Clients (React / Mobile)
        ↓
   Spring Boot API
        ↓
 ┌───────────────────────────────┐
 │ Core Services                 │
 │-------------------------------│
 │ Shipment Service              │
 │ Tracking Service              │
 │ Routing Service               │
 │ Notification Service          │
 └───────────────────────────────┘
        ↓
 ┌──────────────┬───────────────┐
 │ PostgreSQL   │ Redis         │
 │ (source of truth) │ (cache)  │
 └──────────────┴───────────────┘
        ↓
 Background Workers (Outbox + Jobs)
```

---

## 4. Shipment Lifecycle (State Machine)

```
CREATED
  ↓
PICKED_UP
  ↓
IN_TRANSIT
  ↓
ARRIVED_AT_HUB
  ↓
OUT_FOR_DELIVERY
  ↓
DELIVERED
```

### Rules

* Invalid transitions must be blocked
* All transitions must be recorded as events

---

## 5. Database Design

### shipments

```sql
id BIGSERIAL PRIMARY KEY
tracking_id VARCHAR UNIQUE
sender_address TEXT
receiver_address TEXT
status VARCHAR
current_hub_id BIGINT
created_at TIMESTAMP
updated_at TIMESTAMP
version BIGINT DEFAULT 0
```

---

### shipment_events (audit log)

```sql
id BIGSERIAL PRIMARY KEY
shipment_id BIGINT
event_type VARCHAR
payload JSONB
created_at TIMESTAMP
```

---

### shipment_tracking (latest state)

```sql
shipment_id BIGINT PRIMARY KEY
latitude DOUBLE PRECISION
longitude DOUBLE PRECISION
last_updated TIMESTAMP
```

---

### hubs

```sql
id BIGSERIAL PRIMARY KEY
name VARCHAR
location VARCHAR
capacity INT
```

---

### routes

```sql
id BIGSERIAL PRIMARY KEY
source_hub BIGINT
destination_hub BIGINT
distance INT
```

---

### outbox_events

```sql
id UUID PRIMARY KEY
event_type VARCHAR
payload JSONB
status VARCHAR
created_at TIMESTAMP
processed_at TIMESTAMP
retry_count INT
```

---

### job_locks (reuse pattern)

```sql
job_name VARCHAR PRIMARY KEY
locked_until TIMESTAMP
locked_by VARCHAR
updated_at TIMESTAMP
```

---

## 6. Core Services

### ShipmentService

* Create shipment
* Update status
* Validate transitions
* Emit events

---

### TrackingService

* Update location
* Store latest location (DB + Redis)
* Push real-time updates

---

### RoutingService

* Assign route between hubs
* Basic shortest path logic

---

### NotificationService

* Send updates (future: email/SMS)
* Consume events

---

## 7. Real-Time Design

### Flow

```
Agent → POST /tracking/update
        ↓
DB + Redis updated
        ↓
WebSocket broadcast
        ↓
Client UI updates
```

---

## 8. Event-Driven Architecture

### Pattern

* Write event to `outbox_events` inside transaction
* Background processor reads and processes

### Example

```
ShipmentCreated → Outbox → Notification + Tracking
```

---

## 9. Concurrency & Safety

### Techniques Used

* Optimistic locking (`version`)
* Idempotent updates
* DB constraints (unique tracking_id)
* State validation

---

## 10. APIs

### Shipment APIs

```
POST   /shipments
GET    /shipments/{id}
GET    /shipments/track/{trackingId}
```

---

### Tracking APIs

```
POST   /tracking/update
GET    /tracking/{shipmentId}
```

---

### Admin APIs

```
POST   /hubs
GET    /routes
```

---

## 11. Background Jobs

* ETA recalculation
* Delay detection
* Cleanup stale tracking

Use:

* `job_locks` table
* `@Transactional(REQUIRES_NEW)` pattern

---

## 12. Tech Stack

* Java 17+
* Spring Boot
* PostgreSQL
* Redis
* WebSockets

Optional:

* Kafka / RabbitMQ
* Docker

---

# 🧱 PHASED BUILD PLAN

---

## 🟢 Phase 1: Core Setup

### Goal

Basic shipment CRUD + DB schema

### Tasks

* Setup Spring Boot project
* Create entities (Shipment, Hub, Route)
* Setup PostgreSQL
* Create repositories
* Basic APIs:

  * Create shipment
  * Get shipment

---

## 🟢 Phase 2: State Machine

### Goal

Correct lifecycle handling

### Tasks

* Implement status enum
* Add transition validation logic
* Add service layer rules
* Add tests for invalid transitions

---

## 🟢 Phase 3: Tracking System

### Goal

Location updates

### Tasks

* Create tracking table
* Implement `/tracking/update`
* Store latest location
* Return tracking data

---

## 🟢 Phase 4: Outbox Pattern

### Goal

Event-driven architecture

### Tasks

* Create `outbox_events` table
* Emit events in shipment service
* Build OutboxProcessor
* Handle retries

---

## 🟢 Phase 5: Real-Time (WebSockets)

### Goal

Live updates

### Tasks

* Setup WebSocket config
* Push updates on location change
* Build simple frontend listener

---

## 🟢 Phase 6: Redis Caching

### Goal

Fast reads

### Tasks

* Cache tracking data
* Cache shipment summary
* Add TTL

---

## 🟢 Phase 7: Background Jobs

### Goal

Production readiness

### Tasks

* Add `job_locks` table
* Implement lock service
* Build scheduled jobs:

  * delay detection
  * cleanup

---

## 🟢 Phase 8: Advanced Features

### Optional but strong

* Delivery ETA calculation
* Simulated GPS updates
* Failure handling (lost/delayed shipments)

---

## 🟢 Phase 9: Deployment

### Tasks

* Dockerize app
* Deploy backend (Render / Railway)
* Use managed Postgres + Redis

---

# 🧠 Resume Description

“Built a real-time logistics tracking system with event-driven architecture, WebSocket-based live updates, Redis caching, and distributed job coordination using DB locks and outbox pattern.”

---

# ⚠️ Important Guidelines

* Do NOT make it CRUD-heavy
* Focus on system design + correctness
* Ensure concurrency safety
* Implement real-time properly

---

# 🚀 End Goal

A production-style backend demonstrating:

* real-time systems
* event-driven processing
* distributed safety
