-- Add missing updated_at columns and align H2 timestamp behavior with runtime schema.
-- H2 uses column-level ON UPDATE expressions instead of PostgreSQL trigger functions.
-- Add missing updated_at columns for tests. Backfill from created_at where missing.
-- No ON UPDATE / DEFAULT runtime logic in test migrations; timestamps are set by the application.

ALTER TABLE history ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE history SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE updated_at IS NULL;
ALTER TABLE history ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE friends ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE friends SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE updated_at IS NULL;
ALTER TABLE friends ALTER COLUMN updated_at SET NOT NULL;

-- Ensure other tables have non-null updated_at for tests
ALTER TABLE families ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE families SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE updated_at IS NULL;
ALTER TABLE families ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE children ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE children SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE updated_at IS NULL;
ALTER TABLE children ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE tasks SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE updated_at IS NULL;
ALTER TABLE tasks ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE shop_items ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE shop_items SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE updated_at IS NULL;
ALTER TABLE shop_items ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE requests ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE requests SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE updated_at IS NULL;
ALTER TABLE requests ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE device_push_tokens ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE device_push_tokens SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE updated_at IS NULL;
ALTER TABLE device_push_tokens ALTER COLUMN updated_at SET NOT NULL;