# ADR-0001: Redis Pub/Sub for Server-to-Server Broadcasting

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-10
- **Context**: v0.4 - Distributed Messaging Refinement
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 01: Multi-Server Broadcasting Architecture](../deepdives/01-multi-server-broadcasting.md)
- **Related ADR**: [ADR-0002: Full Payload Strategy](./02-full-payload-strategy.md) (Proposed)

---

## TL;DR (Executive Summary)

**Decision**: Use **Redis Pub/Sub** for server-to-server message broadcasting.

**Key Trade-off**: Accept fire-and-forget broadcast (no broker durability) in exchange for sub-millisecond latency.

**Rationale**: This decision follows the **DB-first persistence strategy** analyzed in Deep Dive 01. Since message durability is already guaranteed by PostgreSQL, Redis is used solely for real-time signal propagation, where **speed** is the primary constraint.

---

## Context

### The Problem

In a multi-server environment, a WebSocket connection is bound to a specific server instance. When User A (connected to Server 1) sends a message to User B (connected to Server 2), Server 1 needs a way to deliver that message to Server 2 in real-time.

### Constraints

- **Latency Budget**: Total end-to-end latency (DB Write + Broadcast + Delivery) must be **< 100ms**. This strict budget favors memory-based brokers over disk-based ones.
- **Role Separation**: The component should focus purely on "transport" efficiency, as "storage" is already handled by the Database layer.
- **Durability**: Messages must never be lost (handled by DB).

---

## Decision

### What We Chose

**Redis Pub/Sub** as the ephemeral message bus.

### Architecture Flow

```
1. Client sends message
2. API Server saves to PostgreSQL (COMMIT)  <-- Durability Point (Storage)
3. API Server publishes payload to Redis    <-- Real-time Signal (Transport)
4. All Server instances receive Redis event
5. Servers push to connected WebSockets
```

### Why This Choice (Trade-off Analysis)

| Criteria | **Redis Pub/Sub** (Selected) | Kafka (Rejected) | DB Polling (Rejected) |
| :--- | :--- | :--- | :--- |
| **Latency** | ✅ **< 1ms** | ❌ 10-50ms | ❌ 100ms+ |
| **Purpose Fit** | ✅ Real-time Transport | ❌ Storage & Stream | ❌ Storage |
| **Broker Durability** | ❌ None (Fire-and-forget) | ✅ High (Disk log) | ✅ High (Table) |
| **Ops Complexity** | ✅ **Low** | ❌ High | ✅ Low |

**Primary Reason**:
We prioritized **Latency** and **Purpose Fit** based on the analysis of persistence strategies.
*   Redis is an in-memory transport optimized for speed (<1ms), perfectly matching our real-time constraint.
*   Kafka is a persistent log optimized for durability and replayability. Since we chose a **DB-first strategy** to guarantee persistence in PostgreSQL, using Kafka would be redundant (Dual-Storage) and slower due to disk I/O.

---

## Consequences

### Positive Impacts

- **Speed**: Sub-millisecond fan-out ensures the "chat feel" is instant.
- **Architecture Clarity**: Clear separation of concerns—PostgreSQL for data, Redis for speed.
- **Scalability**: Redis handles high throughput with O(1) complexity per subscriber.

### Negative Impacts & Mitigations

- **Risk: Broadcast Reliability (No ACK)**
  - Redis Pub/Sub does not guarantee delivery. If a server disconnects briefly, it misses messages.
  - **Mitigation**: Clients are responsible for fetching "missed messages" from the DB upon reconnection (Sequence-based catch-up).

- **Risk: Global Broadcast**
  - By default, all servers receive all messages, wasting bandwidth.
  - **Mitigation**: Acceptable at current scale. Future optimization will use channel-specific Redis topics.

### Implementation Details

We implement a **Service Orchestration** pattern with **Full Payload**:

1.  **Transactional Save**: `MessageService` saves the message to DB.
2.  **Post-Commit Publish**: `WebSocketMessageService` explicitly calls Redis **after** the `MessageService.createMessage()` call (which ensures DB COMMIT).
3.  **Full Payload**: The Redis message contains the full JSON data, so subscribers don't need to query the DB again (avoiding Race Conditions as analyzed in Deep Dive 02).

---

## References

- **[Deep Dive 01: Multi-Server Broadcasting Architecture](../deepdives/01-multi-server-broadcasting.md)**
  - Source of the "DB-first + Redis" architecture decision.
- **[Deep Dive 02: Consistency Patterns in Async Messaging](../deepdives/02-db-redis-race-condition.md)**
  - Technical basis for the "Full Payload" implementation strategy.
- **[Slack Engineering: Real-time Messaging](https://slack.engineering/real-time-messaging/)**
  - Industry validation for using a lightweight message bus for ephemeral events.
