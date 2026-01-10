# ADR-0006: Async Persistence for Read Receipts

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-10
- **Context**: v0.4 - Distributed Messaging Refinement
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 05: Read Status Updates](../deepdives/05-read-status-updates.md)
- **Related ADR**: [ADR-0005: Redis ZSET for Unread Counts](./05-redis-zset-unread-counts.md)

---

## TL;DR (Executive Summary)

**Decision**: Use **Asynchronous Persistence** (Write-Behind) for Read Receipt data.

**Key Trade-off**: Accept a small window of data loss (read status revert) in case of crash, in exchange for **massive DB write reduction**.

**Rationale**: "Marking as read" is a high-volume, low-criticality operation. Writing to the DB synchronously for every read action creates unnecessary IOPS. By buffering in Redis/Kafka and flushing to DB in batches, we protect the database from write spikes.

---

## Context

### The Problem

While `Unread Counts` are handled via Redis (ADR-0005), `Read Receipts` (e.g., "User A read up to Message X") must be persisted to the database as the source of truth.
*   **High Volume**: Every click triggers a write.
*   **Burstiness**: A popular message can trigger thousands of read receipts in seconds.

---

## Decision

### What We Chose

**Write-Behind Pattern** (Buffering).

### Architecture Flow

```
1. Client sends "Read" event.
2. Server updates Redis ZSET (Real-time view).
3. Server pushes event to Internal Memory Buffer (or Kafka).
4. Background Worker aggregates events (e.g., every 5 sec or 100 items).
5. Worker performs Bulk UPSERT to PostgreSQL `read_receipts` table.
```

### Why This Choice

| Strategy | Latency | DB Writes | Durability |
| :--- | :--- | :--- | :--- |
| **Write-Through** | High (Wait for DB) | 1:1 (High) | ✅ Strong |
| **Write-Behind** | **Low** (Ack immediately) | **1:N (Low)** | ⚠️ Risk of loss |

**Primary Reason**:
Performance. The risk of a user seeing an "unread badge" reappear after a server crash is acceptable compared to the cost of hammering the DB with thousands of tiny transactions.

---

## Consequences

### Positive Impacts

- **DB Protection**: Absorbs massive "Read" spikes without affecting message send/receive performance.
- **Response Time**: "Mark as Read" API becomes instant.

### Negative Impacts & Mitigations

- **Risk: Data Loss**
  - If the buffer is in memory and the server crashes, recent read receipts are lost.
  - **Mitigation**: Acceptable trade-off. Users will just read the message again. For higher durability, use Kafka instead of memory buffer.

---

## References

- **[Deep Dive 05: Read Status Updates](../deepdives/05-read-status-updates.md)**
  - Analysis of the write amplification problem.
