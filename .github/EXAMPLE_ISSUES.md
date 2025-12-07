# Example Issues for Reference

This file contains examples of how to use the issue templates. You can copy these to create actual issues on GitHub.

---

## Example 1: ADR - v0.3 Multi-Server Broadcasting

**Title:** `[v0.3] How to broadcast messages across multiple servers?`

**Labels:** `architecture, adr, v0.3, distributed-systems`

```markdown
## Problem Statement
Single-server WebSocket works, but when scaling to 3 servers (9000, 9001, 9002), a message sent to Server A needs to reach clients connected to Server B and C. How do we propagate messages across all servers?

## Context
- **Current implementation:** Single server, all clients on same WebSocket instance
- **Limitation:** User on Server 1 sends message → User on Server 2 doesn't receive it
- **Scale/Performance requirement:** 3 servers, 1000+ concurrent connections
- **Real-world approach (Slack/Discord):** Message broker (Kafka) for inter-server communication

## Constraints
- [x] Learning value (understand pub/sub vs message queues)
- [x] Production-readiness (document limitations clearly)
- [ ] Performance (low latency critical for real-time chat)
- [x] Simplicity (start simple, refactor to complex)
- [x] Cost (use existing Redis, avoid new infrastructure in v0.3)

## Proposed Solutions

### Option 1: Redis Pub/Sub

**Description:**
Use Redis Pub/Sub with single channel `slack:websocket:messages`. All servers subscribe, any server publishes.

**Pros:**
- Already using Redis for sequence numbers
- Very low latency (~1ms)
- Simple Spring integration (RedisMessageListener)
- Good for learning pub/sub pattern limitations

**Cons:**
- Fire-and-forget (no message persistence)
- If subscriber disconnects, messages permanently lost
- No replay capability
- Not production-grade for critical messages

**Complexity:** Low
**Learning Value:** High (learn why this isn't enough before Kafka)
**Production Readiness:** Low (at-most-once delivery)

**Example Implementation:**
```java
// Publisher
redisTemplate.convertAndSend("slack:websocket:messages", webSocketMessage);

