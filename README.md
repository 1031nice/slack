# Slack Clone - Real-time Messaging System

## Project Overview

A learning-focused Slack clone project that explores **real-world distributed systems challenges**. The goal is to
understand and solve the same architectural problems that real Slack faces: message delivery, ordering guarantees,
horizontal scaling, and fault tolerance.

**Philosophy**: Learn by building production-quality solutions and understanding trade-offs, not by following simplified
tutorials. Every architectural decision is documented with its trade-offs, limitations, and design rationale.

## Architecture Principles

- **Learn by Doing**: Implement real solutions, experience real problems, understand real trade-offs
- **Measured Decisions**: Document why we choose one approach over another (Redis vs Kafka, consistency models, caching
  strategies)
- **Production Mindset**: Design as if this will serve real users, but acknowledge current limitations
- **Observable**: Metrics, structured logging from day one
- **Iterative**: Ship working versions, improve incrementally

## Tech Stack

- **Backend**: Java 21 + Spring Boot 3.2 + Gradle
- **Frontend**: Next.js 14 + TypeScript
- **Database**: PostgreSQL (source of truth)
- **Message Broker**: Redis Pub/Sub (port 6380)
- **Cache**: Redis
- **Search**: Elasticsearch (v0.7)
- **Monitoring**: Micrometer + Prometheus
- **Infrastructure**: Docker Compose

## Current Status: v0.5 ✅

