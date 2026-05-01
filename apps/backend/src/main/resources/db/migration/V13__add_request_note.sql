-- Add optional child note to requests.
ALTER TABLE requests
    ADD COLUMN IF NOT EXISTS note VARCHAR(120);

-- Optional: speed up filtering/searching by note (not strictly necessary)
-- CREATE INDEX IF NOT EXISTS idx_requests_note ON requests(note);
