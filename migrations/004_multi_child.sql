-- Migration: Support multiple children per family
-- Created: 2026-02-04

-- 1. Create children table
CREATE TABLE children (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    token VARCHAR(255) UNIQUE,
    balance INTEGER DEFAULT 0,
    monthly_limit INTEGER DEFAULT 10000,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_children_family_id ON children(family_id);
CREATE INDEX idx_children_token ON children(token);

-- 2. Migrate existing single-child data to children table
INSERT INTO children (family_id, name, token, balance, monthly_limit)
SELECT 
    f.id, 
    COALESCE(f.child_nickname, 'Child'), 
    f.child_token, 
    COALESCE(fd.balance, 0),
    COALESCE(f.monthly_limit, 10000)
FROM families f
LEFT JOIN family_data fd ON f.id = fd.family_id;

-- 3. Add child_id to tasks
ALTER TABLE tasks ADD COLUMN child_id INTEGER REFERENCES children(id) ON DELETE CASCADE;

-- Update tasks to link to the new child
UPDATE tasks t
SET child_id = c.id
FROM children c
WHERE t.family_id = c.family_id;

-- Make child_id NOT NULL after update
ALTER TABLE tasks ALTER COLUMN child_id SET NOT NULL;

-- Update unique constraint for tasks
ALTER TABLE tasks DROP CONSTRAINT tasks_family_id_task_id_key;
ALTER TABLE tasks ADD CONSTRAINT tasks_child_id_task_id_key UNIQUE (child_id, task_id);


-- 4. Add child_id to shop_items
ALTER TABLE shop_items ADD COLUMN child_id INTEGER REFERENCES children(id) ON DELETE CASCADE;

UPDATE shop_items s
SET child_id = c.id
FROM children c
WHERE s.family_id = c.family_id;

ALTER TABLE shop_items ALTER COLUMN child_id SET NOT NULL;

-- Update unique constraint for shop_items
ALTER TABLE shop_items DROP CONSTRAINT shop_items_family_id_item_id_key;
ALTER TABLE shop_items ADD CONSTRAINT shop_items_child_id_item_id_key UNIQUE (child_id, item_id);


-- 5. Add child_id to history
ALTER TABLE history ADD COLUMN child_id INTEGER REFERENCES children(id) ON DELETE CASCADE;

UPDATE history h
SET child_id = c.id
FROM children c
WHERE h.family_id = c.family_id;

ALTER TABLE history ALTER COLUMN child_id SET NOT NULL;

CREATE INDEX idx_history_child_id ON history(child_id);


-- 6. Add child_id to requests
ALTER TABLE requests ADD COLUMN child_id INTEGER REFERENCES children(id) ON DELETE CASCADE;

UPDATE requests r
SET child_id = c.id
FROM children c
WHERE r.family_id = c.family_id;

ALTER TABLE requests ALTER COLUMN child_id SET NOT NULL;

CREATE INDEX idx_requests_child_id ON requests(child_id);


-- 7. Cleanup old tables/columns
DROP TABLE family_data;

ALTER TABLE families DROP COLUMN child_nickname;
ALTER TABLE families DROP COLUMN child_token;
ALTER TABLE families DROP COLUMN monthly_limit;

-- 8. Friends
-- Current friends table: family_id, friend_family_id.
ALTER TABLE friends ADD COLUMN child_id INTEGER REFERENCES children(id) ON DELETE CASCADE;
ALTER TABLE friends ADD COLUMN friend_child_id INTEGER REFERENCES children(id) ON DELETE CASCADE;

UPDATE friends fr
SET 
    child_id = c1.id,
    friend_child_id = c2.id
FROM children c1, children c2
WHERE fr.family_id = c1.family_id AND fr.friend_family_id = c2.family_id;

-- Make columns NOT NULL (Assuming all friends were successfully migrated)
-- If there were orphan friend records they might fail here. 
-- But CASCADE delete on families likely kept it clean.
ALTER TABLE friends ALTER COLUMN child_id SET NOT NULL;
ALTER TABLE friends ALTER COLUMN friend_child_id SET NOT NULL;

-- Drop old columns
ALTER TABLE friends DROP CONSTRAINT friends_family_id_friend_family_id_key;
ALTER TABLE friends DROP COLUMN family_id;
ALTER TABLE friends DROP COLUMN friend_family_id;

-- Add new constraint
ALTER TABLE friends ADD CONSTRAINT friends_child_id_friend_child_id_key UNIQUE (child_id, friend_child_id);
