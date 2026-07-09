-- Keep test migration history aligned with the production Flyway chain.
ALTER TABLE backup_telegram_settings
    ADD COLUMN IF NOT EXISTS backup_retention_count INTEGER NOT NULL DEFAULT 20;
