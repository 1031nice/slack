# Event-Based Architecture Implementation Plan

**Version**: v0.5
**Based on
**: [ADR-0002: Event-Based Architecture](../docs/adr/0006-event-based-architecture-for-distributed-messaging.md)
**Timeline**: 6 weeks (3 phases, 2 weeks each)

---

## Overview

This document outlines the branch and commit strategy for migrating from sequence-based to event-based messaging
architecture.

**Key Principles**:

- **Incremental migration**: Each phase is independently testable and deployable
- **Zero downtime**: Dual systems during transition, no breaking changes
- **Small commits**: Each commit is atomic, reversible, and well-tested
- **Branch per phase**: Isolate risk, enable parallel code review

---

## Branch Strategy

### Total: 3 Feature Branches

```
main
 ├── feature/event-id-generation (Phase 1)
 ├── feature/client-side-ordering (Phase 2)
 └── feature/remove-sequences (Phase 3)
```

**Branching workflow**:

1. Branch from `main` for each phase
2. Develop, test, review in feature branch
3. Merge to `main` after phase completion
4. Next phase branches from updated `main`

**Why not one big branch?**

- Risk isolation: Issues in Phase 2 don't block Phase 1 release
- Incremental value: Ship Phase 1 (IDs), use in production while building Phase 2
- Code review: Smaller PRs (10-15 commits) vs massive PR (40+ commits)
- Rollback safety: Revert Phase 2 without losing Phase 1

---

## Phase 1: Snowflake ID Generation

**Branch**: `feature/event-id-generation`
**Goal**: Introduce `event_id` alongside `sequence_number` (dual system)
**Duration**: 2 weeks
**Commits**: 8 commits

### Commit Breakdown

#### Commit 1: Add SnowflakeIdGenerator service (embedded, not separate microservice)

```
feat: implement Snowflake ID generator for distributed event IDs

IMPORTANT: This is an embedded Spring @Service (NOT a separate microservice).
Generates IDs locally within each backend server with zero network calls.

- Add SnowflakeIdGenerator.java with 64-bit ID generation
- Format: 41-bit timestamp + 10-bit serverId + 12-bit sequence
- Configure serverId via application.yml (e.g., server-id: 1, 2, 3...)
- Local generation eliminates Redis INCR coordination
- Unit tests: ID uniqueness, timestamp extraction, format validation
```

**Architecture Decision**:
- ✅ **Embedded library**: Spring bean injected into MessageService
- ❌ **NOT separate service**: No HTTP/RPC calls, no new microservice
- **Why**: Snowflake's purpose is to eliminate coordination. Separate service defeats this.
- **Difference from auth-platform**: Auth must be centralized (credentials). IDs must be distributed (scalability).

**Files**:

- `src/main/java/com/slack/service/SnowflakeIdGenerator.java` (new)
- `src/test/java/com/slack/service/SnowflakeIdGeneratorTest.java` (new)
- `src/main/resources/application.yml` (add `slack.server-id: 0`)

**Implementation**:
```java
@Service // Spring bean, not microservice!
public class SnowflakeIdGenerator {
    private final long workerId;

    public SnowflakeIdGenerator(@Value("${slack.server-id:0}") long workerId) {
        this.workerId = workerId; // From config, not ZooKeeper
    }

    public String generateEventId() {
        // Generated LOCALLY, no network call
        long timestamp = System.currentTimeMillis() - CUSTOM_EPOCH;
        long sequence = getLocalSequence(); // In-memory counter
        long id = (timestamp << 22) | (workerId << 12) | sequence;
        return "Ev" + Long.toHexString(id).toUpperCase();
    }
}
```

**Tests**: 5 unit tests (uniqueness, collision, timestamp monotonicity, local generation speed <0.1ms)

---

#### Commit 2: Add event_id column to Message entity

```
feat: add event_id column to Message entity with unique constraint

- Add @Column(unique=true, nullable=false) event_id to Message.java
- Update MessageResponse DTO to include event_id
- Flyway migration: ALTER TABLE message ADD COLUMN event_id VARCHAR(20)
```

**Files**:

- `src/main/java/com/slack/domain/message/Message.java`
- `src/main/java/com/slack/dto/message/MessageResponse.java`
- `src/main/resources/db/migration/V010__add_event_id_to_message.sql` (new)

**Migration SQL**:

```sql
ALTER TABLE message ADD COLUMN event_id VARCHAR(20);
CREATE UNIQUE INDEX idx_message_event_id ON message(event_id);
-- Backfill existing messages with generated event IDs
UPDATE message SET event_id = CONCAT('Ev', LPAD(id::TEXT, 16, '0')) WHERE event_id IS NULL;
ALTER TABLE message ALTER COLUMN event_id SET NOT NULL;
```

