# ADR-0004: Redis Pipeline vs Lua Script for Batch Operations

## Status

**Accepted** - 2024-12-14

## Context

When performing multiple Redis operations in a single logical unit, there are two main approaches to reduce network round trips and improve performance:

1. **Pipeline**: Bundle multiple commands and send them together
2. **Lua Script**: Execute server-side script with all logic

This decision affects performance, complexity, and system behavior across multiple features:
- Unread count tracking (incrementing for N channel members)
- Notification batch delivery
- Rate limiting (multi-user throttling)
- Leaderboard updates
- Any multi-key operations

### Performance Requirements

For a Slack-scale application:
- 100K+ messages/second during peak
- Channels with 1000+ members
- Need to minimize network latency

### The Atomicity Question

The critical decision factor: **Does the entire operation need to be atomic?**

---

## Decision

**Use Pipeline by default. Use Lua Script only when atomicity is required.**

### Decision Tree

```
Does the operation require atomicity?
│
├─ NO  → Use Pipeline
│        ✅ Simpler
│        ✅ Easier to maintain
│        ✅ Easier to test
│        ✅ Good enough performance
│
└─ YES → Use Lua Script
         ✅ Atomic execution
         ✅ Server-side logic
         ✅ Slightly better performance
         ❌ More complex
```

### When to Use Pipeline

✅ **Independent operations**
```java
// Example: Add unread message for multiple users
// Each user's unread count is independent
// If one fails, others should still succeed
for each member:
    ZADD unread:{userId}:{channelId} {timestamp} {messageId}
```

✅ **Partial failure is acceptable**
- Broadcasting messages to N users
- Bulk data loading
- Cache warming

✅ **Simple operations without conditionals**
- Just need to reduce network round trips
- No complex logic or calculations

### When to Use Lua Script

✅ **Atomicity required**
```lua
-- Example: Increment counter and trigger notification at threshold
local count = redis.call('INCR', 'unread:' .. userId)
if count == 100 then
    redis.call('PUBLISH', 'notifications:' .. userId, 'You have 100 unread messages')
end
return count
```

✅ **Read-Modify-Write patterns**
```lua
-- Example: Check-then-set with conditions
local current = redis.call('GET', 'rate_limit:' .. userId)
if tonumber(current) < 100 then
    redis.call('INCR', 'rate_limit:' .. userId)
    return true
else
    return false
end
```

✅ **Complex server-side logic**
```lua
-- Example: Conditional updates with multiple keys
local score = redis.call('ZINCRBY', 'leaderboard', 10, userId)
if score > 1000 then
    redis.call('SADD', 'premium_users', userId)
    redis.call('PUBLISH', 'achievements', userId .. ':1000_points')
end
```

---

## Implementation Examples

### Pipeline (Current: UnreadCountService)

```java
public void incrementUnreadCount(Long channelId, Long messageId, Long senderId, long timestamp) {
    List<Long> memberIds = channelMemberRepository.findUserIdsByChannelId(channelId);

    if (memberIds == null || memberIds.isEmpty()) {
        return;
    }

    String messageIdStr = messageId.toString();

    // Pipeline: 1 network round trip for N operations
    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
        memberIds.stream()
                .filter(memberId -> !memberId.equals(senderId))
                .forEach(memberId -> {
                    String key = buildKey(memberId, channelId);
                    redisTemplate.opsForZSet().add(key, messageIdStr, timestamp);
                });
        return null;
    });
}
```

