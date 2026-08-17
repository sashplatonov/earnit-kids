ALTER TABLE telegram_deliveries ADD COLUMN request_id BIGINT;
CREATE INDEX idx_telegram_delivery_request ON telegram_deliveries(request_id, status);
