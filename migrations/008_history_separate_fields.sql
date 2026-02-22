-- Migration: support separate fields for history entries
-- Created: 2026-02-22

ALTER TABLE history ADD COLUMN IF NOT EXISTS group_name VARCHAR(255);
ALTER TABLE history ADD COLUMN IF NOT EXISTS comment TEXT;

CREATE INDEX IF NOT EXISTS idx_history_group_name ON history(group_name);
