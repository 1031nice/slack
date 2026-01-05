-- Add sequence_number column to messages table for message ordering and reconnection recovery
ALTER TABLE messages ADD COLUMN sequence_number BIGINT;

-- Create index for efficient querying by channel and sequence number
CREATE INDEX idx_messages_channel_sequence ON messages(channel_id, sequence_number);

-- Note: Existing messages will have NULL sequence_number
-- New messages will have sequence numbers assigned by MessageSequenceService