**Why Pipeline here:**
- Each member's unread count is independent
- Partial failure is acceptable (some users get notification, others don't)
- No conditional logic needed
- Simpler to maintain

### Lua Script (Hypothetical: Rate Limiting with Notification)

```java
public boolean incrementWithThreshold(Long userId, Long channelId) {
    String script =
        "local count = redis.call('ZCARD', KEYS[1]) " +
        "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2]) " +
        "if count + 1 == 100 then " +
        "    redis.call('PUBLISH', 'notifications:' .. ARGV[3], 'threshold_reached') " +
        "end " +
        "return count + 1";

    String key = "unread:" + userId + ":" + channelId;

    return redisTemplate.execute(
        new DefaultRedisScript<>(script, Long.class),
        Collections.singletonList(key),
        String.valueOf(timestamp),
        messageId.toString(),
        userId.toString()
    );
}
```

**Why Lua here:**
- Count + Add + Conditional Notify must be atomic
- Race condition possible if not atomic
- Complex conditional logic

---

## Consequences

### Pipeline

**Positive:**
- ✅ Simple Java code (no Lua to maintain)
- ✅ Easier to debug (standard Java debugger works)
- ✅ Easier to test (mock Redis operations)
- ✅ Easier to understand (no script files)
- ✅ Good performance (1 network round trip)

**Negative:**
- ❌ Not atomic (each operation independent)
- ❌ Cannot do conditional logic server-side
- ❌ Cannot do read-modify-write safely

### Lua Script

**Positive:**
- ✅ Atomic execution (all-or-nothing)
- ✅ Server-side logic (conditional, calculations)
- ✅ Slightly better performance (no client-server data transfer)
- ✅ Can do complex read-modify-write patterns

**Negative:**
- ❌ More complex (Lua + Java)
- ❌ Harder to debug (limited debugging tools)
- ❌ Harder to test (need to test Lua script separately)
- ❌ Additional maintenance (script versioning, deployment)
- ❌ Harder to understand (separate script files)

---

## Alternatives Considered

### Alternative 1: Individual Operations (No Batching)

```java
for each member:
    redisTemplate.opsForZSet().add(key, messageId, timestamp);  // N network calls
```

**Rejected because:**
- ❌ N network round trips (1000 members = 1000 network calls)
- ❌ Terrible performance at scale
- ❌ Network latency multiplied by N

### Alternative 2: Redis Transactions (MULTI/EXEC)

```java
redisTemplate.multi();
for each member:
    redisTemplate.opsForZSet().add(...);
redisTemplate.exec();
```

**Rejected because:**
- ❌ Doesn't reduce network round trips (still N calls)
- ❌ Only provides atomicity, not performance
- ❌ Blocks other clients during transaction
- Pipeline is strictly better for performance

### Alternative 3: Always Use Lua Script

```lua
-- Do everything in Lua
```

**Rejected because:**
- ❌ Over-engineering for simple cases
- ❌ Unnecessary complexity
- ❌ Harder to maintain
- Pipeline is simpler and good enough when atomicity not needed

---

## Guidelines for Future Decisions

### Use Pipeline When:
1. Operations are **independent** (failure of one doesn't affect others)
2. **No conditional logic** needed
3. **Partial success is acceptable**
4. Want **simpler code and easier testing**

### Use Lua Script When:
1. **Atomicity is required** (all-or-nothing)
2. **Read-modify-write** patterns with race conditions
3. **Complex conditional logic** on server side
4. **Performance critical** and need to minimize data transfer

### Quick Test:
Ask yourself: **"If operation A succeeds but operation B fails, is the system in an invalid state?"**
- **No**: Use Pipeline
- **Yes**: Use Lua Script

---

## Related

- ADR-0003: Redis ZSET for Unread Counts (uses Pipeline for batch updates)
- Implementation: `UnreadCountService.incrementUnreadCount()`
- Future: Consider Lua Script if adding threshold notifications

## References

- [Redis Pipelining](https://redis.io/docs/manual/pipelining/)
- [Redis Lua Scripting](https://redis.io/docs/manual/programmability/eval-intro/)
- [Spring Data Redis Pipeline](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#pipeline)

## Notes

- If unsure, **start with Pipeline** (simpler) and migrate to Lua Script if atomicity becomes a requirement
- Don't prematurely optimize with Lua Script - YAGNI (You Aren't Gonna Need It)
- Pipeline is easier to understand and maintain - prefer it unless you have a clear reason for atomicity
