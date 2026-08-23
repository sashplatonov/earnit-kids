CREATE TABLE child_magic_link_invitations (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    token_digest VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    consumed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_child_magic_link_invitation_status
        CHECK (status IN ('pending', 'consumed', 'expired', 'revoked'))
);

CREATE INDEX idx_child_magic_link_invitation_child_status
    ON child_magic_link_invitations(child_id, status)
;
CREATE INDEX idx_child_magic_link_invitation_lookup
    ON child_magic_link_invitations(token_digest, expires_at);
