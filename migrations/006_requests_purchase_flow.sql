-- Migration: support purchase approval flow in requests
-- Created: 2026-02-18

ALTER TABLE requests ADD COLUMN IF NOT EXISTS request_type VARCHAR(50) DEFAULT 'earn';
ALTER TABLE requests ADD COLUMN IF NOT EXISTS item_id BIGINT;
ALTER TABLE requests ADD COLUMN IF NOT EXISTS money_amount INTEGER DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_requests_request_type ON requests(request_type);
CREATE INDEX IF NOT EXISTS idx_requests_item_id ON requests(item_id);
