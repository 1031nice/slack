# 📄 Topic 05: High-Throughput Read Status Management

> **Prerequisites**: This document addresses the scalability challenges of managing "Unread Counts" and "Read Receipts" in a high-volume chat application.

## 1. Problem Statement

### 1.1 The Write-Heavy Workload
Unlike message sending (1 write per message), managing read status generates a massive write multiplier:
*   **Fan-out Write**: When 1 message is sent to a channel with 1,000 members, the system must increment **1,000 unread counters**.
*   **Frequent Updates**: Every time a user opens a channel, their unread count must reset to 0 and their "last read" timestamp must update.

### 1.2 The Consistency Dilemma
*   **Database (PostgreSQL)**: Provides durability and strong consistency but creates a bottleneck under high write concurrency (locking, disk I/O).
*   **Cache (Redis)**: Provides blazing speed and atomic increments but risks data loss during crashes.

**Goal**: Design a system that handles 10k+ ops/sec for unread updates without crashing the DB, while ensuring data durability across device sessions.

## 2. Solution Strategy Exploration

We analyze three architectural patterns to handle this specific workload.

### Pattern A: Synchronous DB Write (Strong Consistency)
Directly update the database for every single read/unread event.

*   **Flow**: `Event` → `DB UPDATE` → `Cache Invalidate`
*   **Pros**:
    *   **Simple Consistency**: Source of truth is always the DB. No synchronization logic needed.
    *   **Durability**: Zero data loss guarantee.
*   **Cons**:
    *   **Performance Bottleneck**: Database row locks (e.g., updating user rows) severely limit throughput.
    *   **Cost**: Scaling DB writes is expensive compared to scaling Redis.

### Pattern B: Redis-Only (Ephemeral State)
Store unread counts and read receipts entirely in Redis.

*   **Flow**: `Event` → `Redis INCR`
*   **Pros**:
    *   **Maximum Performance**: In-memory operations are incredibly fast (<1ms).
    *   **Zero DB Load**: The database is completely bypassed.
*   **Cons**:
    *   **Data Loss Risk**: If Redis restarts, all unread counts disappear. Users see "0 unread" even if they missed messages.
    *   **Cold Start**: New devices or cleared caches have no data source to restore from.

### Pattern C: Write-Behind Caching (Eventual Consistency)
Use Redis as the primary data store for writes, and asynchronously persist batched updates to the DB.

*   **Flow**: `Event` → `Redis INCR` (Immediate) → `Background Sync` (Every N seconds) → `DB Batch UPDATE`
*   **Pros**:
    *   **High Throughput**: User-facing latency is minimal (Redis speed).
    *   **Durability**: Data is periodically saved to DB.
    *   **Reduced DB Pressure**: 1,000 increments become 1 batch update (Write Coalescing).
*   **Cons**:
    *   **Consistency Lag**: A small window (e.g., 5-10s) exists where the DB is stale.
    *   **Recovery Complexity**: Requires logic to restore Redis from DB after a crash.

## 3. Comparative Analysis

| Feature | Pattern A (DB Sync) | Pattern B (Redis Only) | Pattern C (Write-Behind) |
| :--- | :--- | :--- | :--- |
| **Throughput** | 🔴 Low | 🟢 Very High | 🟢 High |
| **Write Latency** | 🔴 High (10-50ms) | 🟢 Low (<1ms) | 🟢 Low (<1ms) |
| **Durability** | 🟢 Guaranteed | 🔴 None | 🟡 Good (Checkpoint) |
| **DB Load** | 🔴 High | 🟢 None | 🟢 Low (Batched) |
| **Complexity** | 🟢 Low | 🟢 Low | 🔴 High (Sync Logic) |

## 4. Conclusion

*   **Pattern A** is suitable for low-traffic MVPs where simplicity outweighs performance.
*   **Pattern B** works for ephemeral data (e.g., "Online Status") but is unacceptable for persistent user state like Unread Counts.
*   **Pattern C** is the industry standard for high-scale chat systems (like Slack/Discord). It trades strict consistency for massive write throughput and DB protection.

## 5. Related Topics

*   **Massive Fan-out**: How to efficiently trigger the "Increment" event for 100k users.
    *   **→ See Deep Dive 03**
*   **Redis Data Structures**: Choosing between `Hashes`, `Sorted Sets`, or `Bitmaps` for storage optimization.
