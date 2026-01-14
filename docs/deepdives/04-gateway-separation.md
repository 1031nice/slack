# 📄 Topic 04: Separation of Concerns: Gateway Architecture

## 1. Problem Statement

### 1.1 Monolithic Connection Problem
Currently, the Backend Server handles both stateful connections (WebSocket) and stateless business logic (Auth, Persistence).

**Pain Points:**
*   **Deployment Friction**: A simple logic update requires restarting the server, forcing 100k+ users to reconnect simultaneously (**Thundering Herd**).
*   **Resource Contention**: CPU-intensive tasks (JSON parsing, DB queries) block the event loop, causing jitter in WebSocket heartbeats and message delivery.
*   **Scaling Inefficiency**: We must scale the entire monolith even if we only need more connection capacity.

## 2. Solution Strategy: Split Logic & Gateway

### 2.1 The Split
We separate the system into two distinct layers:
*   **Gateway Layer (Stateful)**: "Dumb" pipe. Handles WebSocket termination, local fan-out, and connection maintenance.
*   **Logic Layer (Stateless)**: "Smart" brain. Handles authentication, database interactions, and business rules.

### 2.2 The Design Challenge: "The Internal Firehose"
Splitting the server introduces a new problem: **Internal Communication Overhead**.
Instead of in-memory function calls, the Logic Layer must now transmit messages over the network to the Gateway Layer.

**Critical Requirements:**
1.  **Low Latency**: Internal hops must add < 5ms delay.
2.  **Head-of-Line (HOL) Blocking Prevention**: If Gateway A is slow (GC pause), it must not block message delivery to Gateway B.
3.  **Thundering Herd on Internal Links**: Logic servers must efficiently connect to hundreds of Gateways without establishing a new TCP handshake for every message.

## 3. Communication Pattern Selection

We evaluated three approaches for Logic-to-Gateway communication.

### Option A: Message Queue (Kafka/RabbitMQ)
*   **Flow**: `Logic → Kafka → Gateway`
*   **Pros**: extreme decoupling, buffering.
*   **Cons**:
    *   Adds significant latency (10-50ms).
    *   Overkill durability (Gateways are ephemeral; if a user disconnects, the message is lost anyway).
    *   **Verdict**: Rejected (Too slow for real-time).

### Option B: REST (HTTP/1.1)
*   **Flow**: `Logic → POST /push → Gateway`
*   **Pros**: Simple, standard, easy to debug.
*   **Cons**:
    *   **Connection Overhead**: New handshake or keep-alive management per request.
    *   **Text Protocol**: JSON serialization is CPU heavy.
    *   **HOL Blocking**: Synchronous HTTP requests typically block threads or consume connection pool limits.
    *   **Verdict**: Rejected (High overhead).

### Option C: Persistent gRPC Streams (HTTP/2)
*   **Flow**: `Logic ⇄ Bidirectional Stream ⇄ Gateway`
*   **Pros**:
    *   **Single Connection**: One TCP connection handles infinite messages (Multiplexing).
    *   **Binary Protocol**: Protobuf is 5-10x faster to serialize than JSON.
    *   **Non-blocking**: Streaming allows "Fire-and-Forget" push without waiting for HTTP response headers.
    *   **Verdict**: **Accepted**.

## 4. Final Architecture: The "Push Stream" Model

### 4.1 Topology
*   **Gateways** act as **gRPC Servers**. They expose a `StreamMessages` endpoint.
*   **Logic Servers** act as **gRPC Clients**. On startup (and periodically), they discover all active Gateways and establish a **Long-lived Persistent Stream** to each.

### 4.2 Message Flow
```
Step 1: User A sends message to Logic Server.
Step 2: Logic Server saves to DB.
Step 3: Logic Server determines target Gateways (Session Lookup).
Step 4: Logic Server pushes protobuf message into the existing gRPC Stream for Gateway X.
        (No Handshake, No Headers, just binary frame push)
Step 5: Gateway X reads frame, deserializes, and pushes to WebSocket.
```

### 4.3 Handling HOL Blocking
By using **gRPC Streams**, we utilize HTTP/2 multiplexing. Even if one stream frame is delayed, the underlying TCP connection remains efficient. Logic servers push messages asynchronously to the stream buffer, decoupling the "Business Logic" thread from the "Network IO" thread.

## 5. Validation: REST vs gRPC Benchmark

### 5.1 Goal
Prove that gRPC Streaming is not just "slightly" faster, but architecturally necessary to handle the load of 100k+ concurrent users.

### 5.2 Experiment Setup
*   **Implementation**: `experiments/gateway-bench/`
*   **Scenario**: Sending 10,000 messages (1.2KB payload) sequentially.
*   **Comparison**: REST (Unary) vs gRPC (Unary) vs gRPC (Streaming).

### 5.3 Results

| Protocol | Throughput (req/sec) | Speedup | Latency Impact |
| :--- | :--- | :--- | :--- |
| **REST (HTTP/1.1)** | ~1,319 | 1x | High (Handshake + Headers) |
| **gRPC (Unary)** | ~3,278 | 2.5x | Medium (Headers per msg) |
| **gRPC (Stream)** | **~18,943** | **14.4x** | **Near Zero (Frame overhead only)** |

### 5.4 Interpretation
*   **The 14x Gap**: The massive performance difference confirms that **connection establishment** and **HTTP headers** are the primary bottlenecks in internal microservice communication.
*   **Efficiency**: gRPC Streaming allows a single Logic Server to drive 14x more traffic to Gateways than a REST-based implementation, significantly reducing the required cluster size.

## 6. Verdict & Roadmap

### Decisions
1.  **Split Architecture**: Adopt Gateway/Logic separation.
2.  **Protocol**: Use **gRPC Bidirectional Streaming** for internal links.
    *   **→ Decision Recorded in [ADR-0009: gRPC Streaming](../adr/09-gateway-grpc-streaming.md)**
3.  **Discovery**: Logic Servers needs a mechanism to discover Gateways (e.g., K8s Service, Consul, or Etcd) to open streams.

### Next Steps
*   **Causal Ordering**: Now that we have a fast pipe, how do we ensure messages arrive in order across distributed servers?
    *   **→ See Deep Dive 05: Causal Ordering Guarantees**

## 7. Related Documents
*   **Deep Dive 01**: Multi-Server Broadcasting (The initial problem).
*   **Deep Dive 03**: Massive Fan-out (Why Redis Pub/Sub fails).
