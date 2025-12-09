# Slack Clone - Production-Grade Real-time Messaging System

## Project Overview

A production-focused Slack clone built to learn **real-world distributed systems challenges** at scale. This is not a simplified tutorial project - it aims to solve the same architectural problems that real Slack faces: message reliability, ordering guarantees, distributed consensus, horizontal scaling, and fault tolerance.

**Philosophy**: Learn by building production-grade solutions, not toy examples. Every architectural decision is documented with trade-offs, failure modes, and scaling characteristics.

## Architecture Principles

- **Reliability First**: At-least-once delivery, idempotency, fault tolerance
- **Measured Trade-offs**: Document why we choose performance vs consistency, when to use caching vs DB queries
- **Real Failure Scenarios**: Network partitions, server crashes, message reordering
- **Observable**: Metrics, tracing, structured logging from day one
- **Scalable from Start**: Multi-server architecture, no "we'll scale it later"

## Tech Stack

- **Backend**: Java 21 + Spring Boot 3.2 + Gradle
- **Frontend**: Next.js 14 + TypeScript
- **Database**: PostgreSQL (source of truth)
- **Message Broker**: Redis Pub/Sub → **Kafka** (v0.4+ for durability)
- **Cache**: Redis
- **Search**: Elasticsearch (v0.6)
- **Monitoring**: Micrometer + Prometheus + Grafana
- **Infrastructure**: Docker Compose (local) → Kubernetes (future)

## Current Status: v0.3

**What Works**:
- ✅ Multi-server WebSocket broadcasting via Redis Pub/Sub
- ✅ Sequence-based message ordering per channel
- ✅ Client reconnection with missed message recovery
- ✅ JWT authentication with OAuth2 resource server
- ✅ RBAC with workspace/channel permissions

**Known Issues (Being Fixed in v0.4)**:
- ❌ **At-most-once delivery**: Messages lost if Redis/network fails
- ❌ **No idempotency**: Client retries create duplicate messages
- ❌ **Redis SPOF**: Single point of failure, no HA
- ❌ **ACK not implemented**: Framework exists, retry logic TODO
- ❌ **Race conditions**: Sequence gaps if DB transaction fails after Redis INCR
- ❌ **No backpressure**: Unbounded message accumulation

## Version Roadmap

### v0.1 - MVP ✅
**Goal**: Single-server baseline + performance measurement

**Implemented**:
- Basic CRUD APIs (User, Workspace, Channel, Message)
- Single-server WebSocket (STOMP over SockJS)
- JWT authentication
- PostgreSQL with Flyway migrations
- Prometheus metrics collection

**Performance Baseline**:
- Throughput: ~45 msg/sec (5 clients, 2 msg/sec each)
- Latency: P50=46ms, P95=175ms, P99=252ms

**Deliverable**: "Can send/receive messages in real-time"

---

### v0.2 - Multi-workspace & Access Control ✅
**Goal**: Enterprise-grade permission system

**Implemented**:
- RBAC (Owner, Admin, Member roles)
- Public/Private channels with access control
- Workspace invitation flow (token-based)
- `@PreAuthorize` method security
- Permission service for fine-grained checks

**Learning Focus**: Multi-tenancy, permission modeling, Spring Security integration

**Deliverable**: "Multiple workspaces with role-based access control"

---

### v0.3 - Distributed Messaging ✅ (Current)
**Goal**: Multi-server architecture (with known limitations)

**Implemented**:
- Redis Pub/Sub for server-to-server messaging
- Nginx load balancer with sticky sessions (ip_hash)
- Message sequence numbers (Redis INCR)
- Client reconnection handling
- ACK message type (handler stubbed)

**Architecture**:
```
Client → WebSocket → Server → PostgreSQL (persist)
                            ↓
                         Redis Pub/Sub → All Servers → Local WebSocket clients
```

**Performance** (3 servers):
- Throughput: 41.76 msg/sec (comparable to single server at low load)
- Latency: P50=34ms, P95=190ms

**Critical Limitations** (Why This Is Not Production-Ready):

1. **Message Durability**:
   - Redis Pub/Sub is **fire-and-forget** (no persistence)
   - If subscriber disconnects for 1 second, all messages during that time are **permanently lost**
   - If Redis crashes, in-flight messages vanish

2. **Delivery Guarantees**:
   - Current: **At-most-once** (message may be lost)
   - Real Slack needs: **At-least-once** (message delivered or retried)
   - Flow: `DB save → Redis publish` - if publish fails, message stuck in DB
   - No retry mechanism, no dead letter queue

3. **Race Conditions**:
   - Sequence generation: `Redis INCR → DB save`
   - If DB transaction fails, sequence number is consumed but no message exists (gap)
   - Two concurrent messages can commit out of order: seq=5 commits after seq=6

