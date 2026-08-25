ALTER TABLE families ADD COLUMN IF NOT EXISTS locale VARCHAR(8);

ALTER TABLE families ADD CONSTRAINT ck_families_locale
    CHECK (locale IS NULL OR locale IN ('en', 'ru'));
