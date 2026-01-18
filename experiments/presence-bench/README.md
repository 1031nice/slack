# Presence Load Test (Topic 07)

## Hypothesis
Batching heartbeat updates using Redis `ZADD` (Sorted Sets) will provide significantly higher throughput than updating individual keys using `SETEX` (Strings), allowing a single Redis instance to handle 1M+ concurrent users.

## Experiment Environment
*   **Infrastructure**: Dockerized Redis.
*   **Runtime**: Node.js v20.
*   **Load**: 100,000 heartbeat updates.

## Scenarios
1.  **Individual SETEX**: 100,000 separate calls. Simulates naive implementation.
2.  **Pipeline SETEX**: 100,000 calls pipelined. Simulates optimized KV.
3.  **Batch ZADD**: 1,000 calls with 100 items each. Simulates "ZSET Presence" model.

## How to Run
```bash
cd experiments/presence-bench
# Ensure Redis is running on localhost:6379
node run-bench.js
```
