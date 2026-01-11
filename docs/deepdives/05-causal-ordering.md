# 📄 Topic 05: Causal Ordering in Distributed Systems

> **Prerequisites**: This document addresses the challenge of maintaining message order across distributed servers, network partitions, and asynchronous delivery paths.

## 1. Problem Statement

### 1.1 The Illusion of Global Order
In a single-server chat app, message order is trivial: `INSERT` order = Message order.
In a distributed system, "Order" is an illusion:
*   **Network Delays**: Message A (sent at 10:00:01) might arrive at Server 2 *after* Message B (sent at 10:00:02).
*   **Clock Skew**: Server A's clock might be 500ms ahead of Server B's clock.
*   **Concurrency**: Two users sending messages to the same channel simultaneously.

### 1.2 The Sequencer Bottleneck
Using a centralized counter (e.g., `Redis INCR` or Auto-Increment DB ID) guarantees strict ordering but introduces:
*   **Single Point of Failure**: If the sequencer dies, no one can chat.
*   **Performance Bottleneck**: All writes must serialize through one node.
*   **False Dependency**: Sharding becomes difficult because Channel A and Channel B share the same global counter mechanism.

**Goal**: Establish a scalable ordering strategy that guarantees "Causal Consistency" (replies appear after original messages) without a centralized bottleneck.

## 2. Solution Strategy Exploration

We evaluate three architectural patterns for message ordering.

### Pattern A: Centralized Sequencer (Strict Ordering)
Use Redis `INCR` or DB `AUTO_INCREMENT` to assign a monotonic sequence number (1, 2, 3...) to every message in a channel.

*   **Mechanism**: `lock(channel_id) → seq++ → save`.
*   **Pros**: Simple, gaps are easily detected (missing "4" between "3" and "5").
*   **Cons**: **Hard Scalability Limit**. Requires coordination for every message. High latency in multi-region setups.

### Pattern B: Logical Clocks (Lamport / Vector Clocks)
Use logical counters attached to every event to track "happens-before" relationships.

*   **Mechanism**: Messages carry `(NodeID, Counter)` metadata.
*   **Pros**: Mathematically perfect causal ordering without a central server.
*   **Cons**: **Implementation Complexity**. Clients must track vector state. Overkill for chat applications (useful for CRDTs/docs).

### Pattern C: Distributed Time-based IDs (Snowflake / ULID)
Generate unique, roughly-ordered IDs independently on each application server using physical time + machine ID + sequence.

*   **Mechanism**: `Timestamp (41b) | Machine ID (10b) | Sequence (12b)`.
*   **Pros**:
    *   **Infinite Scalability**: No coordination between servers.
    *   **Sortable**: `ID_A < ID_B` implies `Time_A < Time_B`.
    *   **High Performance**: Generation is purely local (CPU-bound).
*   **Cons**:
    *   **Clock Skew**: If Server A's clock is slow, its messages might appear "older" than they are.
    *   **Gap Detection**: Impossible to know if a message is missing (no sequence 1, 2, 3).

### Pattern D: Partition-Based Ordering (Kafka)
Route all messages for a specific channel to a dedicated partition in a distributed log (Kafka).

*   **Mechanism**: `Producer → Kafka Partition (Key=ChannelID) → Consumer`.
*   **Pros**:
    *   **Strict Ordering**: Messages within a partition are guaranteed to be ordered.
    *   **Durability**: Log-based storage allows replayability.
*   **Cons**:
    *   **Latency Overhead**: Disk-based writes + polling consumer model introduces 10-50ms+ latency (unacceptable for real-time chat).
    *   **Hot Partition**: A massive channel (100k users) can overwhelm a single partition/broker.
    *   **Operational Complexity**: Managing a Kafka cluster is significantly more complex than Redis.

## 3. Comparative Analysis

| Feature | Pattern A (Sequencer) | Pattern B (Logical Clock) | Pattern C (Snowflake) | Pattern D (Kafka) |
| :--- | :--- | :--- | :--- | :--- |
| **Ordering Guarantee** | 🟢 Total Order | 🟢 Causal Order | 🟡 Roughly Ordered | 🟢 Total Order (Partition) |
| **Scalability** | 🔴 Low (Bottleneck) | 🟢 High | 🟢 **Very High** | 🟡 Medium (Hot Partition) |
| **Coordination** | 🔴 Required (Lock) | 🟢 None | 🟢 **None** | 🟡 Partition Assignment |
| **Latency** | 🟡 Medium | 🟢 Low | 🟢 **Very Low** | 🔴 High (>10ms) |
| **Slack's Choice** | ❌ (Legacy) | ❌ | ✅ **(Current)** | ❌ |

## 4. Conclusion

We adopt **Pattern C (Distributed Time-based IDs)** combined with **Soft Ordering (Insertion)** on the client side.

1.  **Why not Kafka (Pattern D)?**
    *   Latency overhead (disk I/O + polling) violates our <100ms real-time budget.
    *   Snowflake IDs provide "good enough" ordering with superior speed.

2.  **ID Generation**: Use **Snowflake IDs**. (See `ADR-04`)

3.  **Ordering Responsibility**: **Client-Side Soft Ordering**.
    *   **Strategy**: "Insert at Correct Position".
    *   **Mechanism**: When a message arrives, the client inserts it into the UI list based on its Snowflake ID sort order.
    *   **Benefit**: Zero added latency. Late-arriving messages (Jitter) simply "snap" into their correct historical place.
    *   **Rejected Option**: "Buffer & Wait" (Holding messages for 200ms) was rejected because it introduces artificial latency that degrades the real-time feel.

4.  **Conflict Resolution**: Snowflake ID (Timestamp + MachineID + Sequence) is the deterministic tie-breaker.

## 5. Experiment Plan (Causal Ordering Lab)

To validate **Pattern C (Snowflake IDs + Soft Ordering)**, we verify that the client can maintain a sorted state despite network jitter, without artificial delays.

### Scenario: Network Jitter Simulation
*   **Goal**: Verify that **Soft Ordering (Insertion)** achieves 100% eventual consistency with 0ms added latency, compared to the "Buffer & Wait" strategy.
*   **Hypothesis**: Insertion logic allows the UI to eventually reflect the perfect order without the 200ms penalty of buffering.
*   **Setup**:
    *   **Producer**: Generates 1,000 messages with Snowflake IDs.
    *   **Network**: Introduces random jitter (0-100ms).
    *   **Consumer (Client)**:
        *   **Strategy A (Buffer & Wait)**: Hold 200ms, then render. (Rejected Benchmark)
        *   **Strategy B (Soft Ordering)**: Render immediately at `sorted_index`. (Selected Solution)
*   **Success Criteria**:
    *   Strategy B Final State: 100% sorted.
    *   Strategy B Latency: 0ms (processing time only).
*   **Implementation**: `experiments/causal-ordering-lab/`

## 6. Related Topics



*   **Event-Driven Architecture**: Moving away from monolithic sequences.

    *   **→ See ADR-04**

*   **Client-Side Reordering**: Handling out-of-order delivery.

    *   **→ See ADR-03**