4. **No Idempotency**:
   - Client retries (network timeout) → duplicate messages in DB
   - No client-generated message IDs
   - No deduplication logic

5. **Single Point of Failure**:
   - Redis down → no new messages (sequence service fails)
   - Redis down → no message broadcasting
   - No Redis HA/clustering in current setup

6. **Client State Management**:
   - Client tracks last received sequence (not persisted server-side)
   - If client loses state → must query DB for recovery
   - No server-side "read cursor" per user per channel

7. **No Flow Control**:
   - Slow consumers → unbounded message queue in memory
   - No backpressure, no circuit breakers
   - Can OOM if message rate exceeds consumer capacity

**Why Redis Pub/Sub First, Kafka Later?**

*Decision*: Implement v0.3 with Redis Pub/Sub despite knowing its limitations.

*Reasoning*:
1. **Learning Path**: Understand pub/sub pattern before Kafka complexity
2. **Failure Exposure**: Experience message loss first-hand (better than reading docs)
3. **Performance Baseline**: Measure Redis Pub/Sub overhead (actual: ~1ms, very low)
4. **Migration Practice**: v0.3→v0.4 migration teaches backward compatibility

*What We Learned*:
- Redis Pub/Sub is extremely fast but not durable
- Message loss happens silently (no errors, just missing data)
- Subscriber reconnection is a hard problem
- Sticky sessions critical for WebSocket (learned via nginx `ip_hash`)

*Cost*: 2 weeks implementation + 1 week refactoring to Kafka = 3 weeks total. Worth it for learning experience.

**Deliverable**: "Multi-server messaging that works in happy path, exposes failure modes for v0.4 fixes"

---

### v0.4 - Message Reliability (Next: Critical)
**Goal**: Production-grade delivery guarantees

**Planned Architecture Changes**:

1. **Replace Redis Pub/Sub with Kafka**:
   - Durable message log (configurable retention)
   - Consumer groups for horizontal scaling
   - Offset tracking replaces sequence numbers
   - At-least-once delivery with retries

2. **Outbox Pattern for Transactional Messaging**:
   ```sql
   BEGIN TRANSACTION;
     INSERT INTO messages (content, channel_id, sequence) VALUES (...);
     INSERT INTO outbox (message_id, channel_id, status) VALUES (...);
   COMMIT;

   -- Background worker
   SELECT * FROM outbox WHERE status = 'PENDING' FOR UPDATE SKIP LOCKED;
   -- Publish to Kafka
   UPDATE outbox SET status = 'PUBLISHED';
   ```
   - Guarantees: If message in DB, it will be published (eventual)
   - Atomic: DB save + outbox entry in single transaction

3. **Idempotency Keys**:
   ```java
   @Column(unique = true)
   private String clientMessageId;  // UUID from client
   ```
   - Client generates UUID per message
   - Server: `INSERT ... ON CONFLICT (clientMessageId) DO NOTHING`
   - Retries are safe

4. **Server-Side Read Cursors**:
   ```java
   @Entity
   class MessageCursor {
       @EmbeddedId
       private UserChannelId id;  // composite: userId + channelId

       private Long lastDeliveredOffset;  // Kafka offset
       private Long lastReadOffset;
       private Instant updatedAt;
   }
   ```
   - Track what each user has received/read
   - Enable cross-device sync
   - Simplify reconnection (no client state needed)

5. **ACK-based Retry**:
   - Client sends ACK after rendering message
   - Server tracks unacked messages per connection
   - Resend unacked messages on reconnect or timeout

6. **Dead Letter Queue**:
   - Messages failing after N retries → DLQ
   - Alerting for manual intervention
   - Prevents infinite retry loops

**Kafka Topic Design**:
```
slack.messages.{channelId} (partitioned by channelId)
  - Key: messageId (for dedup)
  - Value: WebSocketMessage JSON
  - Retention: 7 days (configurable)

Consumer Group: websocket-broadcaster
  - One consumer per server
  - Auto-commit disabled (manual commit after WebSocket send)
```

**Performance Goals**:
- Throughput: 1000+ msg/sec (20x improvement via Kafka parallelism)
- Latency: P99 < 300ms (includes Kafka produce + consume)
- Durability: 99.99% (message loss < 0.01%)

**Learning Focus**:
- Kafka fundamentals (topics, partitions, consumer groups)
- Transactional outbox pattern
- Idempotency and deduplication
- Exactly-once semantics (at-least-once + dedup)

**Deliverable**: "Message delivery guarantee with metrics-proven reliability"

---

### v0.5 - Read Status & Notifications
**Goal**: User engagement with eventual consistency

**Planned**:
- Redis Sorted Set for per-channel unread counts
  - Key: `unread:{userId}:{channelId}`, Members: messageIds, Scores: timestamps
  - O(1) increment on new message, O(1) clear on read
