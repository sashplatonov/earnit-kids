-- Merge of previously duplicated V11 migration (add_backup_retention_count)
-- Both V11 migrations were applied on prod; V11 duplicate is removed and
-- its contents are relocated here. Run 'flyway repair' on prod to remove
-- the deleted V11 entry from flyway_schema_history before deploying.

ALTER TABLE backup_telegram_settings
    ADD COLUMN IF NOT EXISTS backup_retention_count INTEGER NOT NULL DEFAULT 20;
