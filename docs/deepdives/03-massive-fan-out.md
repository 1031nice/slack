# 📄 Topic 03: Massive Fan-out Architecture

## 1. Problem Statement

### 1.1 Mega-Channel Scenario
*   **Scale**: 100,000 connected WebSocket clients in a single channel (e.g., `#announcements`).
*   **Distribution**: Clients sharded across 100+ Backend Server Instances.
*   **Logical Gateway**: Backend instances handling physical WebSocket connections.
*   **Goal**: Deliver 1 message to 100 Gateways and 100k Users within **200ms**.

### 1.2 Failure Modes at Scale
1.  **Redis Saturation (Bandwidth)**:
    *   Full Payload (Deep Dive 02) increases per-message size.
    *   1KB message to 1,000 Gateways = 1MB instant outbound traffic from Redis.
    *   Bottleneck: Redis Network Bandwidth.
2.  **Thundering Herd (Reconnection Storms)**:
    *   Gateway crash triggers 10k simultaneous reconnections.
    *   Spike in Auth service and Initial State fetch (DB Pull).

## 2. Architectural Strategies

### Tier 1: Connection Sharding + Redis Pub/Sub (Baseline)
*   **Architecture**: Clients sharded across N servers; servers subscribe to `topic/channel.{id}`.
*   **Flow**: `API → Redis PUBLISH → All Servers (Fan-out) → Connected Clients`.
*   **Limit**: Throughput capped by Redis CPU/Bandwidth. 1,000 Gateways = 1,000x packet replication.
*   **Suitability**: < 10k users.

### Tier 2: Application-Level Multicast (Direct Routing)
*   **Architecture**: Logic Servers separate from Gateway Servers. Session Registry (Redis/Etcd) tracks user-gateway mapping.
*   **Flow**: `Logic Service → Query Session Registry → Direct gRPC to target Gateways → Connected Clients`.
*   **Redis Bypass**: Redis is no longer responsible for message replication. Logic server pushes only to Gateways with active subscribers.
*   **Pros**: Eliminates Redis Fan-out bottleneck; bandwidth savings.
*   **Suitability**: > 100k users.

### Tier 3: Edge Aggregation (Edge Caching)
*   **Reference**: [Flannel (Slack Engineering Blog)](https://slack.engineering/flannel-an-application-level-edge-cache-to-make-slack-scale/).
*   **Architecture**: Lightweight "Edge Caches" near users. Core sends 1 message to Edge; Edge handles local fan-out.
*   **Suitability**: Global scale (Millions of users).

## 3. Micro-Optimizations (Tier 1)
*   **Pre-serialization**: Serialize JSON once; write raw bytes to sockets.
*   **Batching**: Group high-frequency updates.
*   **Hybrid Payload**: Full Payload for text; ID-Only for large media.
*   **Topic Partitioning**: Consistent Hashing on Channel ID across multiple Redis nodes.
    *   *Limitation*: Does not solve "Mega-Channel" (Hot Key) problem where all traffic for one channel hits a single instance.

## 4. Verdict & Roadmap
*   **Tier 1 Failure**: Redis Pub/Sub is a physical dead end for Mega-channels due to O(N) bandwidth explosion.
*   **Requirement**: Transition to Tier 2 (Direct Routing) to bypass Redis for fan-out.
*   **Next Step**:
    *   Implement Massive Subscriber Simulation to find Redis breaking point.
    *   **→ See Deep Dive 04: Gateway Separation & gRPC Optimization**

## 5. Experiment Plan (Fan-out Limits)
*   **Goal**: Find the "Knee of the Curve" for Redis Pub/Sub latency.
*   **Setup**: 1 Test Runner simulating **N** parallel Redis subscribers (10 to 5,000).
*   **Payload**: 1KB (Full Payload simulation).
*   **Metrics**: Fan-out Latency (PUBLISH to Nth subscriber receipt), Redis CPU/Network.
*   **Hypothesis**: Network bandwidth saturation at ~2,000 subscribers with 1KB payloads.
*   **Implementation**: `experiments/fanout-latency-lab/`

## 6. Experiment Results (Local Simulation)
*   **Environment**: Local Docker (Redis 7-alpine), Darwin (macOS), Node.js.
*   **Setup**: 1KB Payload (Full Payload simulation).
*   **Metrics**:
    *   **100 Subscribers**: ~35ms P99 Latency.
    *   **500 Subscribers**: ~96ms P99 Latency.
    *   **2,000 Subscribers**: Connection Failure (ECONNREFUSED).
*   **Analysis**:
    1.  **Linear Latency Growth**: Confirmed O(N) cost. Latency nearly triples when scaling from 100 to 500 subscribers.
    2.  **Connection Bottleneck**: Simultaneous subscription attempts trigger OS/Docker-level connection refusal. Validates "Thundering Herd" risk.
    3.  **Capacity Limit**: Under local constraints, the 200ms budget would be exceeded at ~1,000-1,500 subscribers.
*   **Conclusion**: Validates the necessity of Tier 2 (Direct Routing) to bypass Redis fan-out O(N) overhead as subscriber count grows.
