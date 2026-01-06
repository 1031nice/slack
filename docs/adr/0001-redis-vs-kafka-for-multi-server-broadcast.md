# ADR-0001: Redis Pub/Sub for Multi-Server Broadcasting

## Metadata

- **Status**: Accepted ✅
- **Date**: 2024-12-10
- **Context**: v0.3 - Distributed Messaging
- **Deciders**: Architecture team
- **Related Deep Dive**: [Deep Dive 01 § 3 (Broadcast Layer Selection)](../deepdives/01-multi-server-broadcasting.md#3-broadcast-layer-selection)
- **Related**: [GitHub Issue #1](https://github.com/1031nice/slack/issues/1)

---

## TL;DR (Executive Summary)

**Decision**: Use **Redis Pub/Sub** (not Kafka) for server-to-server message broadcasting.

**Key Trade-off**: Accept fire-and-forget broadcast (no broker durability) in exchange for <1ms latency.

**Rationale**: DB-first persistence already guarantees durability; we only need Redis for speed.

---

## Context

### The Problem

Single-server WebSocket architecture cannot scale horizontally and has a single point of failure. When a user sends a message to Server A, clients connected to Server B, C, D must receive it in real-time. We need a mechanism for servers to broadcast messages to each other.

### Constraints

- **Latency budget**: <100ms end-to-end (DB write + broker + WebSocket)
- **Durability**: Already handled by PostgreSQL via DB-first (see Deep Dive 01 § 2.2)
- **Scale**: 10,000+ messages/sec across N servers
- **Horizontal scaling**: Must work without O(N²) complexity

### Success Criteria

- Broadcast latency: <5ms P99 (broker propagation only)
- 0% message loss (DB guarantees durability)
- Linear scalability as server count increases

---

## Decision

### What We Chose

**Redis Pub/Sub** for server-to-server message propagation

```
Client → API → [DB] → [Redis] → Gateway Servers → WebSocket
         Step 2   Step 3    Step 4              Step 5
         ~30ms    <1ms      <1ms                <5ms
```

**Visual details**: See [Deep Dive 01 § 4.1](../deepdives/01-multi-server-broadcasting.md#41-the-chosen-flow) for sequence diagram

### Why This Choice

| Criteria                  | Weight      | Kafka       | NATS        | **Redis**   |
|---------------------------|-------------|-------------|-------------|-------------|
| **Latency**               | 🔴 Critical | ❌ 10-50ms   | ✅ <1ms      | ✅ <1ms      |
| **Broker durability**     | 🟢 N/A*     | ✅ Yes       | ⚠️ Optional  | ❌ No        |
| **Operational complexity**| 🟡 Medium   | ❌ High      | ✅ Low       | ✅ Low       |
| **Real-world validation** | 🟡 Medium   | LinkedIn    | Startups    | ✅ Slack     |

\* Broker durability is N/A because DB-first already guarantees message persistence.

**Primary reason**: We need **speed over broker durability** because PostgreSQL already provides durability. Redis wins on the critical constraint (latency) while meeting all other requirements.

---

## Consequences

### What We Gain

- ✅ **Sub-millisecond broadcast**: <1ms Redis propagation keeps total latency <100ms
- ✅ **Operational simplicity**: No Zookeeper, partitions, or offset tracking
- ✅ **Real-world proven**: Slack uses PostgreSQL + Redis [[1]](#references) (32-47M DAU, 99.99% SLA)
- ✅ **Existing infrastructure**: Already using Redis for caching

### What We Accept

- ⚠️ **No broker durability**: Redis crash → lose in-flight messages
  - **Tech impact**: Messages in Redis buffer (not yet delivered to subscribers) are lost
  - **Business impact**: Users experience brief delivery delay (seconds to minutes) but zero data loss
  - **Why acceptable**: Our priority is **data integrity > real-time UX**. PostgreSQL guarantees persistence; clients query DB on reconnect using sequence numbers.

- ⚠️ **Manual client recovery**: Clients must fetch missed messages from DB on reconnect
  - **Business impact**: Adds client complexity, but matches Slack's pattern
  - **Why acceptable**: Simple sequence-based recovery is proven at scale

- ⚠️ **Broadcast to all servers**: Every server receives every message (no filtering)
  - **Tech impact**: Wastes bandwidth when servers have no clients for that channel
  - **Business impact**: Acceptable at current scale; optimize in v0.8 with channel-specific topics (80%+ bandwidth reduction)
  - **Why acceptable**: Simplicity for learning phase; optimization path is clear

### What We Must Monitor

- 📊 **Redis availability**: Alert if down >30sec (impacts real-time delivery)
- 📊 **Broadcast latency**: Alert if P99 >5ms (indicates Redis performance degradation)
- 📊 **Client reconnection rate**: High rate suggests Redis instability
- 📊 **Message loss rate**: Should be 0% (DB persisted)

---

## Alternatives Considered

### Alternative 1: Kafka

**Rejected due to**: Unacceptable latency (10-50ms) and unnecessary broker durability when DB-first already provides persistence.

**When Kafka is right**: Event sourcing, microservices communication, audit logs requiring message replay.

**Detailed analysis**: [Deep Dive 01 § 3.1](../deepdives/01-multi-server-broadcasting.md#31-broker-comparison)

---

### Alternative 2: NATS

**Rejected due to**: No significant advantage over Redis; team has zero operational experience.

**When NATS is right**: Lightweight microservices messaging, IoT, edge computing.

**Detailed analysis**: [Deep Dive 01 § 3.1](../deepdives/01-multi-server-broadcasting.md#31-broker-comparison)

---

### Alternative 3: PostgreSQL LISTEN/NOTIFY

**Rejected due to**: Database becomes high-throughput message bus bottleneck; violates separation of concerns.

**When PostgreSQL Pub/Sub is right**: Low-frequency notifications (<100 msg/sec), simple single-DB deployments.

**Detailed analysis**: [Deep Dive 01 § 2.1](../deepdives/01-multi-server-broadcasting.md#option-a-database-as-message-bus)

---

## Implementation

### Critical Path

```java
@Transactional
public Message sendMessage(MessageRequest request) {
    // Step 2: Persist to DB first
    Message message = messageRepository.save(new Message(...));

    // Step 3: Broadcast to Redis (after DB commit)
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisPublisher.publish(message); // <-- Redis Pub/Sub
            }
        }
    );

    return message;
}
```

### Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Redis down → no real-time | 🟡 Medium | Alert on-call; clients query DB on reconnect |
| High latency (>5ms) | 🟢 Low | Monitor Redis; scale horizontally if needed |
| Bandwidth waste (all servers) | 🟢 Low | Acceptable now; optimize v0.8 with topic sharding |

### Reference Implementation

- Publisher: `RedisMessagePublisher.java:42`
- Subscriber: `RedisMessageSubscriber.java:28`
- Race condition handling: [Deep Dive 02 § 3.1](../deepdives/02-fan-out-consistency.md)

---

## Validation

**Multi-server broadcast test** (See [Deep Dive 01 § 6](../deepdives/01-multi-server-broadcasting.md#6-validation-plan))
- Setup: 4 servers, 10k clients, 1k msg/sec
- Result: ✅ 100% delivery, P99 <40ms

**Benchmark**: Redis <1ms vs Kafka 15-45ms ([experiments/redis-vs-kafka-bench.md](../experiments/redis-vs-kafka-bench.md))

---

## References

### Internal

- **[Deep Dive 01: Multi-Server Broadcasting Architecture](../deepdives/01-multi-server-broadcasting.md)**
  - § 2.1: Database as Message Bus (rejected)
  - § 3.1: Redis vs Kafka vs NATS comparison
  - § 6: Validation results

- **[Experiment: Redis vs Kafka Benchmark](../experiments/redis-vs-kafka-bench.md)**: Latency comparison under load

### External

1. **[Real-time Messaging | Engineering at Slack](https://slack.engineering/real-time-messaging/)**: Validates our DB-first + Redis approach
2. **[Benchmarking Message Queue Latency](https://bravenewgeek.com/benchmarking-message-queue-latency/)**: Redis <1ms latency data
3. **[Kafka Performance](https://developer.confluent.io/learn/kafka-performance/)**: Kafka latency 10-50ms typical
4. **[Slack Statistics 2024](https://www.demandsage.com/slack-statistics/)**: 32-47M DAU (2024-2025)
5. **[Slack SLA](https://slack.com/terms/service-level-agreement)**: 99.99% uptime guarantee

---

## Revision History

- **2024-12-10**: Initial decision (v0.3 - Distributed Messaging)
- **2025-01-06**: Restructured with Deep Dive cross-references and improved consequence analysis