// Subscriber
@Override
public void onMessage(Message message, byte[] pattern) {
    WebSocketMessage msg = deserialize(message.getBody());
    messagingTemplate.convertAndSend("/topic/channel." + msg.getChannelId(), msg);
}
```

### Option 2: Apache Kafka

**Description:**
Kafka topic per channel, consumer group for WebSocket servers.

**Pros:**
- Durable message log (configurable retention)
- Can replay messages (offset management)
- Production-grade (what Discord/Slack use)
- Natural fit for v0.7 (Elasticsearch indexing via Kafka Connect)

**Cons:**
- Complex local setup (Zookeeper + Kafka brokers)
- Higher latency than Redis (~10-50ms)
- Overkill for learning at this stage
- Operational overhead (monitoring, tuning)

**Complexity:** High
**Learning Value:** Very High (but better suited for v0.4)
**Production Readiness:** High

### Option 3: Database Polling

**Description:**
Each server polls PostgreSQL for new messages every 100ms.

**Pros:**
- No new infrastructure
- 100% reliable (DB is source of truth)
- Simple to implement

**Cons:**
- High latency (100-500ms)
- Database load (SELECT every 100ms × 3 servers)
- Not how real distributed systems work

**Complexity:** Low
**Learning Value:** Low
**Production Readiness:** Low (doesn't scale)

## Decision

**Chose:** Option 1 - Redis Pub/Sub

**Reasoning:**
1. Learn pub/sub pattern before Kafka complexity
2. Experience message loss first-hand (better understanding than reading docs)
3. Measure Redis Pub/Sub performance baseline for v0.4 comparison
4. v0.3→v0.4 migration path teaches backward compatibility

**Trade-offs Accepted:**
- At-most-once delivery (messages can be lost)
- No message replay (client must query DB if missed)
- Single point of failure (Redis down = no new messages)

**Migration Path:**
v0.4 will replace Redis Pub/Sub with Kafka. Keep sequence numbers in Redis, migrate only pub/sub to Kafka.

## Implementation Plan

### Phase 1: Redis Configuration
- [x] Add RedisMessageListenerContainer bean
- [x] Create ChannelTopic("slack:websocket:messages")
- [x] Configure Jackson ObjectMapper for serialization

### Phase 2: Publisher
- [x] Create RedisMessagePublisher service
- [x] Serialize WebSocketMessage to JSON
- [x] Publish to Redis topic

### Phase 3: Subscriber
- [x] Create RedisMessageSubscriber (implements MessageListener)
- [x] Deserialize JSON to WebSocketMessage
- [x] Broadcast to local WebSocket clients via SimpMessagingTemplate

### Phase 4: Testing & Validation
- [x] Test with 3 servers (9000, 9001, 9002)
- [x] Verify message fan-out (1 send → 3 servers → N clients)
- [x] Nginx sticky sessions (ip_hash)
- [x] Performance test: 5 clients, 2 msg/sec each

### Phase 5: Documentation
- [x] Update README.md with "Critical Limitations" section
- [x] Update CLAUDE.md with Redis Pub/Sub architecture
- [x] Document in PERFORMANCE_BENCHMARKS.md
- [x] Create TIL for sticky sessions learning

## Acceptance Criteria
- [x] Functional requirement: Message sent to any server reaches all clients
- [x] Performance target: P95 latency < 200ms (achieved 190ms)
- [ ] Reliability target: N/A (at-most-once accepted for v0.3)
- [x] Tests passing: Manual testing with 3 servers + 5 clients

## References
- [Spring Data Redis Pub/Sub](https://docs.spring.io/spring-data/redis/reference/redis/pubsub.html)
- [Redis Pub/Sub Limitations](https://redis.io/docs/interact/pubsub/)
- [Why Discord uses Kafka](https://discord.com/blog/how-discord-stores-billions-of-messages)

## Related Issues
- Implementation: ✅ Completed (merged to main)
- Learnings: #[TIL: Sticky sessions required for WebSocket]
- Follow-up: #[v0.4 ADR: At-least-once delivery with Kafka]
```

---

## Example 2: TIL - Redis INCR Race Condition

**Title:** `[TIL] Redis INCR doesn't rollback on PostgreSQL transaction failure`

**Labels:** `learning, til, v0.3, redis, race-condition`

```markdown
## What I Learned
Redis `INCR` is atomic but **not transactional with PostgreSQL**. If you increment a Redis counter and then the DB transaction fails, the Redis increment is already committed - there's no rollback, causing sequence number gaps.

## Context
While implementing message sequence numbers for v0.3 (#[ADR issue]), I discovered a race condition between Redis INCR and PostgreSQL INSERT.

## The Problem/Question
I wanted atomic sequence generation for messages:
1. Generate next sequence number
2. Insert message into PostgreSQL with that sequence
3. If insert fails (constraint violation, etc.), rollback everything

But Redis and PostgreSQL are separate systems - no distributed transaction.

## Investigation

**Experiment 1: Simulate DB failure**
```java
@Test
void testSequenceGapOnDBFailure() {
    Long seq1 = sequenceService.getNextSequenceNumber(1L); // Redis returns 1
    // Simulate DB constraint violation
    assertThrows(DataIntegrityViolationException.class, () -> {
        messageService.createMessage(invalidData); // Fails
    });

    Long seq2 = sequenceService.getNextSequenceNumber(1L); // Redis returns 2
    // Sequence 1 was consumed but no message exists → GAP
}
```

**Experiment 2: Concurrent message creation**
```java
// Thread 1: Gets seq=5, slow DB save (network delay)
CompletableFuture<Message> msg1 = CompletableFuture.supplyAsync(() -> {
    Long seq = sequenceService.getNextSequenceNumber(1L); // seq=5
    Thread.sleep(100); // Simulate slow network
    return messageService.createMessage(content, seq);
});

