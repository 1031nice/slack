# 📄 Topic 08: Real-time Message Search (Full-Text Search)

> **Prerequisites**: This document addresses the challenge of providing instant, accurate search across billions of chat messages while maintaining sub-second query latency.

## 1. Problem Statement

### 1.1 The Scale Challenge
Chat applications generate massive volumes of searchable text:
*   **Volume**: A workspace with 10,000 users sending 100 messages/day = **1M messages/day** = **365M messages/year**.
*   **Query Patterns**: Users expect to search across all channels, all time periods, with complex filters (sender, date range, has:link, etc.).
*   **Latency Budget**: Search results must appear in **\<500ms** to feel "instant".

### 1.2 The Real-time Paradox
Traditional search engines (ElasticSearch, OpenSearch) have an **indexing lag** (refresh interval):
*   **Default Refresh**: 1 second. A message sent at `10:00:00.000` becomes searchable at `10:00:01.000`.
*   **User Expectation**: "I just sent a message with the word 'budget'. Why can't I find it?"
*   **UX Failure**: Users perceive the system as "broken" when their own messages don't appear in search immediately.

### 1.3 The Database Limitation
PostgreSQL `LIKE %query%` or even Full-Text Search (`tsvector`) cannot handle this scale:
*   **Sequential Scan**: `LIKE` queries on billions of rows require full table scans (minutes, not milliseconds).
*   **Index Bloat**: `GIN` indexes for `tsvector` become massive and slow to update at high write rates.
*   **No Relevance Ranking**: SQL lacks sophisticated scoring algorithms (TF-IDF, BM25).

**Goal**: Design a search architecture that provides **instant indexing** (\<1s lag), **sub-second queries**, and **relevance-ranked results** for billions of messages.

## 2. Solution Strategy Exploration

We analyze four architectural patterns for full-text search.

### Pattern A: PostgreSQL Full-Text Search (tsvector)
Use PostgreSQL's built-in `tsvector` and `GIN` indexes.

*   **Mechanism**: 
    *   Add a `search_vector tsvector` column to `messages` table.
    *   Create a `GIN` index: `CREATE INDEX idx_messages_fts ON messages USING GIN(search_vector);`.
    *   Query: `SELECT * FROM messages WHERE search_vector @@ to_tsquery('budget');`.
*   **Pros**: 
    *   No additional infrastructure (uses existing DB).
    *   Zero indexing lag (synchronous).
    *   ACID guarantees.
*   **Cons**: 
    *   **Performance Ceiling**: GIN indexes degrade at \>100M rows.
    *   **Write Amplification**: Every message insert must also update the FTS index.
    *   **Limited Features**: No fuzzy matching, no advanced relevance tuning.

### Pattern B: ElasticSearch with Dual-Write (Application Layer)
Application writes to both PostgreSQL (source of truth) and ElasticSearch (search index) simultaneously.

#### Variant B1: Synchronous Dual-Write
*   **Mechanism**: 
    ```java
    messageRepository.save(message);       // PostgreSQL (10ms)
    elasticSearchClient.index(message);    // ElasticSearch (50ms)
    return "Message sent";                 // User waits 60ms
    ```
*   **Pros**: 
    *   Simple to implement.
    *   Zero indexing lag (immediate searchability).
*   **Cons**: 
    *   **Latency Coupling**: User response time depends on ES performance. If ES is slow (500ms) or down, users wait.
    *   **Consistency Risk**: If ES write fails, data is missing from search (permanent loss without retry logic).

#### Variant B2: Asynchronous Dual-Write (Background Thread)
*   **Mechanism**: 
    ```java
    messageRepository.save(message);       // PostgreSQL (10ms)
    executorService.submit(() -> {
        elasticSearchClient.index(message); // Background thread
    });
    return "Message sent";                 // User waits 10ms
    ```
*   **Pros**: 
    *   Fast user response (decoupled from ES performance).
    *   Can implement retry logic in background thread.
