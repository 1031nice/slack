# 📄 Topic 08: Real-time Message Search (Full-Text Search)

## 1. Problem Statement
Chat messages must be searchable immediately after sending ("Near Real-time").
*   **RDB Limitations**: `LIKE %query%` is too slow for billions of rows.
*   **Indexing Lag**: ElasticSearch/OpenSearch typically has a 1s+ refresh interval. Users expect to find the message they *just* sent.

## 2. Key Questions to Solve
*   **Dual-Read**: Do we query DB for "recent" (last 1 min) and ES for "history"?
*   **CDC (Change Data Capture)**: Should we index via Debezium (DB log) or Dual-Write from API?
*   **Reindexing**: How to handle reindexing without downtime when mapping changes?

## 3. Direction
*   **ElasticSearch** with **Kafka Connect** (or application dual-write).
