-- Index audit for identity and membership query paths.
-- Covers token lookups and active membership filters used by auth and admin flows.

CREATE INDEX IF NOT EXISTS idx_families_verification_token
    ON families(verification_token);

CREATE INDEX IF NOT EXISTS idx_families_reset_token_expires_at
    ON families(reset_token, reset_token_expires_at);

CREATE INDEX IF NOT EXISTS idx_parent_accounts_verification_token
    ON parent_accounts(verification_token);

CREATE INDEX IF NOT EXISTS idx_parent_accounts_reset_token_expires_at
    ON parent_accounts(reset_token, reset_token_expires_at);

CREATE INDEX IF NOT EXISTS idx_memberships_parent_status_family
    ON family_parent_memberships(parent_account_id, status, family_id);

CREATE INDEX IF NOT EXISTS idx_memberships_family_status_permission
    ON family_parent_memberships(family_id, status, permission);