*   **Cons**: 
    *   **Volatility**: Thread pool queue is in-memory. Server crash → queue lost → data missing from search.
    *   **No Recovery**: After restart, no way to know which messages failed to index (requires manual DB scan).
    *   **Ordering Issues**: Multiple threads may index messages out of order.
    *   **Backpressure**: If ES is slow, queue grows unbounded → OutOfMemoryError.
    *   **No Replayability**: Cannot rebuild index from a specific point in time.

### Pattern C: ElasticSearch with CDC (Change Data Capture)
Use a CDC tool (Debezium, Maxwell) to stream database changes to ElasticSearch.

*   **Mechanism**: 
    ```
    PostgreSQL WAL → Debezium → Kafka → Kafka Connect (ES Sink) → ElasticSearch
    ```
*   **Pros**: 
    *   **Durability**: Every step is file-based (disk), not memory-based. DB COMMIT → WAL file (fsync) → Kafka log (replicated). Server crashes do not cause data loss.
    *   **Decoupled**: Application code is unaware of search infrastructure. API response time is independent of ES performance.
    *   **Replayable**: Kafka log acts as a durable buffer. Can rebuild ES index from scratch by replaying Kafka topic or DB WAL.
    *   **Single Source of Truth**: PostgreSQL is the only write target. No dual-write consistency issues.
    *   **Ordering Guarantee**: Kafka partitions ensure messages from the same channel are processed in order.
    *   **Backpressure Handling**: Kafka is disk-based. If ES is slow, events accumulate in Kafka (not memory), preventing OOM.
    *   **Fan-out**: Same CDC stream can feed multiple consumers (ES, Data Warehouse, Analytics) without additional DB load.
*   **Cons**: 
    *   **Operational Complexity**: Requires Kafka + Debezium + Kafka Connect infrastructure.
    *   **Indexing Lag**: Typically 1-3 seconds (WAL → Kafka → ES). Not truly "real-time" for user's own messages.
    *   **Schema Evolution**: Mapping changes require careful coordination (reindexing strategy needed).

#### Why CDC is Durable (vs Background Thread)
**Key Difference: File-Based vs Memory-Based**

```
Dual-Write (Background Thread):
  DB → Memory Queue → ES
  ↑ Server crash → Queue lost

CDC (Kafka):
  DB → WAL File → Debezium → Kafka File → ES
  ↑ Server crash → Files remain, auto-recovery
```

**Atomicity Guarantee:**
1. PostgreSQL COMMIT writes to WAL file (fsync to disk).
2. Debezium reads WAL file (not DB memory).
3. If Debezium crashes, it resumes from last read position (LSN stored in Kafka).
4. If Kafka crashes, Debezium retries until success.
5. If ES crashes, Kafka Consumer retries indefinitely (offset not committed until success).

**Result:** Once DB COMMIT succeeds, the change event is guaranteed to eventually reach ES (at-least-once delivery).

### Pattern D: Hybrid (Recent + Historical)
Query PostgreSQL for "recent" messages (last 5 minutes) and ElasticSearch for "historical" messages.

*   **Mechanism**: 
    ```javascript
    const recentResults = await db.query("SELECT * FROM messages WHERE created_at > NOW() - INTERVAL '5 minutes' AND content ILIKE '%query%'");
    const historicalResults = await es.search({ query: { match: { content: query } } });
    return merge(recentResults, historicalResults);
    ```
*   **Pros**: 
    *   **Zero Perceived Lag**: User's own messages are always searchable (from DB).
    *   **Best of Both Worlds**: DB for freshness, ES for scale.
*   **Cons**: 
    *   **Complexity**: Dual query logic, result merging, deduplication.
    *   **Edge Cases**: What if a message is in both DB and ES? How to rank?

## 3. Comparative Analysis

