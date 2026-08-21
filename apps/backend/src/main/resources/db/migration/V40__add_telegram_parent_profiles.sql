-- Existing email-based parent accounts retain their email; only Telegram-only accounts may leave it NULL.
ALTER TABLE parent_accounts
    ALTER COLUMN email DROP NOT NULL;

ALTER TABLE family_parent_memberships
    ADD COLUMN display_name VARCHAR(255);

ALTER TABLE telegram_identities
    ADD COLUMN telegram_username VARCHAR(64);

ALTER TABLE telegram_identities
    ADD COLUMN telegram_display_name VARCHAR(255);

ALTER TABLE telegram_parent_invitations
    ADD COLUMN parent_name VARCHAR(255);
