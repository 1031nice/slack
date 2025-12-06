# Performance Benchmarks

> Performance test results tracked across versions

## Test Environment

- **OS**: macOS (Development)
- **Database**: PostgreSQL 15 (Docker)
- **Cache**: Redis 7 (Docker)
- **Tool**: Custom Node.js WebSocket test (`scripts/performance-test-node.js`)

---

## v0.3 - Distributed Messaging

**Test Date**: 2025-12-06

### Single Server

**Configuration**: 5 clients, 15s duration, 2 msg/sec per client

**Results**:
- ✅ Connections: 5/5 successful
- ✅ Messages: 145 sent, 725 received (5x broadcast)
- ✅ Throughput: 44.57 msg/sec
- ✅ Latency: P50=46ms, P95=175ms, P99=252ms

### Multi-Server (Nginx + 3 backends)

**Configuration**: 5 clients, 15s duration, 2 msg/sec per client, Nginx with `ip_hash`

**Results**:
- ✅ Connections: 5/5 successful
- ✅ Messages: 145 sent, 725 received (5x broadcast)
- ✅ Throughput: 41.76 msg/sec
- ✅ Latency: P50=34ms, P95=190ms, P99=222ms

### Summary

| Configuration | Throughput (msg/sec) | P50 Latency (ms) | P95 Latency (ms) |
|---------------|---------------------|------------------|------------------|
| Single Server | 44.57 | 46 | 175 |
| Multi-Server (3x) | 41.76 | 34 | 190 |

**Observations**:
- Multi-server throughput similar to single server (expected at this low load)
- Both configurations handle real-time chat requirements well (< 200ms P95)

---

## Reproducibility

```bash
# Start all services
./start-all.sh

# Run performance test (auto-generates valid JWT token)
./scripts/run-performance-test.sh

# Results saved to: local/performance-results/
```