---

#### Commit 3: Integrate ID generator in MessageService

```
feat: generate event_id when creating messages

- Inject SnowflakeIdGenerator into MessageService
- Call generator.generateEventId() before persisting message
- Set both sequence_number (existing) and event_id (new)
```

**Files**:

- `src/main/java/com/slack/service/MessageService.java`

**Code change**:

```java
public MessageResponse createMessage(Long channelId,MessageCreateRequest request){
        Long sequenceNumber=sequenceService.getNextSequenceNumber(channelId);
        String eventId=snowflakeIdGenerator.generateEventId(); // NEW

        Message message=Message.builder()
        .channel(channel)
        .user(user)
        .content(request.getContent())
        .sequenceNumber(sequenceNumber)
        .eventId(eventId) // NEW
        .build();
        // ...
        }
```

---

#### Commit 4: Include event_id in WebSocket messages

```
feat: add event_id to WebSocketMessage DTO

- Add eventId field to WebSocketMessage.java
- Update toWebSocketMessage() to include event_id
- Frontend receives event_id (not yet used)
```

**Files**:

- `src/main/java/com/slack/dto/websocket/WebSocketMessage.java`
- `src/main/java/com/slack/service/WebSocketMessageService.java`

---

#### Commit 5: Update frontend to receive event_id

```
feat(frontend): add event_id to WebSocketMessage interface

- Update WebSocketMessage type to include event_id: string
- Log received event_id for debugging (not yet used)
```

**Files**:

- `frontend/lib/types/websocket.ts`
- `frontend/lib/websocket.ts`

---

#### Commit 6: Add integration test for dual system

```
test: verify messages have both sequence_number and event_id

- Integration test: Send message, verify DB has both IDs
- Check event_id uniqueness across multiple messages
- Verify WebSocket broadcast includes both fields
```

**Files**:

- `src/test/java/com/slack/integration/MessageDualIdTest.java` (new)

---

#### Commit 7: Add metrics for event_id generation

```
feat: add Prometheus metrics for event_id generation

- Counter: event_ids_generated_total
- Histogram: event_id_generation_duration_ms
- Track collisions (should be 0)
```

**Files**:

- `src/main/java/com/slack/service/SnowflakeIdGenerator.java`

---

#### Commit 8: Update API documentation

```
docs: update OpenAPI spec to include event_id

- Add event_id to MessageResponse schema
- Update example responses in openapi.yaml
```

**Files**:

- `src/main/resources/api/openapi.yaml`

---

### Phase 1 Testing Checklist

- [ ] Unit tests: SnowflakeIdGenerator produces unique IDs
- [ ] Integration tests: Messages saved with both sequence_number and event_id
- [ ] Database migration: Runs cleanly on test DB
- [ ] WebSocket: Clients receive event_id in messages
- [ ] Performance: No measurable latency increase (<1ms overhead)
- [ ] Metrics: Prometheus dashboard shows event_id generation rate

### Phase 1 Merge

**PR Title**: `feat: Add Snowflake event_id generation (Phase 1/3 of event-based migration)`

**PR Description**:

```markdown
## Summary

Introduces `event_id` to all messages while maintaining existing `sequence_number` (dual system).
This is Phase 1 of the event-based architecture migration (see ADR-0002).

## Changes

- Implement SnowflakeIdGenerator (distributed ID generation)
- Add event_id column to Message entity
- Include event_id in WebSocket messages
- Frontend receives event_id (logged, not yet used)

## Testing

- 5 unit tests for ID generation
- Integration test verifying dual ID system
- Database migration tested on staging

## Next Steps

Phase 2: Client-side ordering with event_id (v0.5.1)
```

---

## Phase 2: Client-Side Ordering with Time Buffer

**Branch**: `feature/client-side-ordering`
**Goal**: Client sorts messages by timestamp, uses event_id for deduplication
**Duration**: 2 weeks
**Commits**: 10 commits

### Commit Breakdown

#### Commit 1: Add timestamp to WebSocketMessage

```
feat: add createdAt timestamp to WebSocketMessage

- Already exists as string, convert to ISO-8601 format
- Ensure microsecond precision for sorting
```

**Files**:

- `src/main/java/com/slack/service/WebSocketMessageService.java`

---

#### Commit 2: Create MessageBuffer class (frontend)

