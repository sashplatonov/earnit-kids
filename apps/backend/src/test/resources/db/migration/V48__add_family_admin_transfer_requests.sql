-- Test mirror for V48__add_family_admin_transfer_requests.sql.
-- H2 2.x does not support partial indexes (CREATE UNIQUE INDEX ... WHERE), so
-- the "one pending request per family" invariant is enforced via a generated
-- pending-family key column and a unique index on it, mirroring the same
-- pattern used in the H2 test migration V28__add_telegram_parent_account_linking.
CREATE TABLE family_admin_transfer_requests (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    actor_membership_id INTEGER NOT NULL REFERENCES family_parent_memberships(id) ON DELETE CASCADE,
    target_membership_id INTEGER NOT NULL REFERENCES family_parent_memberships(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_family_admin_transfer_request_status
        CHECK (status IN ('pending', 'accepted', 'declined', 'cancelled'))
);

ALTER TABLE family_admin_transfer_requests
    ADD COLUMN pending_family_key INTEGER GENERATED ALWAYS AS (
        CASE WHEN status = 'pending' THEN family_id ELSE NULL END
    );

CREATE UNIQUE INDEX uq_family_admin_transfer_pending
    ON family_admin_transfer_requests(pending_family_key);

CREATE INDEX idx_family_admin_transfer_family
    ON family_admin_transfer_requests(family_id, status);
CREATE INDEX idx_family_admin_transfer_target
    ON family_admin_transfer_requests(target_membership_id, status);
