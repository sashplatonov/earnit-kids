-- Migration: add web push subscription support to device_push_tokens
-- Created: 2026-03-01

-- Allow token to be nullable so web push rows can use endpoint+keys instead
ALTER TABLE device_push_tokens
    ALTER COLUMN token DROP NOT NULL;

-- Add web push subscription fields
ALTER TABLE device_push_tokens
    ADD COLUMN IF NOT EXISTS push_type VARCHAR(10) NOT NULL DEFAULT 'fcm'
        CHECK (push_type IN ('fcm', 'web')),
    ADD COLUMN IF NOT EXISTS endpoint TEXT,
    ADD COLUMN IF NOT EXISTS key_p256dh TEXT,
    ADD COLUMN IF NOT EXISTS key_auth TEXT;

-- Web push rows are unique by endpoint
CREATE UNIQUE INDEX IF NOT EXISTS idx_device_push_tokens_endpoint
    ON device_push_tokens(endpoint) WHERE endpoint IS NOT NULL;