```
feat(frontend): implement MessageBuffer for time-based ordering

- Create lib/MessageBuffer.ts
- Buffer messages for 2 seconds before display
- Sort by createdAt timestamp, then event_id (tie-breaker)
```

**Files**:

- `frontend/lib/MessageBuffer.ts` (new)
- `frontend/lib/types/websocket.ts`

**Code**:

```typescript
export class MessageBuffer {
  private buffers: Map<string, WebSocketMessage[]> = new Map();
  private timers: Map<string, NodeJS.Timeout> = new Map();

  add(channelId: string, message: WebSocketMessage) {
    const buffer = this.buffers.get(channelId) || [];
    buffer.push(message);
    this.buffers.set(channelId, buffer);

    // Reset 2-second timer
    this.resetTimer(channelId);
  }

  private resetTimer(channelId: string) {
    const existingTimer = this.timers.get(channelId);
    if (existingTimer) clearTimeout(existingTimer);

    const timer = setTimeout(() => this.flush(channelId), 2000);
    this.timers.set(channelId, timer);
  }

  private flush(channelId: string) {
    const buffer = this.buffers.get(channelId) || [];
    const sorted = buffer.sort((a, b) => {
      if (a.createdAt !== b.createdAt) {
        return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
      }
      return a.eventId.localeCompare(b.eventId);
    });

    // Emit to UI
    this.onFlush(sorted);

    this.buffers.delete(channelId);
    this.timers.delete(channelId);
  }
}
```

---

#### Commit 3: Add event_id deduplication

```
feat(frontend): implement Set-based deduplication for event_id

- Track seen event IDs per channel: Map<channelId, Set<eventId>>
- Ignore duplicate events (at-least-once delivery)
```

**Files**:

- `frontend/lib/MessageBuffer.ts`

---

#### Commit 4: Integrate MessageBuffer in WebSocket client

```
feat(frontend): use MessageBuffer for incoming messages

- Replace direct message display with buffer.add()
- Keep sequence-based logic for reconnection (not yet migrated)
```

**Files**:

- `frontend/lib/websocket.ts`

---

#### Commit 5: Add buffer size metrics

```
feat(frontend): add buffer metrics for monitoring

- Track buffer size per channel
- Log if buffer exceeds 100 messages (potential issue)
```

**Files**:

- `frontend/lib/MessageBuffer.ts`

---

#### Commit 6: Add configuration for buffer delay

```
feat(frontend): make buffer delay configurable

- Default: 2000ms
- Environment variable: NEXT_PUBLIC_MESSAGE_BUFFER_MS
- Allow tuning based on network conditions
```

**Files**:

- `frontend/lib/MessageBuffer.ts`
- `frontend/.env.example`

---

#### Commit 7: Update tests for buffered ordering

```
test(frontend): verify time-based sorting and deduplication

- Test: Messages arrive out of order, displayed in correct order
- Test: Duplicate event_id ignored
- Test: Tie-breaker uses event_id for deterministic order
```

**Files**:

- `frontend/__tests__/MessageBuffer.test.ts` (new)

---

#### Commit 8: Add visual indicator for buffered messages

```
feat(frontend): show "Receiving messages..." during buffer flush

- Display spinner while buffer has pending messages
- Clear after flush completes
```

**Files**:

- `frontend/components/MessageList.tsx`

---

#### Commit 9: Backend support for timestamp queries

```
feat: add getMessagesSince(channelId, timestamp) API

- New endpoint: GET /api/channels/{id}/messages?since={ISO timestamp}
- Returns messages created after timestamp
- Used for reconnection (Phase 3), implemented early for testing
```

**Files**:

- `src/main/java/com/slack/controller/MessageController.java`
- `src/main/java/com/slack/service/MessageService.java`
- `src/main/java/com/slack/repository/MessageRepository.java`

---

#### Commit 10: Integration test for ordering

```
test: verify out-of-order message delivery results in correct display

- Send 3 messages rapidly from different servers
- Introduce artificial delay (network simulation)
- Verify frontend displays in timestamp order
```

**Files**:

- `src/test/java/com/slack/integration/MessageOrderingTest.java` (new)

---

### Phase 2 Testing Checklist

- [ ] Unit tests: MessageBuffer sorts correctly
- [ ] Unit tests: Duplicate event_ids ignored
- [ ] Integration test: Out-of-order messages displayed correctly
- [ ] Manual test: Delay message delivery, verify 2s buffer works
- [ ] Performance: Measure buffer memory usage (target: <10KB per channel)
- [ ] UX test: 2-second delay acceptable for users?

### Phase 2 Merge

