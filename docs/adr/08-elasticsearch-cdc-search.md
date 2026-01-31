# ADR-0008: ElasticSearch with CDC for Full-Text Search

## Metadata

- **Status**: Proposed 📝
- **Date**: 2026-01-31
- **Context**: Future - Search Infrastructure (Post-1M Messages)
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 08: Real-time Message Search](../deepdives/08-realtime-search.md)

---

## TL;DR (Executive Summary)

**Decision**: Use **ElasticSearch with CDC (Change Data Capture)** via Debezium + Kafka for full-text message search.

**Key Trade-off**: Accept **1-3 second indexing lag** in exchange for **guaranteed durability**, **crash recovery**, and **operational decoupling**.

**Rationale**: At scale (1M+ messages/day), PostgreSQL full-text search cannot maintain sub-500ms query latency. Dual-write approaches (synchronous/asynchronous) introduce data loss risks and ordering violations. CDC provides production-grade reliability by treating the database WAL as the source of truth, ensuring every committed message eventually reaches the search index without application-level coordination.

---

## Context

### The Problem

Chat applications require instant, accurate search across massive message volumes:

1. **Scale**: 10,000 users × 100 messages/day = **1M messages/day** = **365M messages/year**.
2. **Latency Budget**: Search results must appear in **<500ms** to feel "instant".
3. **Real-time Paradox**: Users expect their own messages to be searchable immediately after sending.

### Constraints

- **Query Speed**: Sub-second response time for keyword searches across billions of messages.
- **Indexing Lag**: Minimize delay between message send and searchability (target: <5 seconds).
- **Durability**: Zero data loss. Every committed message must eventually be indexed.
- **Crash Recovery**: System must auto-recover from failures without manual intervention.

---

## Decision

### What We Chose

**ElasticSearch with CDC Pipeline**:

```
PostgreSQL WAL → Debezium → Kafka → Kafka Connect (ES Sink) → ElasticSearch
```

**Primary Flow**:
1. User sends message → API saves to PostgreSQL (COMMIT)
2. PostgreSQL WAL emits change event
3. Debezium captures event → Kafka topic: `db.messages`
4. Kafka Connect (ElasticSearch Sink) consumes → ES index
5. User searches → Query ElasticSearch

**Optional Fallback** (for "I just sent this" UX):
- Query both PostgreSQL (recent 5 minutes) and ElasticSearch (historical)
- Merge results with DB results ranked higher

### Why This Choice

| Strategy | Durability | Query Speed | Indexing Lag | Crash Recovery | Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL `tsvector`** | 🟢 ACID | 🔴 Slow (>100M rows) | 🟢 0ms | 🟢 N/A | Rejected (Scale) |
| **Dual-Write (Sync)** | 🔴 At-Risk | 🟢 Fast | 🟢 0ms | 🔴 Manual | Rejected (Latency Coupling) |
| **Dual-Write (Async Thread)** | 🔴 **Volatile (Memory)** | 🟢 Fast | 🟢 <100ms | 🔴 **Manual (DB Scan)** | Rejected (Data Loss) |
| **CDC (Debezium + Kafka)** | 🟢 **Guaranteed (Disk)** | 🟢 Fast | 🟡 1-3s | 🟢 **Auto (WAL/Kafka)** | **Selected** |
| **Hybrid (DB + ES)** | 🟢 Strong (Recent) | 🟢 Fast | 🟢 0ms (Perceived) | 🟡 Partial | Optional Fallback |

**Key Insight**: The difference between Async Dual-Write and CDC is **file-based vs memory-based**:

```
Dual-Write (Background Thread):
  DB → Memory Queue → ES
  ↑ Server crash → Queue lost

CDC (Kafka):
  DB → WAL File → Debezium → Kafka File → ES
  ↑ Server crash → Files remain, auto-recovery
```

---

## Consequences

### Positive Impacts

✅ **Durability Guarantee**:
- DB COMMIT → WAL file (fsync to disk)
- Debezium reads from WAL file (survives crashes)
- Kafka stores events on disk with replication
- **Result**: Zero data loss even with server crashes

✅ **Ordering Preservation**:
- Kafka partitions by `channel_id`
- All messages from same channel → same partition
- Single consumer per partition → guaranteed order

✅ **Backpressure Handling**:
- Kafka is disk-based (not memory)
- Can buffer millions of events without OOM
- Consumer processes at its own pace

✅ **Replayability**:
- Kafka retains logs (configurable retention)
- Can reset consumer offset to any point in time
- Rebuild ES index by replaying Kafka topic

✅ **Operational Decoupling**:
- Application code only writes to DB
- ES can be upgraded, replaced, or taken offline without touching application code
- Same CDC stream feeds multiple systems (ES, Data Warehouse, Analytics)

