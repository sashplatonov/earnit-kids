-- Test mirror for V41__drop_legacy_email_auth_columns.sql
DROP INDEX IF EXISTS idx_families_verification_token;
DROP INDEX IF EXISTS idx_families_reset_token_expires_at;
DROP INDEX IF EXISTS idx_families_reset_token;
DROP INDEX IF EXISTS idx_parent_accounts_verification_token;
DROP INDEX IF EXISTS idx_parent_accounts_reset_token_expires_at;
DROP INDEX IF EXISTS idx_parent_accounts_reset_token;

ALTER TABLE families DROP COLUMN IF EXISTS is_verified;
ALTER TABLE families DROP COLUMN IF EXISTS verification_token;
ALTER TABLE families DROP COLUMN IF EXISTS reset_token;
ALTER TABLE families DROP COLUMN IF EXISTS reset_token_expires_at;

ALTER TABLE parent_accounts DROP COLUMN IF EXISTS is_verified;
ALTER TABLE parent_accounts DROP COLUMN IF EXISTS verification_token;
ALTER TABLE parent_accounts DROP COLUMN IF EXISTS reset_token;
ALTER TABLE parent_accounts DROP COLUMN IF EXISTS reset_token_expires_at;
