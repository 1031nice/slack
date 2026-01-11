# 📄 Topic 04: Separation of Concerns: Gateway Architecture

## 1. Problem Statement

### 1.1 Monolithic Connection Problem
Backend Server handles both stateful connections and stateless business logic.

**Pain Points:**
*   **Deployment Friction**: Logic updates force reconnection of 100k+ WebSockets (Thundering Herd risk).
*   **Resource Contention**: Heavy DB/JSON processing starves the event loop (WebSocket jitter).
*   **Scaling Inefficiency**: Forced scaling of monolith for connection-only growth.

## 2. Proposed Architecture: Gateway vs. Logic

### 2.1 Gateway Layer (Stateful)
*   **Role**: Dumb pipe for connection maintenance.
*   **Responsibilities**: WebSocket termination, local fan-out, heartbeats.
*   **Tech Stack**: High concurrency (Netty, Go, Rust).

### 2.2 Logic Layer (Stateless)
*   **Role**: System brain.
*   **Responsibilities**: Validation, Persistence, Auth, Routing decisions.

### 2.3 Design Challenge: Head-of-Line (HOL) Blocking
*   **Risk**: Synchronous loops in direct routing. Slow Gateway #5 blocks delivery to Gateways #6-#100.
*   **Requirement**: Fully asynchronous communication (Async Streams/Fire-and-Forget).

## 3. Internal Communication: REST vs. gRPC

### Option A: REST (HTTP/1.1 + JSON)
*   **Pros**: Simple, standard, human-readable.
*   **Cons**: Text-based overhead, high (de)serialization cost, connection handshake overhead.

### Option B: gRPC (HTTP/2 + Protobuf)
*   **Pros**: Binary protocol (compact), persistent streams, bidirectional updates.
*   **Cons**: Higher implementation complexity.

## 4. Experiment Plan (Performance Gap)
*   **Goal**: Compare REST vs. gRPC for Logic-to-Gateway fan-out (1,000 msg/sec to 100 instances).
*   **Metrics**: Latency (P99), CPU usage (serialization), Throughput.
*   **Hypothesis**: gRPC offers 40-60% lower CPU and 2-3x higher throughput than REST/JSON.

## 5. Verdict & Roadmap
1.  **Phase 1**: Execute experiment in `experiments/gateway-bench/`.
2.  **Phase 2**: Refactor backend into `gateway` and `api` modules if hypothesis holds.
3.  **Next Step**:
    *   **→ See Deep Dive 05: Causal Ordering Guarantees**

## 6. Related Topics
*   **Deep Dive 03**: Massive Fan-out Architecture.
*   **Deep Dive 05**: Causal Ordering Guarantees.
