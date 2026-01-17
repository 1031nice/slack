# 📄 Topic 07: Distributed Presence System (State Management)

> **Prerequisites**: This document addresses the scalability challenges of tracking "Online/Offline" status for millions of users in a real-time environment.

## 1. Problem Statement

### 1.1 The Heartbeat Storm
Presence systems rely on periodic "Heartbeats" (e.g., every 30 seconds) from clients to prove they are still connected.
*   **Scale**: 1,000,000 concurrent users sending a heartbeat every 30s = **~33,000 writes/sec**.
*   **Fan-out Amplification**: When User A goes "Online", everyone on User A's buddy list (or shared channels) must be notified. If User A is in 50 channels with 100 people each, one status change triggers thousands of events.

### 1.2 The "Ghost" Online Problem
If a server instance crashes, it cannot send "Disconnect" events for the 100k users connected to it.
*   **Challenge**: How do we detect and prune "Ghost" users who are no longer physically connected but still marked as "Online" in the database?

**Goal**: Design a presence system that handles massive heartbeat traffic with sub-second propagation, while maintaining memory efficiency and stale data recovery.

## 2. Solution Strategy Exploration

We analyze three patterns for presence state storage.

### Pattern A: Relational DB Update (Naive)
Update a `last_seen` column in the `users` table for every heartbeat.
*   **Pros**: Simple, consistent.
*   **Cons**: **DB Death**. 33k updates/sec will saturate any standard RDBMS. Presence is ephemeral; disk-based persistence is a waste of IOPS.

### Pattern B: Redis Key-Value (Standard)
Store a key `presence:{userId}` with a TTL of 60 seconds.
*   **Pros**: Extremely fast, automatic expiration (no ghost users).
*   **Cons**: **Memory Overhead**. Storing 1M individual keys with strings takes ~1-2GB of RAM. High CPU usage for 33k `SETEX` commands/sec.

### Pattern C: Redis Bitmaps / HyperLogLog (Aggregated)
Use Bitmaps where `Offset = UserID` and `Value = 1`.
*   **Pros**: **Extreme Memory Efficiency**. 1M users = ~125KB of RAM.
*   **Cons**: No per-user TTL. Requires a manual "sweep" to clear offline users. Hard to implement "Last Seen" timestamps.

### Pattern D: Redis Sorted Sets (ZSET)
Store `UserId` as member and `Timestamp` as score.
*   **Pros**: 
    *   One ZSET can hold 100k+ users.
    *   Easy to find stale users: `ZRANGEBYSCORE(presence, 0, now - 60s)`.
    *   Batch writes possible.
*   **Cons**: Higher memory than Bitmaps, but more functional.

## 3. Comparative Analysis

| Feature | Pattern A (RDBMS) | Pattern B (Redis KV) | Pattern C (Bitmaps) | Pattern D (Redis ZSET) |
| :--- | :--- | :--- | :--- | :--- |
| **Write Throughput** | 🔴 Low | 🟢 High | 🟢 Very High | 🟢 High |
| **Memory Usage** | ⚪ N/A (Disk) | 🔴 High | 🟢 **Excellent** | 🟡 Medium |
| **Stale Cleanup** | 🔴 Slow (Manual) | 🟢 **Auto (TTL)** | 🔴 Hard | ✅ **Easy (Range)** |
| **Feature Rich** | 🟢 High | 🟡 Medium | 🔴 Low | 🟢 High |
| **Verdict** | Rejected | Rejected | Rejected | **Selected** |

## 4. Proposed Architecture: The "ZSET Presence" Model

We adopt **Pattern D (Redis ZSET)** for its balance of performance and cleanup ease.

1.  **Ingestion**: Gateway servers receive WebSocket heartbeats and buffer them for 1 second.
2.  **Batch Write**: Gateways send a batch `ZADD` to Redis: `ZADD presence_set <now> <user1> <now> <user2>...`.
3.  **Active Sweep**: A background worker runs `ZREMRANGEBYSCORE` every 10 seconds to remove users who haven't updated in > 60 seconds.
4.  **Fan-out**: Presence changes (Online -> Offline) are published via Redis Pub/Sub only when a user *enters* or *leaves* the set, not on every heartbeat.

## 5. Experiment Plan (Presence Load Test)

### Scenario: Heartbeat Saturation
*   **Goal**: Compare CPU/Memory cost of 10,000 individual `SETEX` vs 10,000 `ZADD` in batches.
*   **Hypothesis**: Batching ZADD will reduce Redis CPU usage by >70% compared to individual key SETs.
*   **Metrics**: Ops/sec, Memory usage, Redis CPU %.
*   **Implementation**: `experiments/presence-bench/`

## 6. Related Topics

*   **Massive Fan-out**: Notifying buddy lists of status changes.
    *   **→ See Deep Dive 03**
*   **Gateway Separation**: Handling heartbeats at the edge.
    *   **→ See Deep Dive 04**

## 7. Architectural Decision Records

*   **ADR-07**: Redis ZSET for Presence Tracking (Proposed)