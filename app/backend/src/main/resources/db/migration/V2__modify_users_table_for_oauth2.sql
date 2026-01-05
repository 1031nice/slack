-- Modify users table for OAuth2 integration
-- Remove password column and add auth_user_id column

-- Add auth_user_id column first (nullable initially for existing data)
ALTER TABLE users ADD COLUMN auth_user_id VARCHAR(255);

-- Drop password column
ALTER TABLE users DROP COLUMN password;

-- Make auth_user_id NOT NULL and UNIQUE (after data migration if needed)
-- Note: This assumes no existing data. If data exists, populate auth_user_id first.
ALTER TABLE users 
    ALTER COLUMN auth_user_id SET NOT NULL,
    ADD CONSTRAINT uk_users_auth_user_id UNIQUE (auth_user_id);

-- Create index for auth_user_id for faster lookups
CREATE INDEX idx_users_auth_user_id ON users(auth_user_id);

