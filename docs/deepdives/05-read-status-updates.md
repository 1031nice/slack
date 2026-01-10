# 📄 Topic 05: High-Throughput Read Status Management

> **Prerequisites**: This document addresses the scalability challenges of managing "Unread Counts" and "Read Receipts" in a high-volume chat application.

## 1. Problem Statement

### 1.1 The Write-Heavy Workload
Unlike message sending (1 write per message), managing read status generates a massive write multiplier:
*   **Write Amplification (Unread Count)**: When 1 message is sent to a channel with 1,000 members, the system must increment **1,000 unread counters**.
*   **High Frequency (Read Receipt)**: Every time a user opens a channel or scrolls, their "last read" timestamp updates. This creates a relentless stream of DB updates.

### 1.2 The Data Consistency Dilemma
We deal with two types of data with different requirements:
1.  **Unread Count**: Derived data (can be recalculated). Speed is critical.
2.  **Read Receipt**: Source of truth (user state). Durability is critical.

**Goal**: Design a system that handles 10k+ ops/sec for status updates without crashing the DB, while ensuring appropriate durability for each data type.

## 2. Solution Strategy Exploration

We analyze architectural patterns to handle these specific workloads.

### Pattern A: Synchronous DB Write (Strong Consistency)
Directly update the database for every single read/unread event.
*   **Pros**: Simple consistency, zero data loss.
*   **Cons**: **DB bottleneck**. 1,000 users reading a message = 1,000 DB transactions. Unacceptable at scale.

### Pattern B: Redis-Only (Ephemeral State)
Store everything in Redis. No DB sync.
*   **Pros**: Extremely fast (<1ms), simplest implementation.
*   **Cons**: **Data loss risk**. If Redis restarts, users lose their "read position" and everything resets to unread. Acceptable for counts (maybe), unacceptable for receipts.

### Pattern C: Write-Behind via Scheduled Sync (Eventual Consistency)
Use Redis as the primary store, and a background job syncs snapshots to DB.
*   **Target**: **Unread Counts** (`ADR-02`)
*   **Mechanism**: `Redis INCR` → Scheduled Task scans & batches updates to DB.
*   **Rationale**: Unread counts change too fast to capture every transition. Snapshotting the "final count" every few seconds is sufficient.

### Pattern D: Write-Behind via Event Streaming (Eventual Consistency)
Use a message queue (Kafka) to buffer write events before persisting to DB.
*   **Target**: **Read Receipts** (`ADR-07`)
*   **Mechanism**: `API` → `Kafka` → `Consumer Group` → `DB Batch Upsert`.
*   **Rationale**: Read receipts are critical user state. Kafka ensures **durability** (no data loss) while acting as a **shock absorber** (backpressure) to protect the DB from traffic spikes.

## 3. Comparative Analysis

| Feature | Pattern A (DB Sync) | Pattern B (Redis Only) | Pattern C (Scheduled Sync) | Pattern D (Kafka Streaming) |
| :--- | :--- | :--- | :--- | :--- |
| **Throughput** | 🔴 Low | 🟢 Very High | 🟢 Very High | 🟢 High |
| **Durability** | 🟢 Guaranteed | 🔴 None | 🟡 Snapshot (Lossy) | 🟢 Guaranteed (Log) |
| **Latency** | 🔴 High | 🟢 Low (<1ms) | 🟢 Low (Redis) | 🟢 Low (Async) |
| **Complexity** | 🟢 Low | 🟢 Low | 🟡 Medium | 🔴 High |
| **Best For** | MVP | Ephemeral Data | **Unread Counts** | **Read Receipts** |

## 4. Conclusion

We adopt a hybrid strategy based on data characteristics:

1.  **Unread Counts**: Use **Pattern C (Redis + Scheduled Sync)**.
    *   Prioritize speed and deduplication (ZSET).
    *   Accept minor snapshot loss during sync.
    *   *See ADR-02, ADR-03.*

2.  **Read Receipts**: Use **Pattern D (Kafka + Batch Consumer)**.
    *   Prioritize durability and burst handling.
    *   Use Kafka to decouple API write throughput from DB capacity.
    *   *See ADR-07.*

## 5. Related Topics

*   **Massive Fan-out**: How to efficiently trigger the "Increment" event for 100k users.
    *   **→ See Deep Dive 03**
*   **Redis Data Structures**: Why ZSET is used for counts.
    *   **→ See ADR-03**
