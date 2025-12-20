# ADR-0001: Redis Pub/Sub vs Kafka for Multi-Server Broadcast

- **Status**: Accepted ✅
- **Date**: 2024-12-10
- **Context**: v0.3 - Distributed Messaging
- **Related**: [GitHub Issue #1](https://github.com/1031nice/slack/issues/1)

---

## Problem Statement

Single-server WebSocket architecture cannot scale horizontally and has a single point of failure. Need pub/sub mechanism
for multi-server message broadcasting.

## Context

- Messages already persisted to PostgreSQL (DB-first)
- WebSocket connections are stateful (users connect to specific servers)
- Real Slack uses Redis Pub/Sub for this exact use case [[1]](#references)

## Proposed Solutions

### Option 1: PostgreSQL + Redis Pub/Sub ⭐ (Chosen)

**Architecture:** `Client → Server → PostgreSQL (persist) → Redis Pub/Sub (broadcast) → All Servers → WebSocket`

**How the combination works:**

- **PostgreSQL**: Source of truth, durability, client recovery
- **Redis Pub/Sub**: Fast real-time broadcast (sub-1ms [[2]](#references))
- **Trade-off**: Redis failure → temporary loss of real-time, but no data loss (DB persisted)

**Why this combination:**

- ✅ **Durability**: PostgreSQL handles persistence
- ✅ **Speed**: Redis handles real-time broadcast (sub-1ms [[2]](#references))
- ✅ **Simple**: Two components, clear responsibilities
- ✅ **Real-world**: Same as Slack [[1]](#references)
- ✅ **Recovery**: Client queries DB for missed messages (sequence numbers)

**Limitation:**

- ⚠️ Redis down → no real-time broadcasting (but messages still saved to DB)
- ⚠️ Manual recovery: Client must query DB on reconnect

---

### Option 2: PostgreSQL + Outbox + Kafka

**Architecture:** `Client → Server → PostgreSQL + Outbox → Background Worker → Kafka → Consumers → WebSocket`

**How the combination works:**

- **PostgreSQL**: Source of truth + outbox table (transactional)
- **Outbox Pattern**: Decouple DB commit from message delivery
- **Kafka**: Guaranteed delivery, automatic retry, durable log
- **Trade-off**: Higher latency (10-50ms [[3]](#references)[[4]](#references) + polling), more complexity

**Why this combination:**

- ✅ **Durability**: PostgreSQL handles persistence
- ✅ **Guaranteed delivery**: Kafka + Outbox ensures at-least-once
- ✅ **Automatic retry**: No manual client recovery needed
- ✅ **Message replay**: Can replay from Kafka offset

**Limitation:**

- ⚠️ Higher latency: 10-50ms [[3]](#references)[[4]](#references) + outbox polling (1+ sec total)
- ⚠️ More complex: Outbox table, background worker, Kafka cluster
- ⚠️ Over-engineered: For use case where DB is already source of truth

**When this combination makes sense:**

- Event-driven microservices (event sourcing, CQRS)
- Message replay requirements
- Cross-service communication (not just real-time messaging)

---

## Decision: PostgreSQL + Redis Pub/Sub

**Why this combination:**

1. **Real-world validation**: Slack uses the same combination [[1]](#references)
    - PostgreSQL for durability, Redis Pub/Sub for speed
    - Proven at scale: 32-47M DAU [[5]](#references), 99.99% SLA [[6]](#references)

2. **Clear separation of concerns**:
    - **PostgreSQL**: Handles durability, source of truth
    - **Redis Pub/Sub**: Handles speed, real-time broadcast (sub-1ms [[2]](#references))
    - Each tool does what it's best at

3. **Speed matters for real-time chat**:
    - PostgreSQL + Redis: ~500ms end-to-end (DB persist + Redis broadcast)
    - PostgreSQL + Kafka: 1+ sec (DB persist + outbox poll + Kafka)

4. **Simplicity**: Two components vs four (DB, Outbox, Worker, Kafka)

**Trade-offs accepted:**

| Trade-off                              | Why acceptable                                             |
|----------------------------------------|------------------------------------------------------------|
| Redis failure → no real-time broadcast | PostgreSQL still persists, clients query DB on reconnect   |
| Manual client recovery                 | Simple client logic, same as Slack                         |
| All servers receive all messages       | Acceptable now, optimize in v0.8 (channel-specific topics) |
| Single Redis instance                  | Learning project; Redis HA in production                   |

**Comparison:**

|                        | **PostgreSQL + Redis**            | **PostgreSQL + Outbox + Kafka**              |
|------------------------|-----------------------------------|----------------------------------------------|
| **Durability**         | ✅ PostgreSQL                      | ✅ PostgreSQL                                 |
| **Broadcast latency**  | Sub-1ms [[2]](#references)        | 10-50ms [[3]](#references)[[4]](#references) |
| **Total latency**      | ~500ms                            | 1+ sec (+ outbox polling)                    |
| **Delivery guarantee** | Client recovery (DB query)        | Automatic (Outbox retry)                     |
| **Complexity**         | 2 components                      | 4 components                                 |
| **Who uses it**        | Slack [[1]](#references), Discord | LinkedIn, Uber (event streaming)             |
| **Best for**           | Real-time messaging               | Event-driven systems                         |

---

## Key Lessons

**What we learned:**

- **Evaluate tool combinations, not individual tools**: PostgreSQL + Redis vs PostgreSQL + Kafka, both provide
  durability
- **Separation of concerns**: DB for durability, messaging system for broadcast (Slack's approach)
- **Trade-offs are intentional**: Manual recovery → simplicity, 2 components → learning clarity
- **Real-world validation matters**: Following Slack's architecture validates this choice

**Current limitation:**

- All servers receive all messages (single Redis topic)
- Future optimization (v0.8): Channel-specific topics for 80%+ bandwidth reduction

**When we'd use PostgreSQL + Kafka instead:**

- Event-driven microservices (cross-service communication)
- Event sourcing / CQRS (need event log)
- Message replay requirements (debugging, reprocessing)

---

## References

1. [Real-time Messaging | Engineering at Slack](https://slack.engineering/real-time-messaging/)
    - "Now chat messages are persisted in the chat database before being sent to the users over WebSocket"

2. [Benchmarking Message Queue Latency](https://bravenewgeek.com/benchmarking-message-queue-latency/) | [Redis vs Kafka](https://thenewstack.io/redis-pub-sub-vs-apache-kafka/)
    - Redis Pub/Sub: sub-millisecond latency

3. [Kafka Performance](https://developer.confluent.io/learn/kafka-performance/)
    - Kafka latency: 10-50ms typical

4. [Kafka Latency Optimization](https://www.automq.com/blog/kafka-latency-optimization-strategies-best-practices)

5. [Slack Statistics 2024](https://www.demandsage.com/slack-statistics/)
    - 32-47 million DAU (2024-2025)

6. [Slack SLA](https://slack.com/terms/service-level-agreement)
    - 99.99% uptime guarantee
