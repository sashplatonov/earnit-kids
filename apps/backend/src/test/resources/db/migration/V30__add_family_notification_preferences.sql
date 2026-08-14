CREATE TABLE family_notification_preferences (
    id SERIAL PRIMARY KEY,
    family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    scope VARCHAR(16) NOT NULL,
    child_id INTEGER REFERENCES children(id) ON DELETE CASCADE,
    pref_key VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_family_notification_prefs
    ON family_notification_preferences(family_id, scope, child_id);

CREATE UNIQUE INDEX uq_family_notification_pref
    ON family_notification_preferences(family_id, scope, child_id, pref_key);
