ALTER TABLE parent_email_invitations
    ADD COLUMN token_digest_key_id VARCHAR(64);

-- Existing rows retain their digest only when the operator supplies the old
-- secret as the configured previous key with identifier "legacy".
UPDATE parent_email_invitations
SET token_digest_key_id = 'legacy'
WHERE token_digest_key_id IS NULL;

ALTER TABLE parent_email_invitations
    ALTER COLUMN token_digest_key_id SET NOT NULL;

CREATE INDEX idx_parent_email_invitation_digest_key
    ON parent_email_invitations(token_digest_key_id, token_digest);