Event-based architecture with timestamp-based message IDs, client-side ordering, and distributed ID generation.
See [v0.5 details](#v05---event-based-architecture-migration) below.

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

### v0.3 - Distributed Messaging ✅

**Goal**: Multi-server architecture with Redis Pub/Sub

**Implemented**:

- Redis Pub/Sub for server-to-server messaging
- Nginx load balancer with sticky sessions (ip_hash)
- Message sequence numbers (Redis INCR per channel)
- Client reconnection with missed message recovery
- ACK message type (handler stubbed for future use)

**Architecture**:

```
Client → WebSocket → Server → PostgreSQL (persist first)
                            ↓
                         Redis Pub/Sub → All Servers → Local WebSocket clients
```

**Performance** (3 servers):

- Throughput: 41.76 msg/sec (comparable to single server at low load)
- Latency: P50=34ms, P95=190ms
- Redis Pub/Sub overhead: ~1ms (negligible)

**Key Design Decisions**:

- **PostgreSQL + Redis Pub/Sub** (same as real Slack)
- DB-first persistence for durability, Redis for real-time speed
- Client recovery via DB query on reconnect (sequence-based)
- Trade-off: Redis failure → temporary loss of real-time (but no data loss)

**Known Limitations**:

- All servers receive all messages (single Redis topic) → v0.9 optimization planned
- No idempotency yet (client retries may create duplicates) → v0.5+
- Single Redis instance (HA in production)

For detailed architectural decision, trade-offs, and Redis vs Kafka comparison, see:
*
*[ADR-0001: Redis Pub/Sub vs Kafka for Multi-Server Broadcast](./docs/adr/0001-redis-vs-kafka-for-multi-server-broadcast.md)
**

**Deliverable**: "Multi-server messaging with Redis Pub/Sub, same architecture as real Slack"

---

### v0.4 - Read Status & Notifications ✅

**Goal**: User engagement features with eventual consistency

**Note**: This version was initially built with sequence-based ordering, then migrated to event-based in v0.5.

**Implemented**:

1. **Unread Count Tracking**:
    - Redis Sorted Set per user per channel
    - Key: `unread:{userId}:{channelId}`
    - Members: messageIds, Scores: timestamps
    - O(1) increment on new message, O(1) clear on read

2. **Mention Detection**:
    - Parse `@username` in messages
    - Notification queue (could be Redis List or DB table)
    - WebSocket notification: `{type: 'MENTION', messageId, channelId}`

3. **Unreads View**:
    - Aggregate view of all unread messages across channels
    - Sorting: newest first, oldest first, by channel
    - API: `GET /api/unreads?sort=newest&limit=50`
    - Uses ZSET's `ZREVRANGE` for time-ordered retrieval

4. **Read Receipts**:
    - Real-time via WebSocket: `{type: 'READ', userId, channelId, lastReadSequence}`
    - Update Redis instantly, async sync to PostgreSQL
    - **Migration planned**: Move to Kafka-based batching (see ADR-0007)

5. **Eventual Consistency**:
    - Redis is cache (fast, may be lost)
    - PostgreSQL is source of truth (slower, durable)
    - **Current**: Immediate async write to PostgreSQL (@Async, non-blocking)
    - **Planned**: Kafka + consumer batching for scalability (v0.4.1)
    - Crash recovery: Restore from PostgreSQL

**Trade-off Analysis**:

- **Why Redis**: 10,000+ read status updates/sec, sub-millisecond latency
- **Why Eventual Consistency**:
    - Strict consistency requires distributed transaction (2PC) across Redis + PostgreSQL
    - Cost: 10x latency, complexity, availability risk
    - Benefit: Acceptable for read status (not critical like message content)
- **Sync Strategy**:
    - Write-through to Redis (fast, real-time)
    - Immediate async write to PostgreSQL (durable, non-blocking)
    - Expected lag: <100ms (thread pool + DB write)

**Known Issues & Planned Improvements**:

**Current Implementation Problems** (discovered post-v0.4):
- ❌ **Unbounded async processing**: `@Async` without backpressure → thread pool exhaustion at scale
- ❌ **Order inversion**: Newer timestamps can be overwritten by older ones in DB
- ❌ **No reconciliation**: Redis-DB divergence not detected/fixed
- ❌ **Silent failures**: DB write failures just logged, no retry

**Planned Migration (v0.4.1)**: Kafka-based batching
- ✅ Durable buffering with Kafka (2-day retention, 3x replication)
- ✅ Consumer batching with deduplication (30-50% write reduction)
- ✅ Order resolution via `GREATEST()` SQL function
- ✅ Reconciliation job (5-minute intervals)
- ✅ Proven at Slack scale (33,000 jobs/sec)

See **[ADR-0007: Kafka-Based Batching for Read Receipt Persistence](./docs/adr/0007-kafka-batching-for-read-receipt-persistence.md)** for detailed analysis of current issues and Kafka-based solution.

**Learning Focus**:

- Eventual consistency trade-offs
- Redis data structures (Sorted Sets, Lists)
- **High-throughput async processing** (Kafka consumer batching)
- **Order guarantees in distributed systems** (timestamp comparison, SQL GREATEST)
- **Reconciliation patterns** (detecting and fixing divergence)
- Consistency lag measurement

**Deliverable**: "Unread counts and @mentions with production-grade persistence layer"

---

### v0.5 - Event-Based Architecture Migration ✅

**Goal**: Migrate from sequence-based to event-based messaging (Slack's production architecture)

**Motivation**:
Current sequence-based ordering creates bottlenecks at scale:

- Single point of coordination (Redis INCR)
- Horizontal scaling limits (all servers coordinate on single counter)
- Single point of failure (Redis down = no messages)
- Global distribution challenges (multi-region coordination)

Real Slack solved this by moving to event-based architecture with distributed ID generation.

**Implemented (3-phase migration)**:

**Phase 1: Timestamp-based ID Generation**

- Introduced `timestampId` to replace `sequence_number`
- Microsecond precision format: `{unix_timestamp_μs}.{3-digit-sequence}`
- Example: `1640995200123456.001` (chronologically sortable, no coordination needed)
- PostgreSQL: Added `timestamp_id` column with migration
- Implemented in `MessageTimestampGenerator` service

**Phase 2: Client-Side Ordering with Time Buffer**

- Client sorts messages by timestamp (not sequence)
- 2-second buffer for late-arriving messages
- Deduplication: Track `Set<event_id>` per channel
- Trade-off: 2s display latency for correct ordering

**Phase 3: Remove Sequence Numbers**

- Replace sequence-based reconnection with timestamp-based
- Heartbeat-based gap detection (10s intervals)
- Remove `MessageSequenceService` and Redis INCR dependency
- Client requests messages since last timestamp on reconnect

**Key Changes**:

```typescript
// Before: Track single number
lastSequenceNumber: number = 0

// After: Track set of seen timestamp IDs
seenTimestampIds: Set<string> = new Set()

// Before: Gap detection
if (msg.sequenceNumber !== lastSequenceNumber + 1) {
  requestMissingMessages(lastSequenceNumber + 1, msg.sequenceNumber - 1)
}

// After: Deduplication + time-based ordering
if (!seenTimestampIds.has(msg.timestampId)) {
  seenTimestampIds.add(msg.timestampId)
  buffer.add(msg)
  sortByTimestamp()
}
```

**Benefits**:

- ✅ Eliminates Redis INCR bottleneck
- ✅ Supports horizontal scaling (no coordination)
- ✅ Multi-region ready (each DC generates IDs independently)
- ✅ Real Slack architecture (production-grade patterns)

**Costs**:

- ❌ Client complexity (Set tracking vs single number)
- ❌ 2-second display buffer (trade-off for correct ordering)
- ❌ Gap detection harder (heartbeat-based, not sequential)

**Learning Focus**:

- Distributed ID generation (Snowflake algorithm)
- At-least-once delivery with idempotency
- Client-side deduplication patterns
- Trade-offs: simplicity vs scalability

**Documentation**: See *
*[ADR-0002: Event-Based Architecture](./docs/adr/0006-event-based-architecture-for-distributed-messaging.md)** for full
rationale and alternatives considered.

**Deliverable**: "Event-based messaging with distributed ID generation, zero Redis coordination"

---

### v0.6 - Thread Support (Next)

**Goal**: Nested conversations with query optimization

**Note**: Threads will use `timestampId` from day one (clean implementation post-migration).

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
- **Idempotency**: Use `timestampId` for deduplication (no duplicate threads)

**Learning Focus**:

- N+1 query problem and solutions
- `@EntityGraph` and batch fetch optimization
- Hot data caching strategies
- Measuring query performance before/after optimization

**Deliverable**: "Thread UI with optimized query performance"

---

### v0.7 - File Uploads

**Goal**: Share images, documents in channels

**Planned**:

- S3-compatible storage (MinIO for local dev)
- Presigned URL upload (client → S3 direct)
- File metadata in PostgreSQL
- Virus scanning (ClamAV)
- Thumbnail generation for images
- CDN for static assets

**Learning Focus**:

- Object storage patterns
- Presigned URLs for security
- Async processing (scanning, thumbnails)

**Deliverable**: "Upload and share files in messages"

---

### v0.8 - Full-Text Search

**Goal**: Message discoverability at scale

**Phase 1: PostgreSQL Full-Text Search**:

- `tsvector` column with GIN index
- Learning: When is PostgreSQL FTS good enough? (Answer: <1M messages)

**Phase 2: Elasticsearch Migration**:

- Real-time indexing pipeline (Logstash or custom)
- Search across channels, workspaces
- Highlighting, fuzzy matching, faceted filters
- Performance comparison: PostgreSQL vs Elasticsearch at 10M messages

**Learning Focus**:

- When to introduce search infrastructure (cost vs benefit)
- Data consistency between PostgreSQL and Elasticsearch
- Index optimization (sharding, replicas)

**Deliverable**: "Full-text search with PostgreSQL vs Elasticsearch comparison"

---

### v0.9 - Performance Optimization

**Goal**: Optimize Redis broadcasting and reduce unnecessary traffic

**Planned**:

1. **Channel-Specific Redis Topics**:
    - Current: Single topic `slack:websocket:messages` → all servers receive all messages
    - New: Per-channel topics `slack:channel:{channelId}` → servers subscribe only to active channels
   ```java
   // Dynamic subscription management
   @WebSocketEventListener
   public void handleSubscribe(SessionSubscribeEvent event) {
       String channelId = extractChannelId(event.getDestination());
       redisListenerContainer.addMessageListener(
           channelListeners.get(channelId),
           new ChannelTopic("slack:channel:" + channelId)
       );
   }
   ```

2. **Subscription Tracking**:
    - Track which channels have active subscribers per server
    - Subscribe to Redis topic when first client subscribes to channel
    - Unsubscribe when last client leaves channel
    - Redis Set: `server:{serverId}:active-channels` → {channelId1, channelId2, ...}

3. **Alternative: Consistent Hashing** (Advanced):
    - Assign each channel to specific server (like Slack's Channel Server)
    - Route messages through designated server
    - Requires sticky sessions by channel (not just by user)
    - More complex but eliminates broadcast entirely

**Performance Goals**:

- Reduce network traffic by 80%+ (only receive relevant messages)
- Measure: messages received vs messages delivered ratio

**Trade-off Analysis**:
| Approach | Pros | Cons |
|----------|------|------|
| **Current (Single Topic)** | Simple, no subscription management | All servers receive all messages |
| **Channel Topics** | Efficient, servers receive only subscribed channels | Dynamic subscription management |
| **Consistent Hashing** | No broadcast needed, like real Slack | Complex, requires channel-aware routing |

**Learning Focus**:

- Dynamic Redis subscription management
- Trade-offs: simplicity vs efficiency
- Measuring and optimizing network traffic
- When premature optimization becomes necessary optimization

**Deliverable**: "Bandwidth reduction measured, channel-specific topics implemented"

---

### v1.0 - Production Hardening

**Goal**: Run this in production (if we wanted to)

**Reliability**:

- [ ] Chaos testing (kill random servers, partition network)
- [ ] Circuit breakers on all external calls (Redis, PostgreSQL)
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
- [ ] Database query audit and optimization
- [ ] Connection pool tuning (HikariCP)

**Documentation**:

- [ ] Runbook for common incidents (Redis down, DB failover)
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
- **Multi-Server only**: [auth-platform](https://github.com/1031nice/auth-platform) OAuth2 server (clone
  to `../auth-platform` directory)

### Quick Start

**Single Server (v0.1, v0.2)**:

```bash
# Start infrastructure (PostgreSQL, Redis)
docker-compose up -d

# Start backend
cd backend && ./gradlew bootRun

# Start frontend (new terminal)
cd frontend && npm install && npm run dev
```

**Multi-Server (v0.3)**:

```bash
# Requires: Clone [auth-platform](https://github.com/1031nice/auth-platform) to ../auth-platform
# Starts: Auth Platform + 3 backend servers + Nginx + infrastructure
./start-all.sh

# Stop all
./stop-all.sh
```

**Access**:

- Frontend: http://localhost:3000
- Backend API: http://localhost:9000 (single) or http://localhost:8888 (multi-server via Nginx)
- Swagger UI: http://localhost:9000/swagger-ui.html
- Prometheus: http://localhost:9090
- Redis: localhost:6380

### Performance Testing

```bash
# First time: Generate test token (requires auth-platform running)
./scripts/generate-test-token.sh

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
├── CLAUDE.md                   # AI coding assistant guide
└── PERFORMANCE_BENCHMARKS.md  # Measured results
```

---

## Core Learning Goals

### 1. Real-Time Messaging at Scale

- **v0.3**: WebSocket fan-out to multiple servers (how to broadcast?)
- **v0.4**: Read status at scale (consistency models)
- **Trade-off**: WebSocket vs Server-Sent Events vs Long Polling

### 2. Distributed Systems Challenges

- **CAP Theorem**: Where do we choose CP vs AP? (Message writes: CP, Read status: AP)
- **Ordering**: How to order messages across servers? (Sequence numbers per channel)
- **Failure Modes**: Network partition, Redis failure, client disconnect

### 3. Data Consistency Models

- **Strong Consistency**: Message writes (PostgreSQL ACID)
- **Eventual Consistency**: Read status (Redis → PostgreSQL sync)
- **Hybrid**: Pub/Sub (ephemeral) + DB recovery (durable)

### 4. Performance Engineering

- **Benchmarking**: Establish baselines, measure improvements
- **Profiling**: JVM heap analysis, query slow logs
- **Optimization**: When to cache? When to denormalize? Measured decisions.

### 5. Observability

- **Metrics**: RED (Rate, Errors, Duration), USE (Utilization, Saturation, Errors)
- **Logging**: Structured logs for debugging production issues
- **Monitoring**: Prometheus + Grafana

---

## Troubleshooting

Common issues and solutions are documented in [Lessons Learned](#lessons-learned) below. For detailed bug investigation,
use the [troubleshooting issue template](.github/ISSUE_TEMPLATE/troubleshooting.md).

## Lessons Learned

### v0.3 - Redis Pub/Sub vs Kafka Decision

**Key Insight**: Separate concerns - PostgreSQL for durability, Redis Pub/Sub for real-time speed.

Real Slack uses the same architecture. Message durability comes from DB-first persistence, not from the pub/sub
mechanism. Redis Pub/Sub is chosen for speed (~1ms), not durability.

For full analysis and trade-offs, see *
*[ADR-0001: Redis Pub/Sub vs Kafka](./docs/adr/0001-redis-vs-kafka-for-multi-server-broadcast.md)**

### v0.3 - JWT Token Issue

**Problem**: Performance test showed 0 messages received with `BadJwtException: Signed JWT rejected`

**Root Cause**: OAuth2 auth server regenerates RSA key pairs on restart (in-memory). All previous JWTs become invalid.

**Solution**: Auto-regenerate test tokens before each performance test run (`scripts/generate-test-token.sh`)

**Key Insight**: Development environments with ephemeral keys need token regeneration in CI/CD. Production would use
persistent keys (Vault, KMS).

---

## Future Enhancements (Beyond v1.0)

- **Multi-region Deployment**: Geo-distributed Redis, CRDT for conflict resolution
- **Voice/Video Calls**: WebRTC signaling server, TURN/STUN servers
- **Mobile App**: React Native with same WebSocket backend
- **AI Features**: Message summarization (LLM), smart replies
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
- [Real-time Messaging | Engineering at Slack](https://slack.engineering/real-time-messaging/)
- [Discord Engineering: How Discord Stores Billions of Messages](https://discord.com/blog/how-discord-stores-billions-of-messages)
- Martin Kleppmann - *Designing Data-Intensive Applications*

**Key Architectural Patterns**:

- Pub/Sub Pattern (Redis)
- Database as Source of Truth (PostgreSQL)
- Eventual Consistency (Read status)
- Circuit Breaker (Michael Nygard - *Release It!*)

---

## License

MIT License - This is a learning project, use it however helps you learn.
