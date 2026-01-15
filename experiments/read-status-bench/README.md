# Read Status Benchmark (Topic 06)

This experiment compares **Synchronous Write-Through (Direct DB)** vs **Asynchronous Write-Behind (Kafka)** for high-volume read receipt updates.

## Experiment Environment

*   **Infrastructure**: Dockerized PostgreSQL 15 and Kafka 7.4.0 (KRaft mode).
*   **Runtime**: Node.js v20.
*   **Load**: 5,000 read receipt updates for a single channel.

## Test Parameters

*   **Direct DB**: Sequential UPSERT queries with a connection pool (size 50).
*   **Kafka Async**:
    *   **Batching**: 500 messages per `producer.send` call.
    *   **Durability**: `acks: 1` (Wait for leader acknowledgment).
    *   **Consumer**: Aggregates 100 messages for a single **Batch UPSERT** to DB.

## Final Results (Measured on 2026-01-15)

| Metric | Direct DB (Sync) | Kafka Buffer (Async) | Improvement |
| :--- | :--- | :--- | :--- |
| **User Wait Time (Latency)** | ~2,399ms | **111ms** | **21.6x Faster** |
| **System Throughput** | ~2,084 ops/s | **45,225 ops/s** | **21.7x Higher** |
| **Consistency Lag** | **0ms** | ~3,241ms | Trade-off |

## How to Run

1. **Start Infrastructure**:
   ```bash
   cd experiments/read-status-bench
   docker-compose up -d
   ```

2. **Run Benchmark**:
   ```bash
   node run-bench.js
   ```

## Key Learnings

1.  **I/O Amortization**: Batching 500 messages into 1 Kafka send and 100 records into 1 DB query drastically reduces the overhead of network roundtrips and ACID transactions.
2.  **Backpressure**: Kafka acts as a shock absorber. Even if the DB were even slower, the User Wait Time would stay low (111ms) while the Consistency Lag would grow, protecting the user experience.
3.  **Operational Choice**: `acks=1` is used as a balance between performance and reliability for non-critical status updates.