**PR Title**: `feat: Client-side time-based ordering (Phase 2/3 of event-based migration)`

**PR Description**:

```markdown
## Summary

Frontend now sorts messages by timestamp using a 2-second buffer.
Deduplication uses event_id (Set-based tracking).

## Changes

- Implement MessageBuffer with time-based sorting
- Add event_id deduplication
- Support configurable buffer delay
- Backend API for timestamp-based queries

## User Impact

- Messages may appear 2 seconds after sending (buffer delay)
- Out-of-order network delivery no longer causes incorrect display

## Testing

- Unit tests for buffer logic
- Integration test for out-of-order delivery
- Manual testing with network delay simulation

## Next Steps

Phase 3: Remove sequence_number dependency (v0.5.2)
```

---

## Phase 3: Remove Sequence Numbers

**Branch**: `feature/remove-sequences`
**Goal**: Eliminate Redis INCR, migrate reconnection to timestamp-based
**Duration**: 2 weeks
**Commits**: 12 commits

### Commit Breakdown

#### Commit 1: Add heartbeat-based gap detection

```
feat(frontend): implement heartbeat for gap detection

- Emit heartbeat every 5 seconds per channel
- If no message in 10 seconds, request resync
```

**Files**:

- `frontend/lib/HeartbeatMonitor.ts` (new)

---

#### Commit 2: Update reconnection to use timestamps

```
feat(frontend): replace sequence-based reconnection with timestamp

- Track lastMessageTimestamp per channel (instead of lastSequenceNumber)
- On reconnect: GET /api/channels/{id}/messages?since={timestamp - 5s}
- 5-second overlap for safety (network clock skew)
```

**Files**:

- `frontend/lib/websocket.ts`

---

#### Commit 3: Add resync WebSocket endpoint

```
feat: add /app/message.resync endpoint for timestamp-based sync

- Client sends: {channelId, sinceTimestamp}
- Server responds with missed messages via /queue/resend
```

**Files**:

- `src/main/java/com/slack/controller/WebSocketMessageController.java`
- `src/main/java/com/slack/service/WebSocketMessageService.java`

---

#### Commit 4: Deprecate MessageSequenceService

```
refactor: mark MessageSequenceService as @Deprecated

- Add @Deprecated annotation
- Log warning when sequence generation is called
- Prepare for removal in next commit
```

**Files**:

- `src/main/java/com/slack/service/MessageSequenceService.java`

---

#### Commit 5: Remove sequence_number from MessageService

```
refactor: remove sequence number generation from message creation

- Delete sequenceService.getNextSequenceNumber() call
- Remove sequence_number field from MessageCreateRequest
- Update tests to not expect sequence_number
```

**Files**:

- `src/main/java/com/slack/service/MessageService.java`
- `src/main/java/com/slack/dto/message/MessageCreateRequest.java`

---

#### Commit 6: Delete MessageSequenceService

```
refactor: delete MessageSequenceService (no longer needed)

- Remove service class
- Remove from dependency injection
- Remove tests
```

**Files**:

- `src/main/java/com/slack/service/MessageSequenceService.java` (delete)
- `src/test/java/com/slack/service/MessageSequenceServiceTest.java` (delete)

---

#### Commit 7: Remove sequence_number from WebSocketMessage

```
refactor: remove sequence_number from WebSocketMessage DTO

- Remove sequenceNumber field
- Update all references
```

**Files**:

- `src/main/java/com/slack/dto/websocket/WebSocketMessage.java`
- `src/main/java/com/slack/service/WebSocketMessageService.java`

---

#### Commit 8: Database migration to drop sequence_number

```
refactor: drop sequence_number column from message table

- Flyway migration: ALTER TABLE message DROP COLUMN sequence_number
- Drop index on sequence_number
```

**Files**:

- `src/main/resources/db/migration/V011__drop_sequence_number.sql` (new)

**Migration SQL**:

```sql
ALTER TABLE message DROP COLUMN sequence_number;
DROP INDEX IF EXISTS idx_message_channel_sequence;
```

---

#### Commit 9: Remove ACK handling

```
refactor: remove ACK message handling (no longer needed)

- Delete handleAck() method
- Remove ACK message type
- Update WebSocketMessageController
```

**Files**:

- `src/main/java/com/slack/service/WebSocketMessageService.java`
- `src/main/java/com/slack/dto/websocket/WebSocketMessage.java`

**Rationale**: ACK was designed for sequence-based ordering. Event-based uses deduplication instead.

---

#### Commit 10: Update frontend to remove sequence tracking

