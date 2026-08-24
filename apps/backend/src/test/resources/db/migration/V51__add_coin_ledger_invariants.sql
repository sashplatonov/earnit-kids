ALTER TABLE history ADD COLUMN IF NOT EXISTS reason VARCHAR(32);
ALTER TABLE history ADD COLUMN IF NOT EXISTS delta INTEGER;
ALTER TABLE history ADD COLUMN IF NOT EXISTS reverses_entry_id BIGINT;

UPDATE history SET reason = CASE WHEN type = 'spend' THEN 'REWARD_PURCHASE' ELSE 'TASK_REWARD' END
WHERE reason IS NULL;
UPDATE history SET delta = CASE WHEN type = 'spend' THEN -ABS(amount) ELSE ABS(amount) END
WHERE delta IS NULL;

ALTER TABLE history ALTER COLUMN reason SET DEFAULT 'MANUAL_ADJUSTMENT';
ALTER TABLE history ALTER COLUMN reason SET NOT NULL;
ALTER TABLE history ALTER COLUMN delta SET DEFAULT 0;
ALTER TABLE history ALTER COLUMN delta SET NOT NULL;
ALTER TABLE history ADD CONSTRAINT fk_history_reversal FOREIGN KEY (reverses_entry_id) REFERENCES history(id);
ALTER TABLE history ADD CONSTRAINT uq_history_reversal UNIQUE (reverses_entry_id);
ALTER TABLE history ADD CONSTRAINT ck_history_delta_amount CHECK (amount >= 0 AND delta <> 0);
ALTER TABLE children ADD CONSTRAINT ck_children_balance_nonnegative CHECK (balance >= 0);
