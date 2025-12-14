# ADR-0003: Use Redis Sorted Set (ZSET) for Unread Count Tracking

## Status

**Accepted** - 2024-12-14

## Context

Real-time chat applications like Slack require efficient unread count tracking for:
- Channel unread messages
- Direct message (DM) unread counts
- Notification badges
- Mention (@user) counts
- Thread reply counts

### Requirements

1. **Performance**: Must support high-throughput writes (every message sent)
2. **Accuracy**: No duplicate counting, even with retries/network issues
3. **Scalability**: Must work with millions of users and channels
4. **Extensibility**: Should support advanced features like:
   - "Mark as read until here" functionality
   - Time-based queries (e.g., "unread from last 7 days")
   - Partial read tracking (which specific messages are unread)
5. **Memory Efficiency**: Reasonable memory usage for production scale

### Scale Considerations

For a Slack-scale application:
- 10M+ daily active users
- 100M+ messages per day
- Average 50 channels per user
- Peak: 100K messages/second

## Decision

**Use Redis Sorted Set (ZSET) to track unread messages.**

### Data Structure

```
Key:     unread:{userId}:{channelId}
Members: messageId (strings, deduplicated)
Scores:  timestamp (milliseconds since epoch)
Count:   ZCARD = number of unread messages
```

### Implementation

```java
// Add unread message: O(log N)
redisTemplate.opsForZSet().add(key, messageId, timestamp);

// Get count: O(1)
redisTemplate.opsForZSet().count(key, -∞, +∞);

// Clear all: O(1)
redisTemplate.delete(key);

// Get unread message IDs (sorted by time): O(log N + M)
redisTemplate.opsForZSet().range(key, 0, -1);
```

## Consequences

### Positive

1. **O(1) Read Performance**
   - Count queries are constant time
   - Critical for rendering channel lists with unread badges

2. **Automatic Deduplication**
   - Same messageId can only exist once
   - Safe against duplicate events, retries, race conditions

3. **Time-Ordered Data**
   - Messages naturally sorted by timestamp
   - Enables "oldest unread" queries
   - Supports time-range filtering

4. **Advanced Features Support**
   - Mark as read until timestamp: `ZREMRANGEBYSCORE key -inf {timestamp}`
   - Auto-cleanup old unreads: `ZREMRANGEBYSCORE key -inf {30daysAgo}`
   - Get recent N unreads: `ZREVRANGE key 0 N`

5. **Memory Efficiency**
   - Only stores unread messages (not all messages)
   - Auto-expires when user reads

6. **Reusable Pattern**
   - Can be applied to notifications, mentions, thread replies
   - Consistent approach across the application

### Negative

1. **Memory Cost**
   - ~24 bytes per unread message (messageId + timestamp + overhead)
   - For 10M users × 50 channels × 100 avg unreads = ~1.2TB
   - Mitigation: Auto-cleanup old unreads, reasonable TTL

2. **Slightly More Complex Than Counter**
   - Simple counter: `INCR/DECR`
   - ZSET: `ZADD/ZCARD/ZREM`
   - Trade-off: Complexity for features

3. **Write Amplification**
   - Each message writes to N member ZSETs (N = channel members)
   - For 1000-member channel, 1 message = 1000 ZADD operations
   - Mitigation: Redis pipelining, batching

## Alternatives Considered

### Alternative 1: Simple Counter (String/Number)

```redis
Key:   unread:{userId}:{channelId}
Value: 42
```

**Rejected because:**
- ❌ Cannot track which messages are unread
- ❌ Duplicate increment risk (same message counted twice)
- ❌ No "mark as read until" support
- ❌ Cannot implement partial read

### Alternative 2: Set (Unordered)

```redis
Key:     unread:{userId}:{channelId}
Members: messageIds
```

**Rejected because:**
- ❌ No timestamp information
- ❌ Cannot auto-cleanup old unreads
- ❌ No time-based queries
- ✅ Deduplication works
- ✅ O(1) count

### Alternative 3: List

```redis
Key:   unread:{userId}:{channelId}
Value: [msg1, msg2, msg3, ...]
```

**Rejected because:**
- ❌ Allows duplicates
- ❌ Count is O(N)
- ❌ Remove specific item is O(N)
- ❌ No deduplication

### Alternative 4: Hash

```redis
Key:   unread:{userId}:{channelId}
Field: messageId
Value: timestamp
```

**Rejected because:**
- ❌ No automatic sorting
- ❌ Range queries are inefficient
- ❌ Cannot get "oldest unread" easily
- ✅ Deduplication works
- ✅ O(1) count

### Alternative 5: Database (PostgreSQL/MySQL)

```sql
CREATE TABLE unread_messages (
    user_id BIGINT,
    channel_id BIGINT,
    message_id BIGINT,
    timestamp BIGINT,
    PRIMARY KEY (user_id, channel_id, message_id)
);
```

**Rejected because:**
- ❌ Too slow for read-heavy workload (count queries on every channel list)
- ❌ Database becomes bottleneck at scale
- ❌ Requires complex caching layer
- ✅ Durable storage
- ✅ Complex queries possible

## Applicability

This pattern should be used for:

- ✅ Channel unread message counts
- ✅ Direct message (DM) unread counts
- ✅ Notification badge counts
- ✅ Mention (@user) unread counts
- ✅ Thread reply unread counts
- ✅ Unread file shares
- ✅ Any feature requiring "unread items with timestamp"

This pattern should NOT be used for:

- ❌ Total message count (use database or simple counter)
- ❌ Permanent audit logs (use database)
- ❌ Analytics/metrics (use time-series database)

## Related

- Implementation: `backend/src/main/java/com/slack/service/UnreadCountService.java`
- Tests: `backend/src/test/java/com/slack/service/UnreadCountServiceTest.java`
- Redis configuration: `backend/src/main/resources/application.yml`

## References

- [Redis Sorted Sets Documentation](https://redis.io/docs/data-types/sorted-sets/)
- [Slack Engineering: Scaling Presence](https://slack.engineering/scaling-slacks-job-queue/)
- [Discord: How Discord Stores Billions of Messages](https://discord.com/blog/how-discord-stores-billions-of-messages)

## Notes

- Consider adding TTL for old unread entries (e.g., 90 days)
- Monitor memory usage in production: `INFO memory` in Redis
- If a channel has >10K unread for a user, consider showing "9999+" instead of exact count
- Batch ZADD operations when possible to reduce network round trips
