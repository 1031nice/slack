# 📄 Topic 02: Consistency Patterns in Async Messaging

> **Prerequisites**: This document analyzes architectural patterns for managing consistency between Database transactions and Asynchronous Messaging (Redis Pub/Sub).

## 1. Problem Statement

### 1.1 The Challenge: Speed Mismatch
In distributed systems, a fundamental speed mismatch exists between message brokers and databases:
*   **Message Broker (Redis)**: Propagates events in **< 1ms**.
*   **Database (PostgreSQL)**: Commits transactions in **5-20ms** (single node) or replicates to slaves in **100-500ms**.

### 1.2 The Race Condition & Consistency Dimensions
This mismatch creates concurrency gaps. To analyze them effectively, we distinguish between two dimensions of consistency:

1.  **Push Consistency (Real-time Delivery)**
    *   *Definition*: The guarantee that a real-time notification contains or references data that is valid and visible at the moment of receipt.
    *   *Failure Mode*: User receives "New Message" alert, but the data is missing (ghost notification).

2.  **Pull Consistency (Client Fetching)**
    *   *Definition*: The guarantee that a manual data fetch (API call) returns the most up-to-date state, reflecting all received notifications.
    *   *Failure Mode*: User refreshes the list immediately after an alert, but sees an outdated list (stale read).

## 2. Solution Strategy Exploration

We analyze three architectural patterns to address these consistency gaps.

### Pattern A: Full Payload in Event (Push Model)
Include the **entire data object** (Content, User, Timestamp, etc.) inside the notification itself.

*   **Mechanism**: The publisher serializes the final state of the entity into the event payload.
*   **Impact on Push Consistency**: ✅ **Solved**. By carrying the data *with* the event, we bypass the DB read entirely. The data is available the instant the event arrives.
*   **Impact on Pull Consistency**: ❌ **Unresolved**. If the user manually refreshes (Pull) immediately after the event, they usually hit a Read Replica, which may lag behind the Master.
*   **Advanced Variation (Slack's Flannel)**: To further optimize, systems like Slack use "Query Push" or Edge Caching to push not just the message, but also *related data* (e.g., sender profiles) beforehand, preventing generic "Pull" requests entirely.

### Pattern B: ID-Only Notification + Intelligent Routing (Pull Model)
Send only the `ID`. Clients must fetch data via API. The API layer manages consistency.

*   **Mechanism**: Publisher sends `ID`. Client requests `GET /resource/{id}`. API routes "recent" requests to Master DB.
*   **Impact on Push Consistency**: ⚠️ **Risky**. Requires strict `Transaction Synchronization` (wait for commit before publish) to ensure the ID exists in the DB when the client fetches it.
*   **Impact on Pull Consistency**: ✅ **Solved (via Routing)**. By forcing recent fetches to the Master DB, we guarantee fresh data even during replication lag.

### Pattern C: Cache-First (Write-Through)
Write to a global cache (Redis) synchronously with the DB transaction.

*   **Mechanism**: Readers always check the Cache before the DB. Writers update Cache immediately.
*   **Impact on Push Consistency**: ✅ **Solved**. Data is in Cache before the event fires.
*   **Impact on Pull Consistency**: ✅ **Solved**. Both Push and Pull requests hit the same Cache, which is updated instantly by the writer.

## 3. Comparative Analysis

| Feature | Pattern A (Full Payload) | Pattern B (ID + Routing) | Pattern C (Cache-First) |
| :--- | :--- | :--- | :--- |
| **Push Consistency** | ✅ **Guaranteed** (Payload) | ⚠️ Requires Sync & Routing | ✅ Guaranteed (Cache) |
| **Pull Consistency** | ❌ **Vulnerable to Lag** | ✅ **Guaranteed** (Routing) | ✅ Guaranteed (Cache) |
| **User Latency** | 🚀 **Fastest** (1-hop) | 🐢 Slower (2-hops) | ⚡ Fast (1.5-hops) |
| **Complexity** | 📉 **Low** | 📈 High | 📈 High |
| **Bandwidth** | ⚠️ High | ✅ Low | ✅ Low |

## 4. Conclusion

*   **Pattern A (Full Payload)** is the optimal choice for **chat applications** where low latency and immediate feedback ("Push") are the primary UX drivers. The "Pull" inconsistency is a known trade-off, acceptable for the simplicity it offers.
*   **Pattern B** is better suited for systems with **massive payloads** (e.g., document updates) where broadcasting full data is infeasible.
*   **Pattern C** is the gold standard for high-scale systems but introduces significant **maintenance complexity**.

## 5. Related Topics



*   **Massive Fan-out**: Bandwidth implications of Pattern A.

    *   **→ See Deep Dive 03**

*   **Causal Ordering**: Ordering guarantees for asynchronous events.

    *   **→ See Deep Dive 04**



## 6. Architectural Decision Records



*   **ADR-02**: Full Payload Strategy for Consistency

    *   Context: See § 2 (Solution Strategy)

    *   Decision: Include full payload in WebSocket events to prevent race conditions.
