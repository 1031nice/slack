-- Create read_receipts table for tracking which messages users have read
CREATE TABLE read_receipts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel_id BIGINT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    last_read_sequence BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_read_receipt_user_channel UNIQUE (user_id, channel_id)
);

-- Create index for efficient querying by channel
CREATE INDEX idx_read_receipts_channel_id ON read_receipts(channel_id);

-- Create index for efficient querying by user
CREATE INDEX idx_read_receipts_user_id ON read_receipts(user_id);

-- Create index for querying users who read specific sequence
CREATE INDEX idx_read_receipts_channel_sequence ON read_receipts(channel_id, last_read_sequence);
