# ADR-02: Full Payload Strategy for Consistency

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-10
- **Context**: v0.4 - Distributed Messaging Refinement
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 02: Message Visibility & Race Conditions](../deepdives/02-db-redis-race-condition.md)
- **Related ADR**: [ADR-01: Redis Pub/Sub](./01-redis-pubsub-broadcasting.md)

---

## TL;DR (Executive Summary)

**Decision**: Include the **full message data (payload)** in WebSocket notifications, rather than just a message ID.

**Key Trade-off**: Slightly higher bandwidth usage per message in exchange for **eliminating race conditions** and reducing database load.

**Rationale**: When relying on "ID-only" notifications (e.g., "New Message 123"), the client must immediately fetch the data from the DB. In a distributed system, this fetch often fails (404) if the DB transaction hasn't fully propagated (Replication Lag) or if Redis is faster than the DB commit. Sending the full payload bypasses this "Fetch" step entirely for real-time delivery.

---

## Context

### The Problem

When a client receives a real-time notification (e.g., "New Message ID: 123"), it often immediately calls the API to fetch the message details. Due to the speed mismatch between Redis (<1ms) and PostgreSQL (5-500ms), this fetch often results in a `404 Not Found` because the DB transaction is not yet committed or replicated.

### Constraints

- **User Experience**: Users should see messages instantly without "failed to load" glitches.
- **Consistency**: The notification must match the data in the database.
- **Bandwidth**: While bandwidth is a concern, chat messages (text) are relatively small JSON objects.

---

## Decision

### What We Chose

**Full Payload Notification Pattern**.

### Architecture Flow

```
1. Server saves Message to DB (in transaction).
2. Server maps Message entity to JSON DTO (e.g., {id: 1, content: "hello", sender: "alice"}).
3. Server publishes JSON to Redis.
4. Client receives JSON and renders it immediately.
   (No API call required)
```

### Why This Choice (Trade-off Analysis)

| Criteria | **Full Payload** (Selected) | ID-Only Notification (Rejected) |
| :--- | :--- | :--- |
| **Race Condition** | ✅ **Eliminated** (Data is in event) | 🔴 High Risk (Fetch may 404) |
| **Latency** | 🚀 **1-hop** (Push -> Render) | 🐢 2-hop (Push -> Fetch -> Render) |
| **Bandwidth** | 🟡 Medium (Payload size) | 🟢 Low (ID only) |
| **DB Load** | ✅ **Reduced** (No Fetch) | 🔴 High (Fetch per message) |

**Primary Reason**:
We prioritized **Reliability** and **UX Speed**. In a chat application, the "phantom notification" (alerting a message that doesn't exist yet) is a major UX failure. Carrying the payload in the event is the simplest and most robust way to ensure the data is visible at the exact moment of notification.

---

## Consequences

### Positive Impacts

- **Eliminates Race Conditions**: No "Ghost Messages" or 404s during real-time delivery.
- **Improved Responsiveness**: Messages appear in the UI as soon as the WebSocket packet arrives, skipping the 50-100ms API round-trip.
- **Reduced API Load**: Eliminates the "Thundering Herd" of `GET /messages/{id}` calls after every broadcast.

### Negative Impacts & Mitigations

- **Risk: Bandwidth Usage**
  - Large messages could clog the Redis pipe.
  - **Mitigation**: Use dedicated `WebSocketMessage` DTOs that only contain public/relevant information. Large attachments are sent as URL references, not base64 blobs.

### Implementation Details

```java
// WebSocketMessageService.java
public WebSocketMessage handleIncomingMessage(...) {
    // 1. Save to DB (Transactional)
    MessageResponse savedMessage = messageService.createMessage(...);

    // 2. Convert to DTO (Full Payload)
    WebSocketMessage response = toWebSocketMessage(savedMessage);

    // 3. Publish to Redis (No Race Condition)
    redisMessagePublisher.publish(response);
}
```

---

## References

- **[Deep Dive 02: Message Visibility & Race Conditions](../deepdives/02-db-redis-race-condition.md)**
  - Detailed analysis of the "Push vs Pull" consistency models.