| Feature | Pattern A (PostgreSQL) | Pattern B1 (Dual-Write Sync) | Pattern B2 (Dual-Write Async) | Pattern C (CDC) | Pattern D (Hybrid) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **User Latency** | 🟡 Medium (DB query) | 🔴 High (DB + ES) | 🟢 Low (DB only) | 🟢 **Low (DB only)** | 🟢 Low |
| **Indexing Lag** | 🟢 0ms | 🟢 0ms | 🟢 <100ms | 🟡 1-3s | 🟢 0ms (Perceived) |
| **Durability** | 🟢 ACID | 🔴 At-Risk | 🔴 **Volatile (Memory)** | 🟢 **Guaranteed (Disk)** | 🟢 Strong (Recent) |
| **Query Speed** | 🔴 Slow (>100M rows) | 🟢 Fast | 🟢 Fast | 🟢 Fast | 🟢 Fast |
| **Crash Recovery** | 🟢 N/A | 🔴 Manual | 🔴 **Manual (DB scan)** | 🟢 **Auto (WAL/Kafka)** | 🟡 Partial |
| **Ordering** | 🟢 Guaranteed | 🟢 Guaranteed | 🔴 **At-Risk (Threads)** | 🟢 **Guaranteed (Partition)** | 🟢 Guaranteed |
| **Backpressure** | 🟢 N/A | 🔴 Blocks User | 🔴 **OOM Risk** | 🟢 **Disk Buffer** | 🟡 Medium |
| **Scalability** | 🔴 Limited | 🟢 High | 🟢 High | 🟢 **Very High** | 🟢 High |
| **Complexity** | 🟢 Low | 🟢 Low | 🟡 Medium | 🔴 High | 🔴 High |
| **Slack's Choice** | ❌ (Early Days) | ❌ | ❌ | ✅ **(Current)** | ⚠️ (Fallback) |

## 4. Proposed Architecture: CDC with ElasticSearch

We adopt **Pattern C (CDC)** as the primary strategy, with **Pattern D (Hybrid)** as a fallback for critical UX scenarios.

### 4.1 Primary Flow (CDC)
```
1. User sends message
2. API saves to PostgreSQL (COMMIT)
3. PostgreSQL WAL emits change event
4. Debezium captures event → Kafka topic: "db.messages"
5. Kafka Connect (ElasticSearch Sink) consumes → ES index
6. User searches → Query ElasticSearch
```

### 4.2 Fallback Flow (Hybrid - Optional)
For the "I just sent this" use case:
```
1. User searches within 5 minutes of sending
2. Backend queries BOTH:
   - PostgreSQL: `WHERE user_id = :currentUser AND created_at > NOW() - INTERVAL '5 min'`
   - ElasticSearch: Standard query
3. Merge results (DB results ranked higher)
```

### 4.3 Why This Choice

#### Why Not PostgreSQL Full-Text Search?
- **Scalability Ceiling**: GIN indexes degrade significantly beyond 100M rows.
- **Write Performance**: Every message insert triggers FTS index update, slowing down the critical write path.
- **Feature Limitations**: No fuzzy matching, no ML-based ranking, no multi-language analyzers.

#### Why Not Dual-Write (Synchronous)?
- **Latency Coupling**: User waits for both DB and ES. If ES is slow (500ms) or down, chat becomes unusable.
- **Availability Risk**: ES maintenance or failures directly impact core messaging functionality.

#### Why Not Dual-Write (Asynchronous with Background Thread)?
This is the most tempting alternative, but has critical flaws:

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

#### Why CDC + Kafka Solves These Problems

1. **Durability**: 
   - DB COMMIT → WAL file (disk, fsync).
   - Debezium reads from WAL file (survives crashes).
   - Kafka stores events on disk with replication.
   - **Result**: Zero data loss even with server crashes.

2. **Ordering**:
   - Kafka partitions by channel_id.
   - All messages from the same channel go to the same partition.
   - Single consumer per partition → guaranteed order.

