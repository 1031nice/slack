# ADR-51: Redis Pipeline vs Lua Scripting for Bulk Operations

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-10
- **Context**: v0.5 - Performance Optimization
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 05: Read Status Updates](../deepdives/05-read-status-updates.md)
- **Related ADR**: [ADR-05: Redis ZSET for Unread Counts](./05-redis-zset-unread-counts.md)

---

## TL;DR

**Decision**: Use **Redis Pipelining** for bulk updates (e.g., "Mark as Read" for 1 user) and **Lua Scripting** for atomic complex logic (e.g., "Fan-out to 1000 users").

**Rationale**:
*   **Pipeline**: Best for reducing RTT (Round Trip Time) when commands are independent.
*   **Lua**: Best when commands depend on each other (read-modify-write) or need atomicity.

---

## Context

### The Problem

Updating unread counts involves multiple Redis commands.
1.  **Mark Channel Read**: `ZREMRANGEBYSCORE` (Unread) + `SET` (Last Read).
2.  **Broadcast**: `ZADD` (Unread) for **1,000 users** in a channel.

Doing these sequentially (`for user in users: redis.add(...)`) creates 1,000 network round-trips.

---

## Decision

### 1. Use Pipelining for "Mark as Read"

When a single user reads a channel, we need to update multiple keys. These operations are independent.

```java
// Pipelining
redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    connection.zRemRangeByScore("unread:user:1", 0, timestamp);
    connection.set("read:user:1", timestamp);
    return null;
});
```

### 2. Use Lua for "Message Fan-out"

When a message is sent, we must add it to 1,000 users' unread lists. This is a massive batch.

*   **Why Lua?**
    *   **Atomicity**: Either everyone gets the notification or no one does.
    *   **Server-side execution**: Sends the script once, executes locally on Redis.

```lua
-- Lua Script for Fan-out
for i, userId in ipairs(KEYS) do
    redis.call('ZADD', 'unread:' .. userId, ARGV[1], ARGV[2])
    redis.call('PUBLISH', 'notify:' .. userId, 'NEW_MSG')
end
```

---

## Consequences

- ✅ **Performance**: Massive reduction in network latency (RTT).
- ✅ **Atomicity**: Lua guarantees consistent state updates.
- ❌ **Debugging**: Lua scripts are harder to debug than Java code.