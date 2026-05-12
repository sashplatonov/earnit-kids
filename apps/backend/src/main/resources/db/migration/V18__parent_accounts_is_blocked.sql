-- Add the blocked flag expected by ParentAccountEntity.

ALTER TABLE parent_accounts
    ADD COLUMN is_blocked BOOLEAN NOT NULL DEFAULT FALSE;