// Thread 2: Gets seq=6, fast DB save
CompletableFuture<Message> msg2 = CompletableFuture.supplyAsync(() -> {
    Long seq = sequenceService.getNextSequenceNumber(1L); // seq=6
    return messageService.createMessage(content, seq); // Commits first!
});

// DB commits: seq=6 BEFORE seq=5
// Clients receive messages out of order!
```

## Key Insight
**Two separate systems (Redis + PostgreSQL) cannot maintain consistency without distributed transactions (2PC).**

Redis INCR happens in one transaction (Redis), PostgreSQL INSERT in another (PostgreSQL). No coordination between them.

**Visual:**
```
Time →

Redis:    [INCR seq=5] ✅ (committed)

          ↓ Network delay, DB transaction starts

PostgreSQL:              [INSERT seq=5] ❌ (fails)

                         ↓ But Redis already incremented!

Result:   seq=5 consumed, no message → GAP
```

## Why It Matters

**For v0.3:**
- Acceptable limitation (documented in README)
- Real-world learning: understand why distributed transactions are hard

**For v0.4:**
- Kafka solves this naturally via **offsets**
- Offset assigned when message appended to partition log
- Append is atomic: either (message + offset) or nothing
- No race condition possible

**Design Principle:**
> When using multiple data stores, either accept eventual consistency or use a system that provides built-in coordination (like Kafka offsets).

## Code Example / Experiment

**Current (v0.3) - Has race condition:**
```java
// Step 1: Increment Redis (separate transaction)
Long seq = redisTemplate.opsForValue().increment("seq:channel:1");

// Step 2: Insert DB (separate transaction)
@Transactional
public Message createMessage(Long channelId, String content, Long seq) {
    Message msg = new Message(channelId, content, seq);
    return messageRepository.save(msg); // Can fail AFTER seq consumed
}
```

**Future (v0.4) - Atomic with Kafka:**
```java
// Kafka assigns offset atomically when appending to log
ProducerRecord<String, Message> record = new ProducerRecord<>(topic, message);
RecordMetadata metadata = kafkaProducer.send(record).get();

// Offset IS the sequence number, assigned atomically
long offset = metadata.offset();
// If send() fails, no offset consumed
```

## Implications for Project

**v0.3 impact:**
- Document as known limitation in README "Critical Limitations" section
- Add test case demonstrating the race condition
- Acceptable for learning/demo (not production)

**Future consideration:**
- v0.4 migration to Kafka eliminates this entirely
- Alternative: Use PostgreSQL SEQUENCE (but loses Redis performance)
- Or: Accept eventual consistency + deduplication

## Mental Model / Analogy
Think of Redis INCR + DB INSERT like:
1. Taking a ticket number at the DMV (Redis INCR)
2. Walking to the counter to register (DB INSERT)
3. But you trip and can't register (DB fails)
4. Your ticket number is still consumed (gap in sequence)

The ticket machine (Redis) doesn't know you failed at the counter (PostgreSQL).

## References
- [Redis Transactions are not ACID](https://redis.io/docs/interact/transactions/)
- [Two-Phase Commit Problems](https://www.postgresql.org/docs/current/two-phase.html)
- [Kafka Offset Semantics](https://kafka.apache.org/documentation/#semantics)
- [Designing Data-Intensive Applications](https://dataintensive.net/) - Chapter 7: Transactions

## Related Issues
- Discovered while: #[v0.3 ADR]
- Affects design of: #[v0.4 ADR - Kafka migration]
- Follow-up question: Should we implement 2PC? (Answer: No, too complex, use Kafka)

---

## Tags
redis, postgresql, distributed-transactions, race-condition, kafka, sequence-numbers, atomicity
```

---

## Example 3: Troubleshooting - Rancher Desktop Crash

**Title:** `[BUG] stop-all.sh kills Rancher Desktop, Docker engine crashes`

**Labels:** `bug, troubleshooting, infrastructure, v0.3`

