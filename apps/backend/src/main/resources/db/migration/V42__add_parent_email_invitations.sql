CREATE TABLE parent_email_invitations (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    normalized_email VARCHAR(320) NOT NULL,
    permission VARCHAR(32) NOT NULL,
    token_digest VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    invited_by_email VARCHAR(320) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    consumed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    superseded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_parent_email_invitation_status
        CHECK (status IN ('pending', 'accepted', 'expired', 'revoked', 'superseded')),
    CONSTRAINT ck_parent_email_invitation_permission
        CHECK (permission IN ('viewer', 'editor', 'family_admin'))
);

CREATE UNIQUE INDEX uq_parent_email_invitation_pending
    ON parent_email_invitations(family_id, normalized_email, status);
CREATE INDEX idx_parent_email_invitation_lookup
    ON parent_email_invitations(token_digest, expires_at);
CREATE INDEX idx_parent_email_invitation_expiry
    ON parent_email_invitations(family_id, status, expires_at);

ALTER TABLE family_parent_memberships
    ADD CONSTRAINT uq_family_parent_membership_family_parent
    UNIQUE (family_id, parent_account_id);

CREATE TABLE security_audit_events (
    id SERIAL PRIMARY KEY,
    family_id INTEGER REFERENCES families(id) ON DELETE CASCADE,
    actor_parent_account_id INTEGER REFERENCES parent_accounts(id) ON DELETE SET NULL,
    actor_email VARCHAR(320),
    target_email VARCHAR(320),
    event_type VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    request_correlation_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_security_audit_events_created_at ON security_audit_events(created_at);
