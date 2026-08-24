-- Global admin analytics filter by time and then aggregate by event type or status.
-- Family-scoped indexes do not serve these cross-family dashboard queries.
CREATE INDEX IF NOT EXISTS idx_history_type_created
    ON history(type, created_at);

CREATE INDEX IF NOT EXISTS idx_requests_type_status_created
    ON requests(request_type, status, created_at);

CREATE INDEX IF NOT EXISTS idx_requests_status_updated
    ON requests(status, updated_at);

CREATE INDEX IF NOT EXISTS idx_families_created
    ON families(created_at);
