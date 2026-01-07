# 📄 Topic 01: Multi-Server Broadcasting Architecture

## 1. Problem Statement (The Constraints)

### 1.1 The Single-Server Baseline

* **Simple world**: When there's only one server, broadcasting is trivial.
* **Flow**: `Client → Server → In-memory Broadcast → Connected Clients`
* **Why it works**: All connections live in the same process memory.

### 1.2 The Multi-Server Challenge

* **The problem**: How do we deliver a message sent to Server A to clients connected to Server B, C, D?
* **Core requirement**: A shared communication channel visible to all server instances.
* **Constraints**:
    * **Latency Budget**: End-to-end delivery within **100ms**.
    * **Durability**: Messages must not be lost, even if a server crashes.
    * **Scalability**: Must support horizontal scaling to N servers without O(N²) complexity.

## 2. Baseline Architecture & Message Flow

### 2.1 Strategy Exploration

We evaluate three fundamental approaches for server-to-server message propagation:

#### Option A: Database as Message Bus

```
Server A → PostgreSQL (INSERT + optional NOTIFY)
         ↓
Server B, C, D ← (LISTEN or Periodic Polling)
```

* **Pros**:
    * No additional infrastructure (DB already exists).
    * Messages are automatically persisted.
    * Strong consistency guarantees.

* **Cons**:
    * **Latency**: Polling adds 50-100ms+ delay; PostgreSQL LISTEN/NOTIFY has complexity.
    * **DB Load**: Turns DB into a high-throughput message bus, risking overload.
    * **Scalability**: DB becomes bottleneck as server count grows.

#### Option B: Message Broker (Pub/Sub)

```
Server A → Broker (PUBLISH)
         ↓
Server B, C, D ← (SUBSCRIBE)
```

* **Candidates**: Redis Pub/Sub, Kafka
* **Pros**:
    * **Low latency**: Sub-millisecond to single-digit millisecond propagation.
    * **Decoupling**: Servers don't need to know about each other.
    * **Scalability**: Brokers designed for high-throughput fan-out.

* **Cons**:
    * **Additional component**: Requires deploying and managing a broker.
    * **Persistence**: Requires separate strategy (see § 2.2).

#### Option C: Direct Server-to-Server (P2P Gossip)

```
Server A → Direct TCP/HTTP → Server B, C, D
```

* **Pros**:
    * No single point of failure (fully distributed).
    * No external dependencies.

* **Cons**:
    * **Complexity**: O(N²) connections, service discovery, failure detection.
    * **Latency**: Multiple hops in gossip protocols.
    * **Not practical**: For most use cases, operational overhead outweighs benefits.

### 2.2 The Write Path Decision

Once we choose a Broker (Option B), a second critical decision emerges:

**When do we write to the database?**

#### Approach 1: Broker-first (Write-behind)

```
Client → API → Broker → WebSocket (immediate)
                    ↓
                   DB (async write)
```

* **Pros**: Minimal latency (5-10ms end-to-end).
* **Cons**: Message can be delivered but never persisted if async write fails (data loss).

#### Approach 2: DB-first (Write-through)

```
Client → API → DB (commit) → Broker → WebSocket
```

* **Pros**: Durability guaranteed. Broker failure can be retried.
* **Cons**: DB commit adds latency (20-50ms depending on load).

### 2.3 Decision Matrix

| Criteria                       | Option A (DB Bus) | Option B (Broker + DB-first) | Option B (Broker + Broker-first) |
|--------------------------------|-------------------|------------------------------|----------------------------------|
| **Latency**                    | 🟡 50-100ms       | 🟢 20-50ms                   | 🟢 5-10ms                        |
| **Durability**                 | 🟢 Strong         | 🟢 Strong                    | 🔴 At-risk                       |
| **Operational Complexity**     | 🟢 Low            | 🟡 Medium                    | 🔴 High                          |
| **Scalability (Server count)** | 🔴 DB bottleneck  | 🟢 Excellent                 | 🟢 Excellent                     |
| **Our Priority**               | ❌                 | ✅                            | ❌                                |

**Decision**: **Message Broker with DB-first write path**

**Rationale**:

* We prioritize **durability over 10-20ms latency**. A slightly delayed message is acceptable; a lost message is not.
* Redis Pub/Sub provides sub-millisecond broker propagation, keeping total latency under 100ms.
* DB-first allows us to retry broker publish on failure without risking data loss.

## 3. Broadcast Layer Selection

### 3.1 Broker Comparison

| Feature              | Redis Pub/Sub      | Kafka             |
|----------------------|--------------------|-------------------|
| **Latency**          | 🟢 <1ms            | 🟡 5-10ms         |
| **Durability**       | 🔴 Fire-and-forget | 🟢 Persistent log |
| **Throughput**       | 🟢 1M+ msg/sec     | 🟢 1M+ msg/sec    |
| **Operational Cost** | 🟢 Simple          | 🔴 Complex        |
| **Our Use Case**     | ✅ Real-time path   | ❌ Overkill        |

**Decision**: **Redis Pub/Sub**

**Rationale**:

