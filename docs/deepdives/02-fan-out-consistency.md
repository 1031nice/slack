# 📄 Topic 02: Massive-scale Real-time Fan-out & Consistency

> **Prerequisites**: This document assumes the architecture chosen in **Deep Dive 01: Multi-Server Broadcasting Architecture** (DB-first + Redis Pub/Sub).

## 1. Problem Statement (The Constraints)

* **The Challenge**: Addressing the exponential increase in message delivery load as users in a single channel grow.
* **Latency Budget**: Ensuring end-to-end delivery within **100ms**.
* **Consistency vs. Availability**: Managing the trade-off between strict ordering and system uptime.

## 2. Baseline Architecture & Message Flow

### 2.1 The Happy Path (Reference Path)

1. **Step 1 (Ingestion)**: Client API Server (HTTPS POST `/v1/chat.postMessage`).
2. **Step 2 (Persistence)**: API Server  **PostgreSQL** (Transaction Start & Commit).
3. **Step 3 (Trigger)**: API Server  **Redis Pub/Sub** (PUBLISH event).
4. **Step 4 (Fan-out)**: Redis Subscribed **WebSocket Gateways**.
5. **Step 5 (Delivery)**: Gateway Multiple Clients (WebSocket Push).

**Visual Reference**: See `diagrams/baseline-message-flow.mmd` for the detailed sequence diagram.

### 2.2 Key Design Decisions (The "Why")

* **Decision 1: DB-first (Step 2) before Redis (Step 3)**
* *Rationale*: Ensures durability over perceived speed.
* *Reference*: See **Deep Dive 01 § 2.2** (Write Path Decision).


* **Decision 2: Redis Pub/Sub for Broadcast**
* *Rationale*: Sub-millisecond latency for real-time path.
* *Reference*: See **Deep Dive 01 § 3.1** (Broker Comparison) and `experiments/redis-vs-kafka-bench.md`.

## 3. Critical Exceptions: Breaking the Baseline

| Exception           | Location  | Impact  | Likelihood | Core Challenge                       | Ref   |
|---------------------|-----------|---------|------------|--------------------------------------|-------|
| **Race Condition**  | Step 2  3 | 🔴 High | 🟡 Med     | DB commit timing vs. Redis event     | § 3.1 |
| **Thundering Herd** | Step 5    | 🟡 Med  | 🔴 High    | Mass reconnection load               | § 3.2 |
| **Hot Channel**     | Step 4  5 | 🔴 High | 🟢 Low     | Resource saturation by single tenant | § 3.3 |
| **Causal Ordering** | Step 1  5 | 🟡 Med  | 🟡 Med     | Distributed sequence integrity       | § 3.4 |

### 🚩 3.1 Race Condition (Step 2  3)

* **The Gap**: The Redis event reaches the client before the DB transaction is committed/visible, causing "404 Not
  Found" on immediate metadata fetch.
* **Discussion Starters**:
* "How can we leverage Spring's `TransactionSynchronizationManager` to delay the Redis push?"
* "Would an Outbox pattern be overkill for sub-100ms latency requirements?"

### 🚩 3.2 Thundering Herd (Step 5)

* **The Gap**: A gateway crash forces 50k+ clients to reconnect simultaneously, overwhelming the Auth service and DB.
* **Discussion Starters**:
* "How do we implement 'Jitter' in the client-side reconnection logic?"
* "Can we use server-side 'Connection Draining' to shed load gracefully?"

### 🚩 3.3 Hot Channel (Step 4  5)

* **The Gap**: A single channel with 100k members broadcasts a message, clogging the Redis network bandwidth for all
  other channels.
* **Discussion Starters**:
* "Should we shard Redis topics by Workspace ID or Channel ID?"
* "Is it feasible to implement 'Fair Queuing' at the Gateway layer?"

### 🚩 3.4 Causal Ordering (Step 1  5)

* **The Gap**: Network jitter causes a "Reply" message to arrive at the client before the "Original Question" message.
* **Discussion Starters**:
* "What are the trade-offs of using Snowflake IDs vs. Logical Clocks for client-side sorting?"
* "How long should the client buffer out-of-order messages before requesting a sync?"

## 4. Validation Plan (The Proof)

### Scenario A: Whole-System Benchmark

* **Goal**: Verify end-to-end latency () under peak load.
* **Setup**: 10k TPS, 3 Gateway instances, 100k total connections.
* **Success Criteria**:
* P99 latency < 100ms.
* 0% message loss during the 10-minute test.


* **Maps to**: Section 1 (Constraints)

### Scenario B: Race Condition Test

* **Goal**: Prove no "Ghost Messages" even under artificial DB lag.
* **Setup**: Inject 500ms delay in Step 2; Client attempts fetch immediately after Step 5.
* **Success Criteria**:
* 100% success rate on metadata fetch (Step 5 receivers must find the record in DB).


* **Maps to**: Section 3.1 (Race Condition)