# Real-Time Messaging System

System design learning project. Experience distributed systems problems by building a messaging platform.

## Tech Stack

- Backend: Java 21, Spring Boot 3.2
- Database: PostgreSQL (source of truth)
- Cache: Redis (performance layer)
- Message Queue: Kafka (async persistence)
- Real-time: WebSocket (STOMP), Redis Pub/Sub (server-to-server)
- Metrics: Micrometer + Prometheus

## Current Status

**Latest**: v0.4.2 ✅ - Kafka failure handling complete

**Completed**:
- Multi-server real-time messaging (Redis Pub/Sub)
- Event-based architecture (distributed timestamp IDs)
- Read receipts with Kafka persistence + failure handling
- Multi-workspace RBAC

## Version History

### v0.1 - Baseline

Established performance baseline before optimization.

- P50=46ms, P95=175ms, P99=252ms at 45 msg/sec (single server)
- Lesson: Measure simple solution first

### v0.2 - Multi-tenancy

RBAC, workspace isolation, permission modeling.

### v0.3 - Multi-Server Broadcasting

**Problem**: Fan out messages to clients on different servers

**Solution**: Redis Pub/Sub (1ms overhead)

**Alternatives**:

- DB polling: 100ms latency
- Kafka: 10-50ms latency

**Decision**: Redis Pub/Sub for speed, PostgreSQL for durability. Separate concerns - DB first for data persistence,
Pub/Sub for real-time broadcast.

**Trade-off**: Redis failure = temporary real-time loss (data safe in DB)

See: [ADR-0001](./docs/adr/0001-redis-vs-kafka-for-multi-server-broadcast.md)

### v0.4 - Read Receipts

**Problem**: 1M+ read status updates/day

**Solution**: Eventual consistency (Redis → async → PostgreSQL)

**Alternatives**:

- Strong consistency (2PC): 10x latency, complex
- Eventual consistency: 100ms staleness, simple

**Decision**: CAP choice - AP for read status, CP for messages. 100ms staleness acceptable.

**Mistake**: `@Async` has no backpressure → OOM at scale

See: [ADR-0002](./docs/adr/0002-eventual-consistency-for-read-status.md)

### v0.5 - Event-Based Architecture

**Problem**: Redis INCR bottleneck for horizontal scaling

**Solution**: Distributed timestamp ID generation (no coordination)

**Trade-off**: Clock skew may cause ordering issues (to be measured)

See: [ADR-0006](./docs/adr/0006-event-based-architecture-for-distributed-messaging.md)

### v0.4.1 - Kafka Persistence

**Problem**: Fix `@Async` disaster (unbounded threads, order inversion)

**Solution**: Kafka producer/consumer with batching

- 500 events → 1 DB query (50% write reduction)
- SQL `GREATEST()` prevents stale writes
- 48h retention for crash recovery

**Mistake**: Kept `@Async` dual-write (unnecessary)

See: [ADR-0007](./docs/adr/0007-kafka-batching-for-read-receipt-persistence.md)

### v0.4.2 - Failure Handling ✅

**Problem**: What if Kafka fails? What if consumer fails?

**Solution**:
- Producer fallback queue: Buffers events during Kafka outage, retries every 5s
- DLT consumer: Event-driven reconciliation from Redis → DB
- Removed `@Async`: Kafka is sole durability mechanism

**Result**:
- Producer fallback: Kafka outage doesn't lose data
- DLT reconciliation: Failed events recover from Redis
- No scheduled scans: Event-driven approach (efficient)
- Metrics: Track fallback queue size, DLT events, success/failure rates

## Next Problem

Three options:

**A. Thread N+1 Optimization**

- Current: 100 threads = 1101 queries
- Target: 100 threads = 2 queries
- Complexity: Low (no new infrastructure)
- Learning: JPA optimization, profiling

**B. Message Ordering Test**

- Measure: % inversions under concurrent load
- If >5%: Fix needed (channel partitioning or Snowflake IDs)
- If <5%: Acceptable, document

