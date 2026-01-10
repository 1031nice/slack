# ADR-02: Eventual Consistency for Unread Counts

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-10
- **Context**: v0.4 - Scalable Read Status
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 05: High-Throughput Read Status Management](../deepdives/05-read-status-updates.md)

---

## TL;DR (Executive Summary)

**Decision**: Adopt **Redis Write-Behind Caching (Eventual Consistency)** strategy.

**Key Trade-off**: Accept a **consistency lag (< 10 seconds)** between Redis and Database in exchange for handling massive concurrent write throughput.

**Rationale**: Updating the database synchronously for every message read/unread event (10k+ ops/sec) is practically impossible and prohibitively expensive. By buffering writes in Redis and batch-syncing to PostgreSQL, we achieve high performance and durability.

---

## Context

### The Problem

Unlike message sending, "Read Status" management (Unread Counts, Read Receipts) generates an extreme write multiplier.
*   **Write Amplification**: A single message sent to a 1,000-user channel triggers **1,000 unread count increments**.
*   **High Frequency**: Every channel view resets counters and updates timestamps.

### Constraints

- **Throughput**: Must handle bursts of 10,000+ status updates per second.
- **DB Protection**: Direct DB writes would cause lock contention and CPU saturation.
- **Durability**: User state (unread counts) must eventually persist to disk to survive cache failures.

---

## Decision

### What We Chose

**Pattern C: Write-Behind Caching** (as analyzed in Deep Dive 05).

1.  **Primary Store (Write)**: **Redis** receives all increments and updates instantly.
2.  **Buffer**: Updates accumulate in Redis structures (Hashes/Sets).
3.  **Sync (Persistence)**: A background worker periodically (e.g., every 5-10s) reads the buffer and executes **Batch Updates** to PostgreSQL.
4.  **Read**: Clients read directly from Redis (Cache Hit) or fallback to DB.

### Why This Choice (Trade-off Analysis)

| Criteria | Pattern A (DB Sync) | Pattern B (Redis Only) | **Pattern C (Write-Behind)** |
| :--- | :--- | :--- | :--- |
| **Throughput** | 🔴 Low | 🟢 Very High | 🟢 **High** |
| **Durability** | 🟢 Guaranteed | 🔴 None | 🟡 **Eventual (<10s lag)** |
| **DB Load** | 🔴 High Risk | 🟢 Zero | 🟢 **Low (Batched)** |

**Primary Reason**:
We prioritized **Throughput** and **DB Protection**. The sheer volume of read status updates makes synchronous DB writing unfeasible. We accept the risk of minor data loss (last few seconds) in a catastrophic Redis crash as a reasonable trade-off for system stability.

---

## Consequences

### Positive Impacts

- **Performance**: User actions (reading a message) are instant (<1ms) because they only hit Redis.
- **Scalability**: The database sees only ~1% of the actual write traffic due to batching.
- **Cost**: Drastically reduces DB IOPS requirements.

### Negative Impacts & Mitigations

- **Risk: Consistency Lag**
  - The DB is always slightly behind Redis.
  - **Mitigation**: This is acceptable for "Unread Counts" (metadata). We ensure the "Source of Truth" for reads is Redis, so users always see their own updates instantly.

- **Risk: Data Loss on Crash**
  - If Redis crashes before a sync, updates in the buffer are lost.
  - **Mitigation**:
    - Enable Redis AOF (Append Only File) for persistence.
    - Short sync intervals (5-10s) minimize the loss window.
    - On recovery, restore state from DB (even if slightly stale).

### Implementation Details

*   **Redis Data Structure**: Use `Sorted Sets` or `Hashes` to store unread counts per user/channel.
*   **Sync Worker**: Implement a scheduled task (Spring `@Scheduled`) to flush dirty keys to the DB.

---

## References

- **[Deep Dive 05: High-Throughput Read Status Management](../deepdives/05-read-status-updates.md)**
  - Full analysis of write patterns and consistency strategies.
- **[Slack Engineering: Real-time Messaging](https://slack.engineering/real-time-messaging/)**
  - References Slack's use of similar caching layers for user state.
