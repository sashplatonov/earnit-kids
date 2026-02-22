-- Migration: device push tokens for background/closed-app notifications
-- Created: 2026-02-19

CREATE TABLE IF NOT EXISTS device_push_tokens (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER REFERENCES children(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('admin', 'child')),
    platform VARCHAR(20) NOT NULL DEFAULT 'unknown',
    token TEXT NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_seen_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_device_push_tokens_family_id ON device_push_tokens(family_id);
CREATE INDEX IF NOT EXISTS idx_device_push_tokens_child_id ON device_push_tokens(child_id);
CREATE INDEX IF NOT EXISTS idx_device_push_tokens_role ON device_push_tokens(role);
CREATE INDEX IF NOT EXISTS idx_device_push_tokens_active ON device_push_tokens(is_active);
