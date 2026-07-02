-- Composite indexes for common query paths identified in the performance backlog.
-- Single-column indexes from V1 are often not selective enough; these composites
-- let PostgreSQL do index-only scans or efficient range scans for the actual query patterns.

-- history: child-scoped listing (most common: dashboard / child switch)
CREATE INDEX IF NOT EXISTS idx_history_child_created
    ON history(child_id, created_at DESC, id DESC);

-- history: family-scoped listing (admin view, analytics)
CREATE INDEX IF NOT EXISTS idx_history_family_created
    ON history(family_id, created_at DESC, id DESC);

-- history: latest timestamp per related_id (loadLatestHistoryTimestamps aggregation)
CREATE INDEX IF NOT EXISTS idx_history_child_type_related_created
    ON history(child_id, type, related_id, created_at DESC);

-- requests: family-scoped listing (admin requests page, polling)
CREATE INDEX IF NOT EXISTS idx_requests_family_created
    ON requests(family_id, created_at DESC, id DESC);
