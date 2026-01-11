# 📄 Topic 03: Massive Fan-out Architecture

## 1. Problem Statement

### 1.1 The "Mega-Channel" Scenario
Consider a workspace with **100,000 users** (e.g., Uber, Airbnb scale).
When a message is sent to `#announcements`:
*   **Scale**: 100k connected WebSocket clients.
*   **Distribution**: These clients are sharded across **100+ Backend Server Instances**.
*   **Definition**: In this document, we refer to these backend instances as **"Logical Gateways"** because they handle the physical WebSocket connections, regardless of whether they also host business logic.
*   **The Challenge**: How to deliver 1 message to 100 Gateways and then to 100k Users within **200ms**?

### 1.2 Failure Modes at Scale
1.  **Redis Saturation (Bandwidth)**:
    *   In **Deep Dive 02**, we chose **Full Payload** pushes to ensure consistency.
    *   *Consequence*: A 1KB message sent to a channel with 1,000 subscribed Gateways generates **1MB of outgoing traffic from Redis** instantly.
    *   *Risk*: Redis Network Bandwidth becomes the bottleneck before CPU.
2.  **Thundering Herd (Reconnection Storms)**:
    *   Since we use a "Push" model, read-time DB load is low.
    *   *Risk*: If a Gateway instance crashes, 10k users reconnect simultaneously, causing a massive spike in Auth and Initial State fetch (DB Pull).

## 2. Advanced Architectural Strategies

We evaluate strategies used by hyper-scale chat systems (Discord, Slack, Messenger).

### Tier 1: Connection Sharding + Redis Pub/Sub (Current Baseline)
*   **Architecture**:
    *   Clients are sharded across N Backend Servers (Gateways) by Load Balancer.
    *   Every Server Instance subscribes to the Redis Channel `topic/channel.{id}`.
*   **Flow**: `API → Redis PUBLISH → All Servers (Fan-out) → Connected Clients`.
*   **Limit**:
    *   Redis becomes the bottleneck. Throughput is limited by Redis CPU/Bandwidth.
    *   **Broadcast Storm**: If 1000 Gateways listen to `#general`, Redis must replicate the packet 1000 times.
*   **Suitability**: Good for < 10k users.

### Tier 2: Application-Level Multicast (Direct Routing)
*   **Architecture**:
    *   Introduce a **"Session Registry"** (e.g., Redis Hash or Etcd) to track which User is on which Gateway.
    *   Separate **Gateway Servers** from **Logic Servers**.
*   **Flow**: `Logic Service → Query Session Registry → Direct gRPC to target Gateways → Connected Clients`.
*   **The "Redis Bypass" Effect**:
    *   Redis is NO LONGER used for the heavy lifting of message replication (PUBLISH).
    *   Logic servers only send messages to the *specific* Gateways that have active subscribers for that channel.
    *   **Result**: Redis bandwidth scales with the number of *active sessions* (metadata), not the *total message broadcast volume*.
*   **Pros**:
    *   Eliminates Redis Fan-out bottlenecks.
    *   Significant bandwidth savings.
*   **Cons**: High complexity (Service Discovery, Session State consistency).
*   **Suitability**: Necessary for > 100k users.

### Tier 3: Edge Aggregation (The "Flannel" Approach)
*   **Concept Source**: [Flannel: An Application-Level Edge Cache to Make Slack Scale](https://slack.engineering/flannel-an-application-level-edge-cache-to-make-slack-scale/) (Slack Engineering Blog).
*   **Architecture**:
    *   Deploy lightweight "Edge Caches" closer to users.
    *   Backend sends 1 message to Edge; Edge duplicates to 1000 users.
*   **Pros**: Offloads CPU/Bandwidth from core data center.
*   **Suitability**: Global scale (Millions of users).

## 3. Micro-Optimizations (Implementation Details)
Before moving to Tier 2/3, we must optimize Tier 1.

*   **Pre-serialization**: Serialize JSON once, write raw bytes to N sockets. (Basic CPU saving)
*   **Batching**: Group messages for high-frequency updates.
*   **Hybrid Payload Strategy**:
    *   **Text Messages**: Use **Full Payload** (per Deep Dive 02) for speed and consistency.
    *   **Large Media/Files**: Use **ID-Only Push** to save Redis bandwidth; clients fetch metadata via API (Tier 1.5).
*   **Topic Partitioning (Sharding)**:
    *   *Mechanism*: Use Consistent Hashing on Channel ID to spread topics across multiple Redis instances.
    *   *Example*: `hash(channel_id) % 2 == 0` → Redis A, `else` → Redis B.
    *   *Limitation*: **Does NOT solve the "Mega-Channel" problem.** If `#announcements` has 100k users, all traffic for that channel still hits a single Redis instance (Hot Key), creating a bottleneck. This is why Tier 2 is eventually required.

## 4. Conclusion & Roadmap

We conclude that **Tier 1 (Redis Pub/Sub)** is a "Dead End" for massive scale (>10k users in a single channel) due to the **Full Payload + O(N) Fan-out** bandwidth explosion.

1.  **The Verdict**: Tier 1 is only an interim solution. It is physically impossible to sustain a "Mega-channel" broadcast using a centralized Redis broker without hitting network saturation.
2.  **The Transition**: 
    *   To scale further, we MUST move to **Tier 2 (Direct Routing)**.
    *   This requires **Redis Bypass**: Logic servers must take over the fan-out responsibility via direct gRPC calls to Gateway instances.
3.  **Next Step**:
    *   Investigate the architectural requirements for separating Gateways and implementing high-performance internal routing.
    *   **→ See Deep Dive 04: Gateway Separation & gRPC Optimization**

## 5. Experiment Plan (Fan-out Limits)

We need to find the "Knee of the Curve" for Redis Pub/Sub under the "Full Payload" constraint.

### Scenario: Massive Subscriber Simulation
*   **Goal**: Determine the maximum number of Gateway Servers (Subscribers) a single Redis instance can handle before latency exceeds 200ms.
*   **Why**: Testing with 4 actual servers is trivial. We must simulate the load of 100-1,000 Gateways.
*   **Setup**:
    *   **1 Test Runner**: Opens **N** parallel Redis connections (simulating N Gateways).
    *   **Workload**:
        *   All N connections subscribe to `channel.test`.
        *   Publisher sends 1KB payload (simulating "Full Payload").
*   **Variables**:
    *   `N` (Subscribers): 10, 100, 500, 1,000, 5,000.
*   **Metrics**:
    *   **Fan-out Latency**: Time from `PUBLISH` to `onMessage` for the Nth subscriber.
    *   **Redis CPU & Network Usage**.
*   **Hypothesis**: Redis will handle 100-500 subscribers easily, but network bandwidth will saturate at ~2,000 subscribers with 1KB payloads.
*   **Implementation**: `experiments/fanout-latency-lab/` (Updated for Mass-Client simulation)

## 6. Related Topics
*   **Deep Dive 01**: Multi-Server Broadcasting.