**C. Later**

- Search (Elasticsearch) - after 1M+ messages
- Hot channels - after traffic data shows bottleneck

Recommended: A (Thread N+1) - high learning value, low complexity

See: [NEXT_STEPS.md](./NEXT_STEPS.md)

## Architecture Decisions

All decisions documented in [docs/adr/](./docs/adr/) with:

- Problem statement
- Alternatives considered
- Decision rationale
- Trade-offs accepted

Key decisions:

- Redis Pub/Sub over Kafka for real-time (latency)
- Eventual consistency for read status (performance)
- Kafka batching for async writes (throughput)
- DLT consumer over scheduled reconciliation (efficiency)

## Performance Baselines

**Single Server (v0.1)**:

- Throughput: 45 msg/sec
- Latency: P50=46ms, P95=175ms, P99=252ms

**Multi-Server (v0.3)**:

- Throughput: 42 msg/sec (3 servers)
- Latency: P50=34ms, P95=190ms
- Redis Pub/Sub overhead: ~1ms

**Kafka Persistence (v0.4.2)**:

- Batch size: 500 events
- Deduplication: 50% write reduction
- DB write latency: P50=15ms, P95=45ms
- Recovery time: <1 min for 1000 failed events

## Getting Started

```bash
# Infrastructure
docker-compose up -d

# Backend
cd backend && ./gradlew bootRun

# Frontend
cd frontend && npm install && npm run dev
```

Access:

- Frontend: http://localhost:3000
- Backend: http://localhost:9000
- Swagger: http://localhost:9000/swagger-ui.html
- Prometheus: http://localhost:9090

### Multi-Server Setup

```bash
# Requires auth-platform in ../auth-platform
./start-all.sh  # 3 servers + Nginx
./stop-all.sh
```

### Performance Testing

```bash
./scripts/generate-test-token.sh
./scripts/run-performance-test.sh

# Custom params
CONNECTIONS=10 DURATION=30 MESSAGES_PER_SEC=5 ./scripts/run-performance-test.sh
```

## Project Structure

```
slack/
├── backend/src/main/java/com/slack/
│   ├── message/              # Core messaging
│   ├── channel/              # Channel management
│   ├── workspace/            # Multi-tenancy
│   ├── readreceipt/          # Read status
│   ├── websocket/            # WebSocket handlers
│   ├── common/service/       # Redis Pub/Sub, permissions
│   └── config/               # Spring config
├── frontend/                 # Next.js 14
├── docs/adr/                 # Architecture decisions
└── scripts/                  # Performance testing
```

## Key Learnings

1. **Measure before optimize**: No data = no problem
2. **Trade-offs are situational**: Messages=CP, Read status=AP
3. **Research ≠ Copy**: Understand WHY Slack uses X, not just implement X
4. **Failure modes matter**: Happy path ≠ production ready
5. **Infrastructure is cost**: Only add when data justifies complexity

## Common Pitfalls Avoided

- ❌ "Let's use Kafka because Slack does" → ✅ Measure first, Redis Pub/Sub simpler
- ❌ "Eventual consistency is bad" → ✅ Acceptable for read status (10x faster)
- ❌ "Need perfect ordering from day 1" → ✅ Measure accuracy first
- ❌ Building Consul cluster before proving need → ✅ Deleted 200+ lines of premature optimization

## Troubleshooting

**JWT expired**:

```bash
./scripts/generate-test-token.sh
```

**Kafka consumer lag**:

```bash
docker exec -it slack-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group read-receipt-persister
```

**Redis connection**:

```bash
docker ps | grep redis
redis-cli -p 6380 PING
```

## References

Real-world systems:

- [Slack Engineering](https://slack.engineering/)
- [Discord: Storing Billions of Messages](https://discord.com/blog/how-discord-stores-billions-of-messages)

Distributed systems:

- Martin Kleppmann - Designing Data-Intensive Applications
- [Jepsen](https://jepsen.io/)

---

MIT License
