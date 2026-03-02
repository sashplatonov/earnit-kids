-- Migration 014: Add user preferences columns and password reset token support
-- Created: 2026-03-02

-- Remember which child the admin last viewed
ALTER TABLE families
  ADD COLUMN IF NOT EXISTS last_selected_child_id INTEGER REFERENCES children(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS reset_token VARCHAR(64),
  ADD COLUMN IF NOT EXISTS reset_token_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_families_reset_token
  ON families(reset_token) WHERE reset_token IS NOT NULL;

-- Theme preference per child (one of: mint, ocean, sun, coral, cosmos)
ALTER TABLE children
  ADD COLUMN IF NOT EXISTS theme VARCHAR(10) DEFAULT 'ocean'
    CHECK (theme IN ('mint', 'ocean', 'sun', 'coral', 'cosmos'));