```markdown
## Problem Description
When running `./stop-all.sh`, Rancher Desktop crashes and Docker engine becomes unavailable. Next `docker-compose up` fails with:
```
Cannot connect to the Docker daemon at unix:///Users/east/.rd/docker.sock.
Is the docker daemon running?
```

## Error Message / Logs
```
🔪 Killing processes on port 8081 (OAuth2 Server)...
   Killing PID: 43046
   Killing PID: 73002
✅ Port 8081 cleared

# Then Rancher Desktop GUI shows "Docker engine stopped"
```

## Environment
- **Version:** v0.3
- **Components affected:** Infrastructure (Docker, Rancher Desktop)
- **Setup:** Multi-server with Docker Compose
- **OS:** macOS 14.x with Rancher Desktop

## Steps to Reproduce
1. Start services: `./start-multi-server.sh`
2. Services running normally (ports 3000, 8081, 9000-9002)
3. Run `./stop-all.sh`
4. Rancher Desktop crashes
5. Try to restart: `docker-compose up` → fails

**Expected behavior:**
Script stops application processes, leaves Docker engine running

**Actual behavior:**
Script kills Docker/Rancher processes, crashes entire Docker engine

## Initial Hypothesis
The `kill_port` function is too aggressive - it's killing Docker-related processes despite the grep filter.

## Investigation Process

### Step 1: Check what processes are on port 8081
**Action:**
```bash
lsof -ti:8081 | xargs ps -p
```

**Result:**
```
PID   COMMAND
73002 ssh -F /dev/null ... rancher-desktop/lima/0/ssh.sock
43046 /Applications/Rancher Desktop.app/.../limactl hostagent
```

**Learning:**
Port 8081 is used by **Rancher Desktop's SSH tunnel** to the Lima VM, NOT just my application!

### Step 2: Check the grep filter
**Action:**
Reviewed line 45 of `stop-all.sh`:
```bash
if echo "$cmd $full_cmd" | grep -qiE "(docker|containerd|dockerd|com\.docker\.|rancher|vpnkit)"
```

**Result:**
The pattern includes "rancher" but the check happens AFTER getting PIDs from `lsof`. The SSH process command is:
```
ssh -F /dev/null ... rancher-desktop/lima/0/ssh.sock
```

**Learning:**
The grep matches "rancher" in the path, but the check is bypassed OR the pattern doesn't catch SSH processes used by Rancher.

### Step 3: Test improved filter
**Action:**
Added additional patterns:
```bash
grep -qiE "(docker|containerd|dockerd|com\.docker\.|rancher|vpnkit|lima|qemu|ssh.*rancher)"
```

**Result:**
Pattern now catches SSH tunnels used by Rancher/Lima.

**Learning:**
Need to blacklist Lima/QEMU/SSH processes on macOS with Rancher Desktop.

### Step 4: Whitelist approach
**Action:**
Changed from "blacklist bad processes" to "whitelist known app processes":
```bash
if echo "$cmd $full_cmd" | grep -qE "(java|node|npm|gradle)" 2>/dev/null; then
    kill -9 "$pid"
else
    echo "Skipping unknown process"
fi
```

**Result:**
Only kills Java/Node processes (our applications), skips everything else.

**Learning:**
Whitelist is safer than blacklist for critical infrastructure processes.

## Root Cause
Rancher Desktop uses **SSH port forwarding** from macOS host to Lima VM for container port mapping. When `stop-all.sh` ran `kill_port 8081`, it killed the SSH tunnel process, breaking Rancher Desktop's connection to the VM, causing the entire Docker engine to crash.

**Why the filter failed:**
- The grep pattern checked for "rancher" in the command
- But the SSH process path was `/Users/east/Library/Application Support/rancher-desktop/...`
- The pattern matched, but the process was still killed (logic error in script flow)

## Solution

### What Fixed It
```bash
# Added Lima/SSH to skip patterns
if echo "$cmd $full_cmd" | grep -qiE "(docker|containerd|dockerd|com\.docker\.|rancher|vpnkit|lima|qemu|ssh.*rancher|ssh.*lima)" 2>/dev/null; then
    echo "⚠️  Skipping Docker/Rancher-related process (PID: $pid)"
    continue
