-- Admin-transfer requests support the approval-based transfer of family_admin
-- ownership (HTML render screens 9-12). A request is created by the current
-- admin (actor) targeting a recipient (target). Only one pending request per
-- family is allowed; the partial unique index enforces that invariant.
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

-- Only one pending transfer per family at a time.
CREATE UNIQUE INDEX uq_family_admin_transfer_pending
    ON family_admin_transfer_requests(family_id)
    WHERE status = 'pending';

CREATE INDEX idx_family_admin_transfer_family
    ON family_admin_transfer_requests(family_id, status);
CREATE INDEX idx_family_admin_transfer_target
    ON family_admin_transfer_requests(target_membership_id, status);
