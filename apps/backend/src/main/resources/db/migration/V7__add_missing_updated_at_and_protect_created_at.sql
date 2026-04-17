-- Add missing updated_at columns for immutable audit timestamps.
-- Keep created_at stable on every update and auto-refresh updated_at for all mutable rows.
-- Add missing updated_at columns for immutable audit timestamps.
-- This migration backfills `updated_at` from `created_at` when missing.
-- No triggers or runtime DB logic are created here; `updated_at` is managed by the application.

ALTER TABLE history ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE history
SET updated_at = COALESCE(created_at, NOW())
WHERE updated_at IS NULL;
ALTER TABLE history ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE friends ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE friends
SET updated_at = COALESCE(created_at, NOW())
WHERE updated_at IS NULL;
ALTER TABLE friends ALTER COLUMN updated_at SET NOT NULL;