### Negative Impacts & Mitigations

❌ **Operational Complexity**:
- Requires Kafka + Debezium + Kafka Connect infrastructure
- **Mitigation**: Use managed services (AWS MSK, Confluent Cloud) or containerized setup
- **When to Accept**: For systems where search is critical and data loss is unacceptable

❌ **Indexing Lag (1-3 seconds)**:
- Users may complain "I can't find my message"
- **Mitigation 1**: Implement Hybrid pattern (query DB for user's own recent messages)
- **Mitigation 2**: UI feedback: "Indexing... (results may be incomplete)"

❌ **Schema Evolution Complexity**:
- Mapping changes require careful coordination
- **Mitigation**: Zero-downtime alias pattern:
  ```
  messages (alias) → messages_v1 (index)
                   → messages_v2 (index, during migration)
  ```

### Implementation Details

**ElasticSearch Index Mapping**:
```json
{
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "channel_id": { "type": "keyword" },
      "user_id": { "type": "keyword" },
      "content": { 
        "type": "text",
        "analyzer": "standard",
        "search_analyzer": "standard"
      },
      "created_at": { "type": "date" },
      "has_link": { "type": "boolean" },
      "has_attachment": { "type": "boolean" }
    }
  }
}
```

**Query Example (Advanced Filters)**:
```json
GET /messages/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "content": "budget" } }
      ],
      "filter": [
        { "term": { "channel_id": "123" } },
        { "range": { "created_at": { "gte": "2026-01-01" } } },
        { "term": { "has_link": true } }
      ]
    }
  }
}
```

---

## Alternatives Considered

### Alternative 1: PostgreSQL Full-Text Search (`tsvector`)

**Approach**: Use PostgreSQL's built-in `tsvector` and `GIN` indexes.

```sql
CREATE INDEX idx_messages_fts ON messages USING GIN(search_vector);
SELECT * FROM messages WHERE search_vector @@ to_tsquery('budget');
```

**Why Rejected**:
- **Performance Ceiling**: GIN indexes degrade at >100M rows
- **Write Amplification**: Every message insert must also update the FTS index
- **Limited Features**: No fuzzy matching, no advanced relevance tuning (BM25)
- **Scalability**: Cannot horizontally scale search independently from database

### Alternative 2: Dual-Write (Synchronous)

**Approach**: Application writes to both PostgreSQL and ElasticSearch simultaneously.

```java
messageRepository.save(message);       // PostgreSQL (10ms)
elasticSearchClient.index(message);    // ElasticSearch (50ms)
return "Message sent";                 // User waits 60ms
```

**Why Rejected**:
- **Latency Coupling**: User response time depends on ES performance. If ES is slow (500ms) or down, chat becomes unusable.
- **Availability Risk**: ES maintenance or failures directly impact core messaging functionality.
- **Consistency Risk**: If ES write fails, data is missing from search (permanent loss without retry logic).

### Alternative 3: Dual-Write (Asynchronous with Background Thread)

**Approach**: Write to DB synchronously, index to ES in background thread.

```java
messageRepository.save(message);       // PostgreSQL (10ms)
executorService.submit(() -> {
    elasticSearchClient.index(message); // Background thread
});
return "Message sent";                 // User waits 10ms
```

**Why Rejected** (Most Critical):

1. **Volatility (Data Loss Risk)**:
   - Thread pool queue is in-memory. Server crash → all pending indexing tasks lost.
   - No way to know which messages failed to index after restart.
   - Recovery requires full DB scan (expensive and disruptive).

2. **Ordering Violations**:
   - Multiple threads process messages concurrently.
   - Message A (sent first) may be indexed after Message B (sent second).
   - Search results show messages in wrong chronological order.

3. **Backpressure Failure**:
   - If ES is slow, queue grows unbounded in memory.
   - Eventually triggers OutOfMemoryError, crashing the application server.
   - No graceful degradation.

4. **No Replayability**:
   - Cannot rebuild ES index from a specific point in time.
   - Mapping changes require full DB export and re-import.

### Alternative 4: Hybrid (Recent DB + Historical ES)

**Approach**: Query PostgreSQL for "recent" messages (last 5 minutes) and ElasticSearch for "historical" messages.

```javascript
const recentResults = await db.query(
  "SELECT * FROM messages WHERE created_at > NOW() - INTERVAL '5 minutes' AND content ILIKE '%query%'"
);
const historicalResults = await es.search({ query: { match: { content: query } } });
return merge(recentResults, historicalResults);
```

**Why Not Primary Choice**:
- **Complexity**: Dual query logic, result merging, deduplication.
- **Edge Cases**: What if a message is in both DB and ES? How to rank?
- **Verdict**: Acceptable as **optional fallback** for critical UX scenarios, not primary architecture.

---

## Performance Expectations

Based on industry benchmarks (ElasticSearch 8.x, 3-node cluster):

| Metric | Target | Notes |
| :--- | :--- | :--- |
| **Indexing Throughput** | 10k-50k docs/sec | Depends on document size and cluster size |
| **Query Latency (P95)** | <200ms | For typical keyword searches |
| **Index Size** | ~1.5x raw data | With standard compression |
| **Indexing Lag** | 1-3 seconds | CDC pipeline latency |

---

## Risks & Mitigations

### Risk 1: ElasticSearch Cluster Failure

**Impact**: Search is completely unavailable.

**Mitigation**:
- Replicas (2+ copies of each shard)
- Fallback to PostgreSQL `ILIKE` for critical queries (degraded mode)
- Monitoring: Alert on cluster health, shard status

### Risk 2: Indexing Lag Complaints

**Impact**: Users complain "I can't find my message".

**Mitigation**:
- Implement Hybrid pattern (DB + ES) for user's own messages
- UI feedback: "Indexing... (results may be incomplete)"
- SLA: Document expected lag (1-3s) in user-facing docs

### Risk 3: Mapping Explosion

**Impact**: Too many fields → slow queries, high memory usage.

**Mitigation**:
- Limit indexed fields (don't index everything)
- Use `dynamic: false` to prevent auto-field creation
- Regular mapping audits

---

## Success Metrics

**Before (No Search)**:
- Search capability: None (manual DB queries only)
- Query latency: N/A

**After (CDC + ElasticSearch)**:
- Query latency: P95 <200ms for keyword searches
- Indexing lag: P95 <3 seconds
- Data loss rate: 0% (guaranteed by CDC)
- Crash recovery time: <5 minutes (automatic)

**Measure**:
- ElasticSearch query latency (P50, P95, P99)
- Indexing lag (time from DB COMMIT to ES searchable)
- Kafka consumer lag (events pending in Kafka)
- Search result relevance (user feedback, click-through rate)

---

## Implementation Roadmap

**Phase 1: Infrastructure Setup** (Week 1-2)
1. Set up Debezium connector for `messages` table
2. Configure Kafka topic: `db.messages`
3. Deploy Kafka Connect ElasticSearch Sink

**Phase 2: Index Design** (Week 3)
1. Design ElasticSearch mappings
2. Create index with proper analyzers
3. Test query performance with sample data

**Phase 3: Integration** (Week 4-5)
1. Implement search API endpoint
2. Build frontend search UI
3. Add filters (channel, date range, has:link)

**Phase 4: Optimization** (Week 6+)
1. Benchmark with 100M+ documents
2. Implement Hybrid pattern (optional)
3. Tune relevance scoring (BM25 parameters)

---

## Related Decisions

- **[ADR-0004: Event-Driven Architecture](./04-event-driven-architecture.md)**: CDC as a foundation for decoupled systems
- **[ADR-0003: Snowflake ID Ordering](./03-snowflake-id-ordering.md)**: Ensuring search results respect message chronology

---

## References

- [Slack Engineering: Search Infrastructure](https://slack.engineering/)
- [ElasticSearch: The Definitive Guide](https://www.elastic.co/guide/en/elasticsearch/guide/current/index.html)
- [Debezium: Change Data Capture](https://debezium.io/)
- Martin Kleppmann - *Designing Data-Intensive Applications*, Chapter 11: Stream Processing
- [Discord: How We Scaled Search](https://discord.com/blog/how-discord-indexes-billions-of-messages)

---

## Notes

**Why CDC over Dual-Write?**

The critical difference is **atomicity guarantee**:

1. PostgreSQL COMMIT writes to WAL file (fsync to disk).
2. Debezium reads WAL file (not DB memory).
3. If Debezium crashes, it resumes from last read position (LSN stored in Kafka).
4. If Kafka crashes, Debezium retries until success.
5. If ES crashes, Kafka Consumer retries indefinitely (offset not committed until success).

**Result**: Once DB COMMIT succeeds, the change event is guaranteed to eventually reach ES (at-least-once delivery).

**When to Choose CDC**:
- For systems where search is critical and data loss is unacceptable (Slack, Discord, LinkedIn scale)
- When you need operational flexibility (upgrade ES without touching app code)
- When you want to fan out DB changes to multiple consumers (ES, Data Warehouse, Analytics)

**When to Choose Dual-Write**:
- For MVP/prototypes where infrastructure complexity is prohibitive
- For low-volume systems (<1M messages/year) where PostgreSQL FTS is sufficient
- When indexing lag is absolutely unacceptable (though Hybrid pattern can mitigate)
