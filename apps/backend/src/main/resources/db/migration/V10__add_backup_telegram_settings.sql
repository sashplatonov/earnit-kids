CREATE TABLE IF NOT EXISTS backup_telegram_settings (
    id VARCHAR(32) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    bot_token VARCHAR(512),
    chat_id VARCHAR(255),
    interval_hours INTEGER NOT NULL DEFAULT 24,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    last_sent_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
