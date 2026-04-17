-- V4: Fix history entries that were incorrectly attributed to the wrong child.
--
-- Root cause: syncHistory() used to force every history entry's child_id to
-- selectedChildId (the admin's currently-viewed child) instead of reading the
-- childId that the client encoded in each entry.  As a result, approving a
-- request for child B while the admin was viewing child A stored the earn/spend
-- entry under child A.
--
-- We can reliably detect and correct these rows: for 'earn' entries the
-- related_id points to a task_id; for 'spend' entries it points to an item_id.
-- If the referenced task/item does NOT belong to history.child_id but DOES
-- belong to exactly one other child in the same family, the row is misassigned
-- and we move it to the correct child.
--
-- Entries without a related_id or where the task/item no longer exists cannot
-- be automatically corrected and are left unchanged.

-- Fix 'earn' entries whose referenced task belongs to a different child.
UPDATE history
SET child_id = (
    SELECT t.child_id
    FROM tasks t
    WHERE t.task_id   = history.related_id
      AND t.family_id = history.family_id
      AND t.child_id <> history.child_id
    LIMIT 1
)
WHERE history.type = 'earn'
  AND history.related_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM tasks t
      WHERE t.task_id   = history.related_id
        AND t.family_id = history.family_id
        AND t.child_id  = history.child_id
  )
  AND (
      SELECT COUNT(*)
      FROM tasks t
      WHERE t.task_id   = history.related_id
        AND t.family_id = history.family_id
        AND t.child_id <> history.child_id
  ) = 1;

-- Fix 'spend' entries whose referenced shop item belongs to a different child.
UPDATE history
SET child_id = (
    SELECT i.child_id
    FROM shop_items i
    WHERE i.item_id   = history.related_id
      AND i.family_id = history.family_id
      AND i.child_id <> history.child_id
    LIMIT 1
)
WHERE history.type = 'spend'
  AND history.related_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM shop_items i
      WHERE i.item_id   = history.related_id
        AND i.family_id = history.family_id
        AND i.child_id  = history.child_id
  )
  AND (
      SELECT COUNT(*)
      FROM shop_items i
      WHERE i.item_id   = history.related_id
        AND i.family_id = history.family_id
        AND i.child_id <> history.child_id
  ) = 1;
