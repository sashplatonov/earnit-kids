ALTER TABLE application_outbox_events ADD COLUMN resolution_status VARCHAR(32);
ALTER TABLE application_outbox_events ADD COLUMN resolution_title VARCHAR(255);