- Mention detection (`@username`) with notification queue
- Real-time read receipts (WebSocket: `{type: 'READ', userId, channelId, lastReadOffset}`)
- Periodic sync to PostgreSQL (eventual consistency trade-off)
  - Crash recovery: PostgreSQL is source of truth
  - Redis is cache for performance

**Trade-off Analysis**:
- **Why Redis**: 10,000+ read status updates/sec, sub-millisecond latency
- **Why Eventual Consistency**: Strict consistency would require distributed transaction (2PC) across Redis + PostgreSQL
  - Cost: 10x latency increase, complexity, availability risk
  - Benefit: Acceptable for read status (not financial transaction)
- **Sync Strategy**: Write-through to Redis, async batch write to PostgreSQL every 5 seconds

**Deliverable**: "Unread counts and @mentions with measured consistency lag"

---

### v0.6 - Thread Support
**Goal**: Nested conversations with query optimization

**Planned**:
- Self-referencing Message entity: `@ManyToOne parentMessage`
- Thread depth limit (Slack uses 1-level: no nested threads)
- N+1 query prevention:
  - `@EntityGraph(attributePaths = {"parentMessage", "user"})`
  - Batch fetch size tuning
- Hot thread caching (Redis):
  - Cache threads with >10 replies in last hour
  - Invalidation on new reply
- Pagination for long threads (cursor-based, not offset)

**Performance Test**:
- Measure query time: threads with 100 replies
- Before optimization: ~500ms (N+1 queries)
- After optimization: <50ms (batch fetch)
- Cache hit rate: >80% for active threads

**Deliverable**: "Thread UI with <100ms query performance"

---

### v0.7 - Full-Text Search
**Goal**: Message discoverability at scale

**Phase 1: PostgreSQL Full-Text Search**:
- `tsvector` column with GIN index
- Learning: When is PostgreSQL FTS good enough? (Answer: <1M messages)

**Phase 2: Elasticsearch Migration**:
- Real-time indexing pipeline (Kafka Connect → Elasticsearch)
- Search across channels, workspaces
- Highlighting, fuzzy matching, faceted filters
- Performance comparison: PostgreSQL vs Elasticsearch at 10M messages

**Learning Focus**:
- When to introduce search infrastructure (cost vs benefit)
- Data consistency between PostgreSQL and Elasticsearch
- Index optimization (sharding, replicas)

**Deliverable**: "Search 10M+ messages in <200ms P95"

---

### v1.0 - Production Hardening
**Goal**: Run this in production (if we wanted to)

**Reliability**:
- [ ] Chaos testing (kill random servers, partition network)
- [ ] Circuit breakers on all external calls (Redis, Kafka, PostgreSQL)
- [ ] Rate limiting per user (token bucket algorithm)
- [ ] Graceful degradation (serve stale data if cache down)
- [ ] Health checks with dependency status
- [ ] Automated failover testing

**Observability**:
- [ ] Distributed tracing (OpenTelemetry)
- [ ] Structured logging (JSON) with trace IDs
- [ ] Grafana dashboards (RED metrics: Rate, Errors, Duration)
- [ ] Alerts for SLO violations (P95 latency > 500ms)

**Security**:
- [ ] SQL injection prevention audit
- [ ] XSS protection (CSP headers)
- [ ] Rate limiting on auth endpoints (prevent brute force)
- [ ] Secrets management (Vault, not environment variables)

**Performance**:
- [ ] Load test: 10,000 concurrent WebSocket connections
- [ ] Database query audit (<10ms P95 for all queries)
- [ ] Connection pool tuning (HikariCP)
- [ ] Kafka consumer lag monitoring

**Documentation**:
- [ ] Runbook for common incidents (Redis down, Kafka lag, DB failover)
- [ ] Architecture decision records (ADRs)
- [ ] API versioning strategy
- [ ] Disaster recovery procedures

**Deliverable**: "System that could handle 100k MAU with 99.9% uptime SLA"

---

## Getting Started

### Prerequisites
- Java 21+
- Node.js 18+
- Docker & Docker Compose
- (Optional) Kafka installed locally for v0.4+

### Quick Start

**Single Server (v0.1, v0.2)**:
```bash
# Start infrastructure
docker-compose up -d

# Start backend
cd backend && ./gradlew bootRun

# Start frontend (new terminal)
cd frontend && npm install && npm run dev
```

**Multi-Server (v0.3)**:
```bash
# Start 3 backend servers + Nginx + infrastructure
./start-multi-server.sh

# Stop all
./stop-multi-server.sh
```

**Access**:
- Frontend: http://localhost:3000
- Backend API: http://localhost:9000 (single) or http://localhost (multi-server via Nginx)
- Swagger UI: http://localhost:9000/swagger-ui.html
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001 (planned for v1.0)

### Performance Testing

