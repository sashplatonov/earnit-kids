ALTER TABLE telegram_identities
    ADD COLUMN parent_account_id INTEGER REFERENCES parent_accounts(id) ON DELETE CASCADE;

UPDATE telegram_identities
SET parent_account_id = (
    SELECT parent_accounts.id
    FROM families
    JOIN parent_accounts ON parent_accounts.email = families.email
    WHERE families.id = telegram_identities.family_id
)
WHERE role = 'parent' AND parent_account_id IS NULL;

CREATE INDEX idx_telegram_identity_parent_account
    ON telegram_identities(parent_account_id);

ALTER TABLE telegram_identities
    ADD COLUMN active_parent_account_key INTEGER GENERATED ALWAYS AS (
        CASE WHEN role = 'parent' AND is_active THEN parent_account_id ELSE NULL END
    ) STORED;

CREATE UNIQUE INDEX uq_telegram_identity_active_parent_account
    ON telegram_identities(active_parent_account_key);

CREATE TABLE telegram_parent_link_challenges (
    id SERIAL PRIMARY KEY,
    parent_account_id INTEGER NOT NULL REFERENCES parent_accounts(id) ON DELETE CASCADE,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    secret_digest VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_telegram_parent_link_challenge_lookup
    ON telegram_parent_link_challenges(secret_digest, expires_at);
