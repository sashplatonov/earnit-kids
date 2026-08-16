ALTER TABLE tasks ADD COLUMN IF NOT EXISTS source_catalog_item_id BIGINT;
ALTER TABLE shop_items ADD COLUMN IF NOT EXISTS source_catalog_item_id BIGINT;
