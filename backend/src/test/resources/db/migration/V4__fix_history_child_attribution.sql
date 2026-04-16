-- H2-compatible version of V4 (uses standard SQL compatible with H2)

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
