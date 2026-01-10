# ADR-04: Full Payload Strategy for Notifications

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-10
- **Context**: v0.4 - Consistency & Latency Optimization
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 02: Message Visibility & Race Conditions](../deepdives/02-db-redis-race-condition.md)

---

## TL;DR (Executive Summary)

**Decision**: Include the **full message data (payload)** in WebSocket notifications, rather than just a message ID.

**Key Trade-off**: Accept increased WebSocket bandwidth usage in exchange for **guaranteed data consistency** and **reduced client-side latency**.

**Rationale**: By providing all necessary data in the push event, we eliminate the need for clients to fetch data via API, thereby bypassing race conditions caused by database transaction/replication lag.

---

## Context

### The Problem
When a client receives a real-time notification (e.g., "New Message ID: 123"), it often immediately calls the API to fetch the message details. Due to the speed mismatch between Redis (<1ms) and PostgreSQL (5-500ms), this fetch often results in a `404 Not Found` because the DB transaction is not yet committed or replicated.

### Constraints
- **User Experience**: Users should see messages instantly without "failed to load" glitches.
- **Complexity**: Minimize the need for complex server-side transaction hooks or client-side retry logic.
- **Bandwidth**: While bandwidth is a concern, chat messages (text) are relatively small JSON objects.

---

## Decision

### What We Chose
**Full Payload Push Model** (Pattern A in Deep Dive 02).

*   **Mechanism**: The API server maps the saved entity to a complete `MessageResponse` DTO and publishes the JSON string to Redis.
*   **Client Behavior**: The client renders the UI directly from the WebSocket payload and does **not** perform an additional GET request.

### Why This Choice (Trade-off Analysis)

| Criteria | **Full Payload** (Selected) | ID-Only + Fetch |
| :--- | :--- | :--- |
| **Consistency** | ✅ **100% Guaranteed** | ❌ Vulnerable to Race Conditions |
| **User Latency** | ✅ **1-hop** (Instant) | ❌ 2-hops (Wait for API) |
| **DB Load** | ✅ **Reduced** (No Fetch) | 🔴 High (Fetch per message) |
| **Bandwidth** | 🔴 Higher | ✅ Lower |

**Primary Reason**:
We prioritized **Reliability** and **UX Speed**. In a chat application, the "phantom notification" (alerting a message that doesn't exist yet) is a major UX failure. Carrying the payload in the event is the simplest and most robust way to ensure the data is visible at the exact moment of notification.

---

## Consequences

### Positive Impacts
- **Eliminates Race Conditions**: No "Ghost Messages" or 404s during real-time delivery.
- **Improved Responsiveness**: Messages appear in the UI as soon as the WebSocket packet arrives, skipping the 50-100ms API round-trip.
- **Lower DB Read Traffic**: Removes millions of "fetch by ID" queries from the system.

### Negative Impacts & Mitigations
- **Bandwidth Consumption**: Sending full JSON (user info, content, etc.) to all subscribers increases network out-traffic.
    - **Mitigation**: We only send necessary fields for UI rendering. Large attachments (files/images) are still sent as URLs/IDs to be fetched on demand.
- **Security**: Sensitive data must be filtered out before broadcasting.
    - **Mitigation**: Use dedicated `WebSocketMessage` DTOs that only contain public/relevant information.

---

## Implementation Details

```java
// WebSocketMessageService.java
public WebSocketMessage handleIncomingMessage(...) {
    // 1. Save to DB
    MessageResponse savedMessage = messageService.createMessage(...);

    // 2. Map to Full Payload DTO
    WebSocketMessage response = toWebSocketMessage(savedMessage);

    // 3. Broadcast Full Payload
    redisMessagePublisher.publish(response); 

    return response;
}
```

---

## References

- **[Deep Dive 02: Message Visibility & Race Conditions](../deepdives/02-db-redis-race-condition.md)**
- **[Slack Engineering: Real-time Messaging](https://slack.engineering/real-time-messaging/)**
  - "Events are dispatched to connected clients... ensuring the client state is synchronized with the server." (Validates payload-driven sync)