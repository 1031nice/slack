-- Create mentions table for @mention notifications
CREATE TABLE mentions (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    mentioned_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_mention_message_user UNIQUE (message_id, mentioned_user_id)
);

-- Create index for efficient querying of mentions by user
CREATE INDEX idx_mentions_mentioned_user_id ON mentions(mentioned_user_id);

-- Create index for efficient querying of unread mentions
CREATE INDEX idx_mentions_unread ON mentions(mentioned_user_id, is_read) WHERE is_read = FALSE;

-- Create index for querying mentions by message
CREATE INDEX idx_mentions_message_id ON mentions(message_id);