fi

# Added whitelist check
if echo "$cmd $full_cmd" | grep -qE "(java|node|npm|gradle)" 2>/dev/null; then
    echo "Killing application process PID: $pid ($cmd)"
    kill -9 "$pid" 2>/dev/null || true
else
    echo "⚠️  Skipping unknown process (PID: $pid): $cmd"
fi
```

### Why It Works
1. **Expanded blacklist:** Catches Lima, QEMU, SSH tunnels used by Rancher Desktop
2. **Whitelist safety:** Only kills processes we KNOW are our app (Java/Node)
3. **Defense in depth:** Two checks prevent accidental kills

### How to Verify
```bash
./stop-all.sh

# Should see:
# ⚠️  Skipping Docker/Rancher-related process (PID: 43046)
# ✅ Port 8081 cleared

# Then verify Docker still works:
docker ps
# Should NOT error with "daemon not running"
```

## Prevention
- [x] Add test case: verify script doesn't kill Rancher processes
- [x] Add validation: check Docker daemon still running after script
- [x] Update documentation: note about Rancher Desktop port forwarding
- [ ] Add monitoring/alerting: N/A (local script)
- [x] Fix in multiple places: Also updated `stop-multi-server.sh`

## Lessons Learned
1. **Rancher Desktop is different from Docker Desktop:** Uses Lima VM + SSH tunnels, not native Docker daemon
2. **Port ownership is tricky:** Application port may be held by infrastructure (SSH tunnel)
3. **Whitelist > Blacklist:** For critical infrastructure, safer to only kill known processes
4. **Test destructive scripts carefully:** Should have tested on clean VM first

## Code Changes
- Commit: [link to commit]
- Files changed: `stop-all.sh`, `stop-multi-server.sh`

## Related Issues
- Similar issue: N/A (first occurrence)
- Caused by: Aggressive port cleanup without Rancher Desktop awareness
- Blocks: v0.3 development (couldn't restart services)

## Documentation Updates
- [x] Update README.md "Lessons Learned" section
- [ ] Update troubleshooting guide (TODO: create one)
- [ ] Create TIL issue: #[TIL: Rancher Desktop port forwarding architecture]

---

## Debugging Tips Used
- [x] Checked logs (Rancher Desktop GUI error messages)
- [ ] Used debugger
- [x] Added print statements (echo in bash script)
- [x] Simplified to minimal reproduction (ran lsof manually)
- [x] Checked Docker container status (docker ps after script)
- [ ] Reviewed recent commits
- [x] Searched GitHub issues (Rancher Desktop issues about crashes)
- [x] Read documentation (Rancher Desktop architecture docs)
- [ ] Asked in community / forum
```

---

## How to Use These Templates

1. **Push templates to GitHub:**
   ```bash
   git add .github/
   git commit -m "docs: add GitHub issue templates (ADR, TIL, Troubleshooting)"
   git push
   ```

2. **Create new issue:**
   - Go to GitHub repository → Issues → New Issue
   - You'll see template options (ADR, TIL, Troubleshooting)
   - Click "Get started" on the template you want

3. **Label your issues consistently:**
   - `architecture` + `vX.X` for ADRs
   - `learning` + `til` for learnings
   - `bug` + `troubleshooting` for problems

4. **Link issues together:**
   - ADR mentions implementation issue: `Implementation: #123`
   - TIL references ADR: `Discovered while: #45`
   - This creates a knowledge graph!

5. **Close ADRs when decided:**
   - After implementing, close ADR with comment: "Implemented in v0.4, see #[implementation-issue]"
   - Keep TIL issues open (they're reference material)

---

## Next Steps

Would you like me to:
1. Create actual GitHub issues for v0.3 and v0.4 using these templates?
2. Set up GitHub Project Board configuration?
3. Create a `CONTRIBUTING.md` explaining the issue workflow?
