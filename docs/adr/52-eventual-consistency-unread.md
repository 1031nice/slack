# ADR-52: Eventual Consistency for Unread Counts

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-10
- **Context**: v0.4 - Distributed Messaging Refinement
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 05: Read Status Updates](../deepdives/05-read-status-updates.md)
- **Related ADR**: [ADR-05: Redis ZSET for Unread Counts](./05-redis-zset-unread-counts.md)

---

## TL;DR

**Decision**: Adopt **Eventual Consistency** for the "Unread Message Count" feature.

**Key Trade-off**: The unread count may be slightly inaccurate (stale) for a few seconds, but the system gains **massive availability and write performance**.

**Rationale**:
*   "Exact Unread Count" is expensive to calculate in real-time.
*   "Approximate Unread Count" is sufficient for UX (e.g., showing "99+" instead of "103").
*   Using Redis as the primary store for this transient data avoids locking the main database rows.

---

## Context

### The Problem

Unlike message sending, "Read Status" management (Unread Counts, Read Receipts) generates an extreme write multiplier.
*   **Write Amplification**: A single message sent to a 1,000-user channel triggers **1,000 unread count increments**.
*   **DB Locking**: Updating 1,000 rows in PostgreSQL simultaneously creates lock contention.

---

## Decision

We decouple the "Unread Count" from the "Messages Table".

1.  **Read Path**: Client fetches unread count from **Redis**.
2.  **Write Path**:
    *   New Message -> Increment Redis (Fast).
    *   Mark Read -> Clear Redis (Fast).
    *   **Background**: Sync to DB slowly (Lazy Persistence).

### Consistency Model

*   **AP over CP**: We prioritize Availability. It is better to show an outdated count than to block message sending.
*   **Self-Healing**: When a user opens the app, we perform a "Full Sync" (Count = DB Count) to correct any drift.

---

## Consequences

- ✅ **Performance**: User actions (reading a message) are instant (<1ms) because they only hit Redis.
- ✅ **Scalability**: No DB bottlenecks on massive channels.
- ⚠️ **Drift**: Redis and DB might disagree if Redis crashes.
    - *Mitigation*: On app launch, the client requests a "Sync" to reset the count.