-- Measured composite indexes for the query paths that still showed repeated
-- filter and window scans after pagination was split out in BAP-07.
-- Keep these aligned with repository predicates instead of speculative columns.

-- history: task and item limit checks, plus top task/item analytics windows.
CREATE INDEX IF NOT EXISTS idx_history_family_child_type_related_created
    ON history(family_id, child_id, type, related_id, created_at DESC);

-- requests: pending task-limit checks.
CREATE INDEX IF NOT EXISTS idx_requests_family_child_task_status_created
    ON requests(family_id, child_id, task_id, status, created_at DESC);

-- requests: pending item-limit checks.
CREATE INDEX IF NOT EXISTS idx_requests_family_child_item_status_created
    ON requests(family_id, child_id, item_id, status, created_at DESC);
