# DB-First Durability Test (Scenario B)

## Hypothesis

**From Deep Dive 01, Section 2.2**: In a DB-first write path architecture, we hypothesize that:

1. Redis failure does NOT cause message loss
2. All messages are persisted to PostgreSQL before publishing to Redis
3. 0% data loss even when Redis is unavailable
4. Messages in DB can be recovered and re-delivered after Redis recovery

## Experimental Setup

### Architecture

```
Client → REST API → PostgreSQL (COMMIT) → Redis Pub/Sub (May Fail)
                         ↓                        ↓
                    Durability                Fan-out
                    Guaranteed               (Best Effort)
```

### Test Flow

```
Phase 1: Send messages 1-50
   ├─ STOMP WebSocket → Backend → DB → Redis → Broadcast
   └─ All systems operational (~2.5 seconds)

Phase 2: Kill Redis at message 50
   ├─ docker stop redis
   └─ Continue sending messages 51-100

Phase 3: Send messages 51-100 (Redis DOWN)
   ├─ STOMP WebSocket → Backend → DB ✅ (still works)
   └─ Redis ❌ (publish fails, but DB commit succeeds)
   └─ (~2.5 seconds)

Phase 4: Verify Database
   └─ SELECT COUNT(*) FROM messages
      → Should be 100 (no loss!)

Phase 5: Restart Redis
   ├─ docker start redis
   └─ Trigger recovery (manual or automatic)

Phase 6: Verify Delivery
   └─ All WebSocket clients should receive all 100 messages

Total Test Duration: ~10-15 seconds
```

### Components

- **2 Backend Instances**: Spring Boot servers running on ports 9000-9001
- **1 PostgreSQL**: Message persistence (durability layer)
- **1 Redis**: Pub/Sub broker (can be killed mid-test)
- **N WebSocket Clients**: Distributed across 2 servers (default: 4 clients)

## Running the Experiment

### Prerequisites

- Docker & Docker Compose installed
- Node.js 18+ installed
- Java 21+ installed

### Quick Start

```bash
cd experiments/db-first-durability-test
./start-experiment.sh
```

This script will:
1. Start infrastructure (PostgreSQL, Redis)
2. Build and start 2 Spring Boot backend instances
3. Run the durability test (`durability-test.js`)
4. Kill Redis at message 500
5. Verify database persistence
6. Restart Redis
7. Generate results in `./logs/`

### Manual Execution

If you prefer to run steps manually:

```bash
# 1. Start infrastructure
docker-compose up -d postgres redis

# 2. Start backends (in separate terminals or use start-experiment.sh)
cd ../../app/backend
SERVER_PORT=9000 ./gradlew bootRun
SERVER_PORT=9001 ./gradlew bootRun

# 3. Install Node dependencies
npm install

# 4. Run the durability test
node durability-test.js
```

### Configuration

Edit `durability-test.js` to adjust test parameters:

```javascript
const CONFIG = {
  totalMessages: 100,        // Total messages to send
  redisKillAt: 50,           // Kill Redis after this many messages
  totalClients: 4,           // Total WebSocket listeners
  messageInterval: 50,       // ms between messages (20 msg/sec)
  recoveryWaitSec: 3         // Wait time after Redis restart
};
```

## Output & Results

### Console Output

The script will output:
- Connection status of WebSocket clients
- Message sending progress
- Redis kill event
- Database verification results
- Redis restart event
- Final delivery statistics

### Log Files

- **`logs/messages.csv`**: Raw message delivery logs (clientId, messageId, sentTime, receiveTime, latency)
- **`logs/events.log`**: Timeline of test events (Redis kill, restart, etc.)
- **`logs/results.json`**: Aggregated test results and statistics

### Expected Output

```
═══════════════════════════════════════════════
           📈 TEST RESULTS SUMMARY
═══════════════════════════════════════════════
Messages Sent:              100
Messages in Database:       100
Unique Messages Received:   TBD
Expected Delivery:          100
Delivery Rate:              TBD%
───────────────────────────────────────────────
Redis killed at message:    50
Redis killed:               YES
Redis restarted:            YES
═══════════════════════════════════════════════
```

## Success Criteria

This experiment validates **Deep Dive 01, Section 2.2** if:

| Criterion                         | Target | Result |
|-----------------------------------|--------|--------|
| **Messages in DB**                | 100    | TBD    |
| **Data Loss**                     | 0%     | TBD    |
| **DB Write Success (Redis DOWN)** | 100%   | TBD    |
| **Recovery Possible**             | YES    | TBD    |

### What This Test Proves

✅ **DB-first prevents data loss**: Even when Redis is completely unavailable, all messages are safely persisted to PostgreSQL.

✅ **Durability over latency**: We accept slightly higher latency (DB commit time) in exchange for guaranteed durability.

✅ **Recovery is possible**: Since all messages are in the DB, we can implement a recovery mechanism to re-broadcast missed messages after Redis restart.

## Known Limitations

### Current Implementation

- **No automatic recovery**: After Redis restart, you must manually trigger re-broadcast from DB
- **Client delivery during outage**: WebSocket clients won't receive messages during Redis outage (expected)
- **Partial delivery**: Messages 1-500 delivered, 501-1000 missed until recovery

### Future Improvements

To implement automatic recovery, you would:

1. **Track last broadcast event ID per server**
   - Store in Redis or separate table: `last_broadcast_message_id`

2. **On Redis reconnect, detect gap**
   ```sql
   SELECT * FROM messages
   WHERE channel_id = 1
     AND message_id > last_broadcast_message_id
   ORDER BY message_id ASC
   ```

3. **Re-publish missed messages**
   - Loop through results and publish to Redis
   - Update `last_broadcast_message_id`

4. **Idempotency**
   - Clients should handle duplicate messages gracefully
   - Use message ID to de-duplicate

## Architectural Decision Records

This experiment validates:

- **ADR-0001**: Use DB-first write path for message durability
  - Context: See Deep Dive 01, § 2.2 (Write Path Decision)
  - Decision: Commit to PostgreSQL before publishing to Redis
  - **This test proves**: Redis failure does NOT cause message loss

## What's Next?

After validating DB-first durability, next challenges include:

- **Automatic recovery mechanism**: Detect and re-broadcast missed messages
- **Race conditions**: What if Redis propagates before DB transaction is visible? (Deep Dive 02)
- **At-least-once vs exactly-once delivery**: Handle duplicate messages gracefully
- **Monitoring & alerting**: Detect when Redis is down and recovery is needed

**→ See Deep Dive 02: Database-Redis Race Conditions**
