-- Remove sequence_number from messages table (replaced by timestamp-based IDs)
DROP INDEX IF EXISTS idx_messages_channel_sequence;
ALTER TABLE messages DROP COLUMN IF EXISTS sequence_number;

-- Convert read_receipts from sequence-based to timestamp-based
-- Drop old index for sequence-based lookups
DROP INDEX IF EXISTS idx_read_receipts_channel_sequence;

-- Rename and change type of last_read_sequence to last_read_timestamp
-- Using VARCHAR(30) to support both timestampId (e.g., "1735046400000001") and ISO datetime
ALTER TABLE read_receipts
    RENAME COLUMN last_read_sequence TO last_read_timestamp;

ALTER TABLE read_receipts
    ALTER COLUMN last_read_timestamp TYPE VARCHAR(30);

-- Create new index for timestamp-based lookups
CREATE INDEX idx_read_receipts_channel_timestamp ON read_receipts(channel_id, last_read_timestamp);