```bash
# Auto-generates JWT token and runs test
./scripts/run-performance-test.sh

# Custom parameters
CONNECTIONS=10 DURATION=30 MESSAGES_PER_SEC=5 ./scripts/run-performance-test.sh

# Results saved to: local/performance-results/
```

See **[PERFORMANCE_BENCHMARKS.md](./PERFORMANCE_BENCHMARKS.md)** for detailed baseline metrics and version comparisons.

---

## Project Structure

```
slack/
├── backend/                    # Spring Boot backend
│   ├── src/main/java/com/slack/
│   │   ├── application/        # Application services (use case orchestration)
│   │   ├── config/             # Spring config (WebSocket, Redis, Security)
│   │   ├── controller/         # REST + WebSocket controllers
│   │   ├── domain/             # JPA entities (workspace/, channel/, message/, user/)
│   │   ├── dto/                # DTOs (websocket/, request/, response/)
│   │   ├── exception/          # Custom exceptions
│   │   ├── repository/         # Spring Data JPA repositories
│   │   ├── service/            # Business logic
│   │   └── util/               # Utilities
│   └── src/main/resources/
│       ├── api/openapi.yaml    # OpenAPI 3.0 spec
│       └── db/migration/       # Flyway SQL migrations
├── frontend/                   # Next.js 14 (App Router)
├── scripts/                    # Performance test, token generation
├── docker-compose.yml
├── [CLAUDE.md](./CLAUDE.md)                   # AI coding assistant guide
└── [PERFORMANCE_BENCHMARKS.md](./PERFORMANCE_BENCHMARKS.md)  # Measured results
```

---

## Core Learning Goals

### 1. Real-Time Messaging at Scale
- **v0.3**: WebSocket fan-out to 10,000 connections (what are the bottlenecks?)
- **v0.4**: Kafka consumer groups (how to partition load?)
- **Trade-off**: WebSocket vs Server-Sent Events vs Long Polling

### 2. Distributed Systems Challenges
- **CAP Theorem**: Where do we choose CP vs AP? (Message writes: CP, Read status: AP)
- **Consensus**: How to order messages across servers? (Kafka partition log = single leader)
- **Failure Modes**: Network partition, split brain, cascading failures

### 3. Data Consistency Models
- **Strong Consistency**: Message writes (PostgreSQL ACID)
- **Eventual Consistency**: Read status (Redis → PostgreSQL sync)
- **Idempotency**: Client retries, duplicate handling

### 4. Performance Engineering
- **Benchmarking**: Establish baselines, measure improvements
- **Profiling**: JVM heap analysis, query slow logs
- **Optimization**: When to cache? When to denormalize? Measured decisions.

### 5. Observability
- **Metrics**: RED (Rate, Errors, Duration), USE (Utilization, Saturation, Errors)
- **Tracing**: Distributed request tracing across services
- **Logging**: Structured logs for debugging production issues

---

## Lessons Learned

### v0.3 - JWT Token Issue

**Problem**: Performance test showed 0 messages received with `BadJwtException: Signed JWT rejected`

**Root Cause**: OAuth2 auth server regenerates RSA key pairs on restart (in-memory). All previous JWTs become invalid.

**Solution**: Auto-regenerate test tokens before each performance test run (`scripts/generate-test-token.sh`)

**Key Insight**: Development environments with ephemeral keys need token regeneration in CI/CD. Production would use persistent keys (Vault, KMS).

---

## Future Enhancements (Beyond v1.0)

- **Multi-region Deployment**: Geo-distributed Kafka clusters, CRDT for conflict resolution
- **Voice/Video Calls**: WebRTC signaling server, TURN/STUN servers
- **File Uploads**: S3 with presigned URLs, virus scanning, CDN
- **Mobile App**: React Native with same WebSocket backend
- **AI Features**: Message summarization (LLM), smart replies, sentiment analysis
- **Analytics**: Data warehouse (ClickHouse), message metrics, user engagement

---

## Contributing

This is a personal learning project, but feedback welcome:
- Open issues for architectural discussions
- PRs for bug fixes accepted
- No feature requests (following planned roadmap)

---

## References

**Inspired By**:
- [Slack Engineering Blog](https://slack.engineering/)
- [Discord Engineering: How Discord Stores Billions of Messages](https://discord.com/blog/how-discord-stores-billions-of-messages)
- Martin Kleppmann - *Designing Data-Intensive Applications*
- [Kafka: The Definitive Guide](https://www.confluent.io/resources/kafka-the-definitive-guide/)

**Key Architectural Patterns**:
- Transactional Outbox (Chris Richardson)
- CQRS (Command Query Responsibility Segregation)
- Event Sourcing (Martin Fowler)
- Circuit Breaker (Michael Nygard - *Release It!*)

---

## License

MIT License - This is a learning project, use it however helps you learn.
