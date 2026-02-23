-- Remove the legacy store name column
ALTER TABLE families DROP COLUMN IF EXISTS name;