3. **Backpressure**:
   - Kafka is disk-based (not memory).
   - Can buffer millions of events without OOM.
   - Consumer processes at its own pace.

4. **Replayability**:
   - Kafka retains logs (configurable retention).
   - Can reset consumer offset to any point in time.
   - Rebuild ES index by replaying Kafka topic.

5. **Decoupling**:
   - Application code only writes to DB.
   - ES can be upgraded, replaced, or taken offline without touching application code.
   - Same CDC stream feeds multiple systems (ES, Data Warehouse, Analytics).

#### Trade-off: Complexity vs Reliability
- **Cost**: Requires Kafka + Debezium + Kafka Connect infrastructure.
- **Benefit**: Production-grade reliability, scalability, and operational flexibility.
- **When to Choose**: For systems where search is critical and data loss is unacceptable (Slack, Discord, LinkedIn scale).

## 5. Implementation Considerations

### 5.1 ElasticSearch Index Design
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

### 5.2 Query Examples
**Basic Search:**
```json
GET /messages/_search
{
  "query": {
    "match": { "content": "budget proposal" }
  }
}
```

**Advanced Filters:**
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

### 5.3 Reindexing Strategy
When mapping changes (e.g., adding a new field):
1.  Create a new index with updated mapping: `messages_v2`.
2.  Use Kafka Connect to dual-write to both `messages_v1` and `messages_v2`.
3.  Backfill `messages_v2` from Kafka topic (replay) or DB snapshot.
4.  Switch application to query `messages_v2`.
5.  Delete `messages_v1`.

**Zero-Downtime Alias Pattern:**
```
messages (alias) → messages_v1 (index)
                 → messages_v2 (index, during migration)
```

## 6. Performance Expectations

Based on industry benchmarks (ElasticSearch 8.x, 3-node cluster):

| Metric | Target | Notes |
| :--- | :--- | :--- |
| **Indexing Throughput** | 10k-50k docs/sec | Depends on document size and cluster size |
| **Query Latency (P95)** | \<200ms | For typical keyword searches |
| **Index Size** | ~1.5x raw data | With standard compression |
| **Indexing Lag** | 1-3 seconds | CDC pipeline latency |

## 7. Risks \& Mitigations

### Risk 1: ElasticSearch Cluster Failure
*   **Impact**: Search is completely unavailable.
*   **Mitigation**: 
    *   Replicas (2+ copies of each shard).
    *   Fallback to PostgreSQL `ILIKE` for critical queries (degraded mode).

### Risk 2: Indexing Lag Complaints
*   **Impact**: Users complain "I can't find my message".
*   **Mitigation**: 
    *   Implement Hybrid pattern (DB + ES) for user's own messages.
    *   UI feedback: "Indexing... (results may be incomplete)".

### Risk 3: Mapping Explosion
*   **Impact**: Too many fields → slow queries, high memory usage.
*   **Mitigation**: 
    *   Limit indexed fields (don't index everything).
    *   Use `dynamic: false` to prevent auto-field creation.

## 8. Verdict \& Roadmap

*   **Decision**: Use **ElasticSearch with CDC (Debezium + Kafka)** for production search.
*   **MVP Fallback**: PostgreSQL `tsvector` for initial launch (if ES infrastructure is not ready).
*   **Next Steps**:
    1.  Set up Debezium connector for `messages` table.
    2.  Configure Kafka Connect ElasticSearch Sink.
    3.  Design index mappings and query API.
    4.  Benchmark query performance with 100M+ documents.

## 9. Related Topics

*   **Event-Driven Architecture**: CDC as a foundation for decoupled systems.
    *   **→ See ADR-04**
*   **Message Ordering**: Ensuring search results respect message chronology.
    *   **→ See Deep Dive 05**

## 10. Architectural Decision Records

*   **[ADR-08: ElasticSearch with CDC for Full-Text Search](../adr/08-elasticsearch-cdc-search.md)** ✅
