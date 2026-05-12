-- Parent accounts and family memberships migration
-- Creates parent identity model separate from family ownership

-- Parent accounts table: stores unique parent identity
CREATE TABLE IF NOT EXISTS parent_accounts (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_verified BOOLEAN DEFAULT TRUE,
    verification_token VARCHAR(255),
    reset_token VARCHAR(64),
    reset_token_expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_parent_accounts_email ON parent_accounts(email);
CREATE INDEX IF NOT EXISTS idx_parent_accounts_reset_token ON parent_accounts(reset_token)
    WHERE reset_token IS NOT NULL;

-- Family parent memberships table: links parents to families with permissions
CREATE TYPE membership_permission AS ENUM ('viewer', 'editor', 'family_admin');

CREATE TABLE IF NOT EXISTS family_parent_memberships (
    id SERIAL PRIMARY KEY,
    parent_account_id INTEGER NOT NULL REFERENCES parent_accounts(id) ON DELETE CASCADE,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    permission membership_permission NOT NULL DEFAULT 'viewer',
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    invited_by_email VARCHAR(255),
    invited_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_membership_parent_family UNIQUE (parent_account_id, family_id)
);

CREATE INDEX IF NOT EXISTS idx_memberships_parent ON family_parent_memberships(parent_account_id);
CREATE INDEX IF NOT EXISTS idx_memberships_family ON family_parent_memberships(family_id);
CREATE INDEX IF NOT EXISTS idx_memberships_permission ON family_parent_memberships(permission);

-- Backfill: create parent accounts from existing families
INSERT INTO parent_accounts (email, password_hash, is_verified, verification_token, created_at, updated_at)
SELECT 
    email,
    admin_password,
    is_verified,
    verification_token,
    created_at,
    updated_at
FROM families
WHERE email IS NOT NULL AND email != '';

-- Backfill: create family_admin memberships for each existing family
INSERT INTO family_parent_memberships (parent_account_id, family_id, permission, status, created_at, updated_at)
SELECT 
    pa.id,
    f.id,
    'family_admin'::membership_permission,
    'active',
    f.created_at,
    f.updated_at
FROM families f
JOIN parent_accounts pa ON pa.email = f.email;