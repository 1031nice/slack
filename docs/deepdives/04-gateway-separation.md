# 📄 Topic 04: Separation of Concerns: Gateway Architecture

> **Prerequisites**: This document builds on the scale limits identified in **Deep Dive 03: Massive Fan-out Architecture**.

## 1. Problem Statement

### 1.1 The Monolithic Connection Problem
In our current architecture, the **Backend Server** handles both:
1.  **Stateful Connections**: Maintaining 100k+ WebSockets, Ping/Pong, session state.
2.  **Stateless Business Logic**: Message processing, Auth, DB transactions, API routing.

**The "Pain Points":**
*   **Deployment Friction**: Every time we deploy a bug fix in the business logic, we must terminate and reconnect 100k+ WebSockets. This causes a "Thundering Herd" on the Auth service.
*   **Resource Contention**: Heavy DB processing or JSON serialization can starve the event loop, causing WebSocket timeouts and jitter.
*   **Scaling Inefficiency**: We must scale the entire monolith even if only the connection count increases.

## 2. Proposed Architecture: Gateway vs. Logic

We propose splitting the system into two distinct layers.

### 2.1 The Gateway Layer (Stateful)
*   **Role**: A "dumb" pipe that maintains connections.
*   **Tech Stack**: Optimized for high concurrency (e.g., Netty, Go, or Rust).
*   **Responsibilities**:
    *   WebSocket termination.
    *   Fan-out of messages to local clients.
    *   Health checks (Heartbeats).

### 2.2 The Logic Layer (Stateless)
*   **Role**: The "brains" of the system.
*   **Responsibilities**:
    *   Validation, Persistence, Auth.
    *   Deciding *who* should receive a message.
    *   Pushing messages to the Gateway layer.

### 2.3 Design Challenge: Head-of-Line (HOL) Blocking
When the Logic layer sends a message to 100 Gateways (Direct Routing), it must avoid synchronous loops.
*   **The Risk**: If Gateway #5 is slow or unresponsive, a synchronous sender will block, delaying delivery to Gateways #6 through #100.
*   **The Fix**: Communication must be **fully asynchronous** (Fire-and-Forget or Async Stream) so that one slow node does not penalize the entire broadcast.

## 3. Internal Communication: REST vs. gRPC

A critical decision is how the Logic layer communicates with the Gateway layer during fan-out.

### Option A: REST (HTTP/1.1 + JSON)
*   **Pros**: Simple, easy to debug, standard.
*   **Cons**: 
    *   High overhead (Text-based headers, JSON serialization).
    *   Connection overhead (Handshakes for every request).

### Option B: gRPC (HTTP/2 + Protobuf)
*   **Pros**:
    *   **Binary Protocol**: Protobuf is significantly smaller and faster to (de)serialize than JSON.
    *   **Persistent Streams**: Long-lived HTTP/2 streams reduce handshake overhead.
    *   **Bidirectional**: Allows Gateways to easily push backpressure or status updates to Logic.
*   **Cons**: Higher complexity, requires Protobuf definitions.

## 4. Experiment Plan (The Performance Gap)

We will verify if the complexity of gRPC is worth the performance gain for our Fan-out scenario.

### Scenario: High-Throughput Internal Fan-out
*   **Goal**: Compare REST vs. gRPC for pushing 1,000 messages/sec from Logic to 100 Gateway instances.
*   **Setup**:
    *   **1 Logic Server Mock**.
    *   **10 Gateway Server Mocks**.
*   **Metrics**:
    *   **Latency (P99)**: Time from Logic send to Gateway receipt.
    *   **CPU Usage**: Percentage of CPU consumed by serialization on both sides.
    *   **Throughput**: Max messages per second before the communication layer saturates.
*   **Hypothesis**: gRPC will show 40-60% lower CPU usage and 2-3x higher throughput compared to REST/JSON.

## 5. Conclusion & Roadmap

1.  **Phase 1**: Implement the experiment in `experiments/gateway-bench/`.
2.  **Phase 2**: If gRPC wins (Hypothesis holds), refactor the Backend into a separate `gateway` and `api` module.
3.  **Next Step**:
    *   With multiple Gateways and Logic servers, how do we ensure messages arrive in the correct order?
    *   **→ See Deep Dive 05: Causal Ordering Guarantees**

## 6. Related Topics
*   **Deep Dive 03**: Massive Fan-out Architecture.
*   **Deep Dive 05**: Causal Ordering Guarantees.
