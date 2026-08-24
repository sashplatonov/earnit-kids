-- Add the missing updated_at column to child_magic_link_invitations.
-- The entity extends CreatedAtEntity which requires both created_at and updated_at,
-- but V44 only created created_at. Backfill from created_at and set NOT NULL.
ALTER TABLE child_magic_link_invitations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE child_magic_link_invitations
SET updated_at = COALESCE(created_at, NOW())
WHERE updated_at IS NULL;
ALTER TABLE child_magic_link_invitations ALTER COLUMN updated_at SET NOT NULL;