```
refactor(frontend): remove sequence number tracking

- Delete lastSequenceNumber state
- Remove ACK sending logic
- Simplify reconnection to timestamp-based only
```

**Files**:

- `frontend/lib/websocket.ts`

---

#### Commit 11: Integration test for timestamp-based reconnection

```
test: verify reconnection fetches missed messages by timestamp

- Disconnect client for 10 seconds
- Send 5 messages during disconnect
- Reconnect client
- Verify all 5 messages received via resync
```

**Files**:

- `src/test/java/com/slack/integration/ReconnectionTest.java`

---

#### Commit 12: Update documentation

```
docs: update architecture docs to reflect event-based system

- Remove references to sequence numbers
- Update diagrams with event_id flow
- Add troubleshooting for clock skew issues
```

**Files**:

- `README.md`
- `docs/adr/0002-event-based-architecture-for-distributed-messaging.md`

---

### Phase 3 Testing Checklist

- [ ] Unit tests: Heartbeat detects gaps correctly
- [ ] Integration test: Timestamp-based reconnection works
- [ ] Database migration: Clean migration on staging
- [ ] Performance: Redis dependency eliminated (measure write latency)
- [ ] Reliability: Client reconnects successfully after 30-second disconnect
- [ ] Clock skew: Test with clients in different timezones

### Phase 3 Merge

**PR Title**: `feat: Complete event-based migration (Phase 3/3)`

**PR Description**:

```markdown
## Summary

Removes all sequence number dependencies. System now fully event-based.

## Changes

- Replace sequence-based reconnection with timestamp-based
- Add heartbeat gap detection
- Remove MessageSequenceService (eliminate Redis INCR)
- Drop sequence_number column from database
- Remove ACK handling

## Breaking Changes

- Clients must upgrade to timestamp-based protocol
- Old clients (using sequences) will not work

## Performance Impact

- ✅ Eliminated Redis INCR bottleneck
- ✅ Write latency reduced by ~1ms (no Redis coordination)
- ❌ Client memory +10KB per channel (event ID tracking)

## Rollback Plan

If critical issues:

1. Revert this PR
2. Restore sequence_number column (backfill from event_id)
3. Re-enable MessageSequenceService

## Documentation

See ADR-0002 for full architectural rationale.

## Next Steps

v0.6: Implement threads using event_id (no sequence dependency)
```

---

## Summary

### Total Implementation

| Phase       | Branch                         | Commits        | Duration    | Focus                          |
|-------------|--------------------------------|----------------|-------------|--------------------------------|
| **Phase 1** | `feature/event-id-generation`  | 8              | 2 weeks     | Add event_id (dual system)     |
| **Phase 2** | `feature/client-side-ordering` | 10             | 2 weeks     | Client sorting + deduplication |
| **Phase 3** | `feature/remove-sequences`     | 12             | 2 weeks     | Remove sequence numbers        |
| **Total**   | 3 branches                     | **30 commits** | **6 weeks** | Full migration                 |

### Risk Mitigation

**Phase 1**: Lowest risk (additive only, no breaking changes)
**Phase 2**: Medium risk (client behavior change, but sequences still work)
**Phase 3**: Highest risk (breaking changes, requires client upgrade)

**Rollback strategy**:

- Phase 1: Simply stop generating event_id (no harm)
- Phase 2: Disable MessageBuffer, revert to sequence-based display
- Phase 3: Restore sequence_number column, re-enable MessageSequenceService

### Success Metrics

**Before migration** (v0.4):

- Message write latency: P50=10ms, P95=25ms (includes Redis INCR)
- Redis dependency: 100% (no sequences = no messages)

**After migration** (v0.5):

- Message write latency: P50=9ms, P95=22ms (local ID generation)
- Redis dependency: 0% for writes (Pub/Sub only)
- Client memory: +10KB per active channel (acceptable)

### Coordination

**Team responsibilities**:

- **Backend**: Phase 1, Phase 3 commits 3-9
- **Frontend**: Phase 2, Phase 3 commits 1-2, 10
- **DevOps**: Database migrations, Prometheus dashboard updates
- **QA**: Integration tests, manual testing, load testing

**Communication**:

- Daily standup: Progress updates per phase
- Code review: 2 reviewers per PR (backend + frontend)
- Staging deployment: After each phase, before merging to main

---

## Conclusion

This plan delivers event-based architecture incrementally over 6 weeks with 3 feature branches and 30 atomic commits.
Each phase is independently valuable and can be rolled back if needed.

By following this plan, we gain horizontal scalability and eliminate single points of failure while maintaining system
stability through careful migration.
