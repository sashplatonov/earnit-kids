CREATE TABLE web_push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    parent_account_id INTEGER REFERENCES parent_accounts(id) ON DELETE CASCADE,
    child_id INTEGER REFERENCES children(id) ON DELETE CASCADE,
    actor_type VARCHAR(16) NOT NULL,
    endpoint TEXT NOT NULL,
    p256dh_key VARCHAR(200) NOT NULL,
    auth_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_web_push_endpoint UNIQUE (endpoint),
    CONSTRAINT ck_web_push_actor CHECK (
        (actor_type = 'parent' AND parent_account_id IS NOT NULL AND child_id IS NULL)
        OR (actor_type = 'child' AND parent_account_id IS NULL AND child_id IS NOT NULL)
    )
);
CREATE INDEX idx_web_push_subscription_recipient
    ON web_push_subscriptions(family_id, actor_type, parent_account_id, child_id);

CREATE TABLE web_push_deliveries (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES application_outbox_events(id) ON DELETE CASCADE,
    subscription_id BIGINT NOT NULL REFERENCES web_push_subscriptions(id) ON DELETE CASCADE,
    transport VARCHAR(32) NOT NULL DEFAULT 'WEB_PUSH',
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500) NOT NULL,
    deep_link VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    claimed_at TIMESTAMP WITH TIME ZONE,
    terminal_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(120),
    CONSTRAINT uq_web_push_delivery UNIQUE (event_id, subscription_id, transport)
);
CREATE INDEX idx_web_push_delivery_due
    ON web_push_deliveries(status, next_attempt_at, claimed_at);
