CREATE TABLE application_outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    request_id BIGINT REFERENCES requests(id) ON DELETE SET NULL,
    coin_delta INTEGER NOT NULL DEFAULT 0,
    resulting_balance INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    planning_claimed_at TIMESTAMP WITH TIME ZONE,
    planning_completed_at TIMESTAMP WITH TIME ZONE,
    planning_status VARCHAR(32) NOT NULL DEFAULT 'UNPLANNED'
);
CREATE INDEX idx_outbox_planning ON application_outbox_events(planning_completed_at, planning_claimed_at);

CREATE TABLE telegram_deliveries (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES application_outbox_events(id) ON DELETE CASCADE,
    recipient_identity_id INTEGER NOT NULL REFERENCES telegram_identities(id) ON DELETE CASCADE,
    chat_id BIGINT NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    claimed_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    terminal_at TIMESTAMP WITH TIME ZONE,
    message_id BIGINT,
    last_error VARCHAR(500),
    CONSTRAINT uq_telegram_delivery_event_recipient UNIQUE (event_id, recipient_identity_id)
);
CREATE INDEX idx_telegram_delivery_due ON telegram_deliveries(status, next_attempt_at, claimed_at);
