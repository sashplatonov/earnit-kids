-- Merge of previously duplicated V11 migration (add_backup_retention_count)
-- Both V11 migrations were applied on prod. The duplicate V11 file is removed
-- and its contents are relocated here. quarkus.flyway.ignore-missing-migrations=true
-- in application.properties allows Flyway to skip the missing V11 on startup.

ALTER TABLE backup_telegram_settings
    ADD COLUMN IF NOT EXISTS backup_retention_count INTEGER NOT NULL DEFAULT 20;
