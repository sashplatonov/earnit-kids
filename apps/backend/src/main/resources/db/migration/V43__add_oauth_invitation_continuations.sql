CREATE TABLE oauth_invitation_continuations (
    id SERIAL PRIMARY KEY,
    invitation_id INTEGER NOT NULL REFERENCES parent_email_invitations(id) ON DELETE CASCADE,
    nonce_digest VARCHAR(128) NOT NULL UNIQUE,
    browser_binding_digest VARCHAR(128) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    post_login_path VARCHAR(256) NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    verified_email VARCHAR(320),
    CONSTRAINT ck_oauth_invitation_continuation_path
        CHECK (post_login_path LIKE '/invite/parent%')
);

CREATE INDEX idx_oauth_invitation_continuation_lookup
    ON oauth_invitation_continuations(id, browser_binding_digest, expires_at);
