CREATE TABLE telegram_identities (
    id SERIAL PRIMARY KEY,
    family_id INTEGER REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER REFERENCES children(id) ON DELETE CASCADE,
    telegram_user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('parent', 'child')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    unlinked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT telegram_identity_owner_check CHECK ((role = 'parent' AND family_id IS NOT NULL AND child_id IS NULL) OR (role = 'child' AND child_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_telegram_identity_active_user ON telegram_identities(telegram_user_id) WHERE is_active = TRUE;
CREATE UNIQUE INDEX uq_telegram_identity_active_child ON telegram_identities(child_id) WHERE role = 'child' AND is_active = TRUE;
CREATE INDEX idx_telegram_identity_family ON telegram_identities(family_id);
CREATE INDEX idx_telegram_identity_child ON telegram_identities(child_id);

CREATE TABLE telegram_child_invitations (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    secret_digest VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    consumed_at TIMESTAMP WITH TIME ZONE,
    issued_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_telegram_invite_lookup ON telegram_child_invitations(secret_digest, expires_at);

CREATE TABLE telegram_callback_actions (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    identity_id INTEGER REFERENCES telegram_identities(id) ON DELETE SET NULL,
    action VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    secret_digest VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_telegram_callback_lookup ON telegram_callback_actions(secret_digest, expires_at);

CREATE TABLE telegram_webhook_updates (
    id SERIAL PRIMARY KEY,
    update_id BIGINT NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_telegram_webhook_update_id UNIQUE (update_id)
);

CREATE TABLE telegram_security_audit_events (
    id SERIAL PRIMARY KEY,
    family_id INTEGER REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER REFERENCES children(id) ON DELETE SET NULL,
    identity_id INTEGER REFERENCES telegram_identities(id) ON DELETE SET NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_reference VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_telegram_audit_family_time ON telegram_security_audit_events(family_id, created_at);
