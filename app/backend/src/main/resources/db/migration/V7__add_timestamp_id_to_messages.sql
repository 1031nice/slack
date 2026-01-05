-- Add timestamp_id column for Slack-style message identification
-- Format: {unix_timestamp_ms}.{6-digit-sequence} (e.g., "1640995200123.000001")
-- Unique per channel (not globally unique)

-- Step 1: Add timestamp_id column (nullable initially for backfill)
ALTER TABLE messages ADD COLUMN timestamp_id VARCHAR(20);

-- Step 2: Backfill existing messages with generated timestamp IDs
-- Format: createdAt timestamp (ms) + zero-padded id
-- This ensures uniqueness during migration
UPDATE messages
SET timestamp_id = CONCAT(
    FLOOR(EXTRACT(EPOCH FROM created_at) * 1000)::BIGINT,
    '.',
    LPAD(id::TEXT, 6, '0')
)
WHERE timestamp_id IS NULL;

-- Step 3: Create unique index on (channel_id, timestamp_id)
-- This enforces per-channel uniqueness like Slack
CREATE UNIQUE INDEX idx_messages_channel_timestamp
ON messages(channel_id, timestamp_id);

-- Step 4: Make timestamp_id NOT NULL after backfill
ALTER TABLE messages ALTER COLUMN timestamp_id SET NOT NULL;

-- Note: New messages will use MessageTimestampGenerator for timestamp_id generation
-- Format ensures chronological ordering and per-channel uniqueness
