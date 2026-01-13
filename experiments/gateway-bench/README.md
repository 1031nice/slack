# Gateway Protocol Benchmark (Topic 04)

## Hypothesis
Internal communication between Logic and Gateway layers will become a bottleneck. We hypothesize that **gRPC Streaming** will significantly outperform REST (HTTP/1.1) and Unary gRPC by eliminating connection setup overhead and utilizing HTTP/2 multiplexing.

## Experimental Setup
*   **Hardware**: MacBook Pro (Apple Silicon)
*   **Runtime**: Node.js v20
*   **Clients**: 10 concurrent virtual users simulated via `run-bench.js`.
*   **Payload**: ~1.2KB JSON/Protobuf message.

## Prerequisites
*   Node.js installed.
*   No external database required (pure network benchmark).

## How to Run

1. **Install Dependencies**:
   ```bash
   cd experiments/gateway-bench
   npm install
   ```

2. **Run Benchmark**:
   ```bash
   node run-bench.js
   ```

## Summary of Results (Measured on 2026-01-12)

| Protocol | Throughput (req/sec) | Relative Speed |
| :--- | :--- | :--- |
| **REST (HTTP/1.1)** | ~1,300 | 1x |
| **gRPC Unary** | ~3,300 | 2.5x |
| **gRPC Stream** | **~19,000** | **14.4x** |

> **Note**: For detailed architectural analysis and decision making based on these numbers, please refer to [Deep Dive 04: Gateway Separation](../../docs/deepdives/04-gateway-separation.md).