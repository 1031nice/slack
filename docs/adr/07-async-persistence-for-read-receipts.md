# ADR-07: Async Persistence for Read Receipts

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-10
- **Context**: v0.4 - Scalable Read Status
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 05: High-Throughput Read Status Management](../deepdives/05-read-status-updates.md)
- **Related ADR**: [ADR-02: Eventual Consistency for Unread Counts](./02-eventual-consistency-for-unread-counts.md)

---

## TL;DR (Executive Summary)

**Decision**: Use **Apache Kafka** to buffer and batch "Read Receipt" updates before persisting them to the database.

**Key Trade-off**: Introduce infrastructure complexity (Kafka) to prevent database saturation during high-traffic bursts.

**Rationale**: "Read Receipt" updates are extremely frequent and bursty. Writing them synchronously to the DB causes lock contention and performance degradation. Kafka acts as a durable shock absorber, allowing the DB consumer to process updates at a controlled, batched rate.

---

## Context

### The Problem
While `Unread Counts` are handled via Redis (ADR-02), `Read Receipts` (e.g., "User A read up to Message X") must be persisted to the database as the source of truth.
*   **Volume**: Every channel scroll or view triggers a write.
*   **Burstiness**: A popular message can trigger thousands of read receipts in seconds.
*   **DB Risk**: Direct DB updates (`UPDATE read_receipts SET ...`) create massive lock contention on user/channel rows.

### Constraints
*   **Durability**: Unlike unread counts, read positions should survive system crashes.
*   **Backpressure**: The system must not crash the DB even if input traffic spikes by 100x.

---

## Decision

### What We Chose
**Pattern D: Write-Behind via Event Streaming (Eventual Consistency)** (as analyzed in Deep Dive 05).

1.  **Producer**: API Server publishes `ReadReceiptEvent` to Kafka topic `read-receipts`. (Fast, Async)
2.  **Broker**: Kafka persists events on disk. (Durable Buffer)
3.  **Consumer**: A background worker pulls events in batches (e.g., 500 at a time).
4.  **Persistence**: The consumer executes **Batch Upsert** to PostgreSQL.

### Why This Choice (Trade-off Analysis)

| Criteria | Direct DB Write | Redis Write-Behind | **Kafka Write-Behind** |
| :--- | :--- | :--- | :--- |
| **Burst Handling** | 🔴 Poor (DB lock) | 🟡 Good (Memory limit) | 🟢 **Excellent** (Disk buffer) |
| **Durability** | 🟢 Immediate | 🔴 Risk (Redis crash) | 🟢 **High** (Replicated Log) |
| **Complexity** | 🟢 Low | 🟡 Medium | 🔴 **High** (Infra) |

**Primary Reason**:
We chose Kafka over Redis for this specific use case because **Durability** and **Buffer Capacity** are critical. Redis memory is expensive and volatile; Kafka disk is cheap and persistent, making it the ideal buffer for massive, bursty write streams like read receipts.

---

## Consequences

### Positive Impacts
- **DB Protection**: The database is completely shielded from traffic spikes. Consumer controls the write rate.
- **Write Efficiency**: Batching 500 updates into 1 DB transaction reduces IOPS by ~99%.
- **No Data Loss**: Even if the DB goes down, events accumulate in Kafka and are processed when the DB recovers.

### Negative Impacts & Mitigations
- **Infrastructure Cost**: Requires running and maintaining a Kafka cluster.
- **Latency**: There is a delay (seconds) between "User reads" and "DB update".
    - **Mitigation**: The client UI optimistically updates the read marker. Real-time sync to other devices happens via WebSocket (Redis), so DB lag is rarely noticed.

---

## Implementation Details

```java
// Producer (API)
kafkaTemplate.send("read-receipts", new ReadReceiptEvent(userId, channelId, timestamp));

// Consumer (Worker)
@KafkaListener(topics = "read-receipts", batch = "true")
public void onMessageBatch(List<ReadReceiptEvent> events) {
    // 1. Deduplicate events (keep latest timestamp per user:channel)
    // 2. Batch Upsert to DB
    repository.saveAll(deduplicatedEvents);
}
```

---

## References

- **[Deep Dive 05: High-Throughput Read Status Management](../deepdives/05-read-status-updates.md)**
- **[Slack Engineering: Scaling Job Queue](https://slack.engineering/scaling-slacks-job-queue/)**
  - References Slack's use of Kafka to buffer high-velocity jobs.