* Since we're doing **DB-first**, we don't need Kafka's durability guarantees in the broker.
* Redis's simplicity and sub-millisecond latency align with our real-time requirements.
* We already use Redis for caching, minimizing operational overhead.

**Reference**: See `experiments/redis-vs-kafka-bench.md` for detailed benchmark results.

## 4. Final Architecture

### 4.1 The Chosen Flow

```
Step 1 (Ingestion):  Client → API Server (HTTPS POST /v1/chat.postMessage)
Step 2 (Persistence): API Server → PostgreSQL (Transaction Commit)
Step 3 (Trigger):     API Server → Redis Pub/Sub (PUBLISH to channel topic)
Step 4 (Fan-out):     Redis → All Subscribed WebSocket Gateway Servers
Step 5 (Delivery):    Gateway Servers → Connected Clients (WebSocket push)
```

**Visual Reference**: See `diagrams/multi-server-message-flow.mmd` for the detailed sequence diagram.

### 4.2 Key Properties

* **Multi-server communication**: Redis Pub/Sub (Step 3 → 4)
* **Durability guarantee**: DB-first (Step 2 before Step 3)
* **Latency profile**:
    * Step 2: ~20-30ms (DB write)
    * Step 3: <1ms (Redis publish)
    * Step 4: <1ms (Redis fan-out)
    * Step 5: <5ms (WebSocket push)
    * **Total**: ~30-40ms (well under 100ms budget)

## 5. Validation Plan (The Proof)

### Scenario A: Multi-Server Broadcast Test

* **Goal**: Verify that a message sent to Server A reaches clients on Server B, C, D.
* **Setup**:
    * 4 server instances
    * 10k clients distributed across servers
    * 1k messages/sec load
* **How to Test**:
    * Use Docker Compose to spin up 4 backend instances + 1 Redis + 1 PostgreSQL
    * Run a load test script that:
        1. Connects 10k WebSocket clients (2.5k per server)
        2. Each client subscribes to a test channel
        3. Sends messages via REST API to random servers
        4. Each client logs: message_id, received_timestamp
    * Collect logs and verify all clients received all messages
* **Success Criteria**:
    * 100% delivery rate across all servers
    * P99 latency < 100ms (Step 1 → Step 5)
* **Maps to**: Section 1.2 (Multi-Server Challenge)

### Scenario B: DB-first Durability Test

* **Goal**: Prove that Redis failure doesn't cause message loss.
* **Setup**:
    * 2 server instances, N WebSocket clients
* **How to Test**:
    * Send 1000 messages via API
    * During message sending (around message 500):
        1. `docker stop redis` (kill Redis container)
        2. Continue sending remaining 500 messages
    * Verify in PostgreSQL: `SELECT COUNT(*) FROM messages` should be 1000
    * Restart Redis: `docker start redis`
    * Manually trigger re-broadcast from DB (or implement a recovery mechanism)
    * Verify all WebSocket clients received all 1000 messages
* **Success Criteria**:
    * 0% data loss
    * All messages in DB can be recovered and re-delivered
* **Maps to**: Section 2.2 (DB-first Rationale)

### Scenario C: Horizontal Scaling Test

* **Goal**: Verify linear scalability as server count increases.
* **Setup**:
    * Test with 2, 4, 8, 16 server instances
    * Maintain 10k clients per server
    * Constant 1k msg/sec load
* **How to Test**:
    * For each server count (2, 4, 8, 16):
        1. Use Docker Compose `--scale backend=N` to spin up N instances
        2. Load test script connects 10k * N clients (evenly distributed)
        3. Send 1k msg/sec for 60 seconds
        4. Measure:
            * P50, P95, P99 latency (client-side timestamp tracking)
            * Redis: `redis-cli INFO stats` for CPU usage, network throughput
            * PostgreSQL: query duration from logs
    * Plot latency vs. server count, Redis metrics vs. server count
* **Success Criteria**:
    * Latency remains stable (no O(N²) degradation)
    * Redis CPU/bandwidth usage scales linearly
* **Maps to**: Section 1.2 (Scalability Constraint)

## 6. Architectural Decision Records

This deep dive leads to the following ADRs:

* **ADR-0001**: Use DB-first write path for message durability
    * Context: See § 2.2 (Write Path Decision)
    * Decision: Commit to PostgreSQL before publishing to Redis

* **ADR-0002**: Use Redis Pub/Sub for server-to-server broadcasting
    * Context: See § 3.1 (Broker Comparison)
    * Decision: Redis Pub/Sub as the real-time fan-out layer

## 7. What's Next?

This architecture establishes the foundation, but introduces new challenges:

* **Race Conditions**: What if Redis (Step 3) propagates before DB transaction (Step 2) is visible to other
  transactions?
* **Hot Channels**: What if a single channel saturates Redis bandwidth?
* **Thundering Herd**: What happens when all Gateway servers restart simultaneously?
* **Message Ordering**: How do we ensure causal consistency across distributed servers?

**→ These challenges are addressed in Deep Dive 02: Massive-scale Fan-out & Consistency**
