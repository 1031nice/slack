# ADR-0009: gRPC Streaming for Gateway Communication

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-12
- **Context**: v0.6 - Microservices Separation
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 04: Gateway Separation](../deepdives/04-gateway-separation.md)

---

## TL;DR (Executive Summary)

**Decision**: Adopt **gRPC Bidirectional Streaming** for internal communication between Logic Servers and Gateway Servers.

**Key Trade-off**: Accept **Implementation Complexity** (Protobuf management, connection lifecycle) in exchange for **Maximum Throughput** and **Zero Connection Overhead**.

**Rationale**: Separation of State (Gateway) and Logic introduces internal network latency. Our benchmark shows REST/HTTP1.1 incurs 14x overhead compared to gRPC Streams. To prevent this internal link from becoming a bottleneck, we must utilize HTTP/2 multiplexing.

---

## Context

### The Problem

We are splitting the Monolithic Backend into:
1.  **Gateway Layer**: Handles 100k+ concurrent WebSocket connections (Stateful).
2.  **Logic Layer**: Handles business rules, DB, and Auth (Stateless).

This split requires the Logic Layer to "push" thousands of messages per second to the Gateway Layer over the network.

### Constraints

- **Head-of-Line (HOL) Blocking (Protocol Level)**: 
    - *The Problem*: In HTTP/1.1, a single TCP connection is a "single-lane road." Even if the application code is non-blocking (Async), the protocol itself forces requests to wait for the previous one to finish. If `Message A` to a Gateway is delayed by 100ms, `Message B` is stuck behind it on that connection.
    - *The Solution*: gRPC (HTTP/2) uses **Multiplexing**, turning the connection into a "multi-lane highway." Frames for different messages are interleaved, so a slow `Message A` doesn't stop `Message B` from being delivered.
- **Throughput**: Must support 10k+ internal messages/sec with minimal CPU overhead. Binary Protobuf avoids the heavy cost of string-based JSON parsing.

---

## Decision

### What We Chose

1.  **Protocol**: **gRPC (Google Remote Procedure Call)** over HTTP/2.
2.  **Pattern**: **Bidirectional Streaming**. Logic Servers open a long-lived stream to each Gateway and "fire-and-forget" messages into it.

### Architecture Flow

```
1. Gateway Server starts and registers itself (e.g., via K8s/Service Discovery).
2. Logic Server detects Gateway and opens a Persistent gRPC Stream.
3. User A sends message to Logic Server.
4. Logic Server processes message and pushes Proto frame into Stream for Gateway X.
   (No Handshake, No Headers, Instant Transmission)
5. Gateway X reads frame and pushes to WebSocket.
```

### Why This Choice (Trade-off Analysis)

| Criteria | **gRPC Streaming** (Selected) | REST/HTTP1.1 (Rejected) | Message Queue (Rejected) |
| :--- | :--- | :--- | :--- |
| **Throughput** | ✅ **~19k req/sec** (14x) | ❌ ~1.3k req/sec | 🟡 High (but high latency) |
| **Latency** | ✅ **Microseconds** | ❌ Milliseconds (Headers) | ❌ 10-50ms |
| **Connection Cost** | ✅ **Zero** (Persistent) | ❌ High (Per-request) | ✅ Zero (Consumer) |
| **Complexity** | 🔴 High (State management) | ✅ Low (Stateless) | 🔴 High (Infra required) |

**Primary Reason**:
Our benchmark (`experiments/gateway-bench`) proved that REST is physically incapable of meeting our throughput requirements without massive hardware scaling. The 14x efficiency gain of gRPC Streaming is critical for cost-effective scaling.

---

## Consequences

### Positive Impacts

- **No HOL Blocking**: HTTP/2 multiplexing allows multiple message streams to coexist on one connection. A "slow" message no longer stalls the entire pipeline.
- **CPU Efficiency**: Protobuf serialization is significantly more efficient than JSON, reducing Logic Server overhead during massive fan-outs.
- **Resource Savings**: Reduces the number of internal connections and server instances required.

### Negative Impacts & Mitigations

- **Risk: Load Balancing**
  - Long-lived streams break standard L7 Load Balancing (sticky connections).
  - **Mitigation**: Implement **Client-side Load Balancing** in the Logic Layer, where it explicitly maintains connections to *all* available Gateways (Mesh topology).

- **Risk: Complexity**
  - Requires defining `.proto` files and handling stream reconnection/error logic.
  - **Mitigation**: Encapsulate gRPC logic in a shared library to keep business code clean.

### Implementation Details

- **Proto Definition**:
  ```protobuf
  service GatewayService {
    rpc PushStream (stream MessageRequest) returns (MessageResponse) {}
  }
  ```
- **Error Handling**: If a stream breaks, Logic Server buffers messages briefly and attempts exponential backoff reconnection.

---

## References

- **[Deep Dive 04: Gateway Separation](../deepdives/04-gateway-separation.md)**
  - Detailed analysis of the Monolith problem and the benchmark results.
- **[gRPC Performance Best Practices](https://grpc.io/docs/guides/performance/)**
  - Recommendations for using streaming to maximize throughput.
