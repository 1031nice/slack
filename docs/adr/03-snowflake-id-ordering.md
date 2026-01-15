# ADR-0003: Distributed Message Ordering Strategy (Snowflake ID)

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-10
- **Context**: v0.5 - Causal Consistency Implementation
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 05: Causal Ordering in Distributed Systems](../deepdives/05-causal-ordering.md)
- **Related ADR**: [ADR-0001: Redis Pub/Sub](./01-redis-pubsub-broadcasting.md)

---

## TL;DR (Executive Summary)

**Decision**: Adopt **Snowflake IDs (Distributed Time-based IDs)** for message generation and enforce **Client-Side Ordered Insertion.

**Key Trade-off**: Accept **Rough Ordering** (dependent on clock synchronization) in exchange for **Infinite Scalability** and removing centralized bottlenecks.

**Rationale**: A centralized sequencer (e.g., Redis `INCR`) creates a "False Dependency" between independent channels, limiting sharding capabilities. Snowflake IDs allow each server to generate unique, roughly ordered IDs independently, preserving performance at scale.

---

## Context

### The Problem

In a distributed chat system, messages for a single channel may originate from different servers.
1.  **Network Delays**: A message sent at 10:00:01 might arrive later than one sent at 10:00:02.
2.  **No Global Clock**: Server clocks may drift (Clock Skew), making standard timestamps unreliable for strict ordering.
3.  **Sharding Difficulty**: Using a single global counter (1, 2, 3...) ties all channels together, making it hard to split data across multiple databases.

### Constraints

- **Scalability**: ID generation must not require cross-server coordination (Zero Network I/O).
- **Sortability**: IDs must be k-sortable (roughly ordered by time).
- **Uniqueness**: Collision probability must be zero.

---

## Decision

### What We Chose

1.  **ID Generation**: **Snowflake IDs** (64-bit integers).
    - `Timestamp (41b)` | `Machine ID (10b)` | `Sequence (12b)`
2.  **Ordering Responsibility**: **Client-Side Ordered Insertion.

### Architecture Flow

```
1. Server receives "Send Message" request.
2. Server generates ID locally:
   [ Current Time (ms) ] + [ Server ID ] + [ Local Sequence ]
3. Server saves to DB and publishes to Redis.
4. Client receives messages (possibly out of order).
5. Client buffers for a short window (e.g., 300ms).
6. Client sorts by ID and renders.
```

### Why This Choice (Trade-off Analysis)

| Criteria | **Snowflake IDs** (Selected) | Central Sequencer (Rejected) | Vector Clocks (Rejected) |
| :--- | :--- | :--- | :--- |
| **Scalability** | ✅ **Infinite** (Local CPU) | 🔴 Low (Redis Bottleneck) | 🟢 High |
| **Ordering** | 🟡 Rough (Time-based) | 🟢 Strict (1, 2, 3) | 🟢 Causal (Happens-before) |
| **Complexity** | ✅ **Low** | 🟢 Low | 🔴 Very High |
| **Sharding** | ✅ **Easy** (Independent) | 🔴 Difficult (Coupled) | ✅ Easy |

**Primary Reason**:
We rejected the **Central Sequencer** because it introduces a Single Point of Failure and "False Dependency" between unrelated channels. We rejected **Vector Clocks** because the implementation complexity and data overhead are overkill for a chat application. Snowflake IDs provide the "Sweet Spot" of performance and sufficient ordering for human communication.

---

## Consequences

### Positive Impacts

- **No Bottleneck**: ID generation is purely local and incredibly fast.
- **DB Sharding**: Channels can be moved between databases without coordinating sequence numbers.
- **Index Performance**: Snowflake IDs are integers, which are faster to index in PostgreSQL than UUIDs.

### Negative Impacts & Mitigations

- **Risk: Clock Skew**
  - If a server's clock is behind, it may generate IDs that appear "older".
  - **Mitigation**: Use NTP (Network Time Protocol) to keep skew under 100ms. The Client-Side Reordering window (300ms) absorbs minor skew.

- **Risk: Gap Detection**
  - Unlike sequential IDs (1, 2, 3), Snowflake IDs (1004, 1056, 1201...) have gaps, so clients can't easily know if they missed a message.
  - **Mitigation**: Clients rely on explicit "Sync" APIs or "Cursor-based Pagination" to fetch missing history, rather than checking for numeric gaps.

### Implementation Details

- **ID Structure**:
  - `Sign bit`: 1 bit (Unused)
  - `Timestamp`: 41 bits (Milliseconds since custom epoch)
  - `Node ID`: 10 bits (Configured per server instance, max 1024 nodes)
  - `Sequence`: 12 bits (Per-millisecond counter, max 4096 ops/ms)
- **Client Logic**:
  - Frontend maintains a `Min-Heap` or sorted buffer for incoming real-time messages.
  - Render loop pulls from buffer with a slight delay to ensure older stragglers are inserted correctly.

---

## References

- **[Deep Dive 05: Causal Ordering in Distributed Systems](../deepdives/05-causal-ordering.md)**
  - Detailed analysis of ordering patterns.
- **[Twitter Snowflake](https://github.com/twitter-archive/snowflake/tree/snowflake-2010)**
  - Original implementation of the ID generation algorithm.
- **[Discord Engineering: Generating Unique IDs](https://discord.com/blog/how-discord-stores-billions-of-messages)**
  - Reference for high-scale usage of Snowflake IDs.