-- Note: Logic-wise, rows now prefer IDs. We keep columns but stop relying on them for new data.
-- If the user wants to physically drop them, we can, but soft cleanup (nulling) is safer for existing data.
UPDATE history SET description = NULL, group_name = NULL, comment = NULL WHERE related_id IS NOT NULL;
