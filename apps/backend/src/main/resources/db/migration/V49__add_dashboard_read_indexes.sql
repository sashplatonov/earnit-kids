-- Dashboard catalog reads filter by child and exclude soft-deleted rows.
-- The existing family_id indexes do not support the child-scoped dashboard path.
CREATE INDEX IF NOT EXISTS idx_tasks_child_deleted_id
    ON tasks(child_id, is_deleted, id);

CREATE INDEX IF NOT EXISTS idx_shop_items_child_deleted_id
    ON shop_items(child_id, is_deleted, id);

-- The dashboard batches pending requests by child, status, and time window
-- before grouping by task_id or item_id.
CREATE INDEX IF NOT EXISTS idx_requests_family_child_status_created
    ON requests(family_id, child_id, status, created_at DESC);
