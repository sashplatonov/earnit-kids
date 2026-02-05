-- Migration: Add daily coin limit to children
-- Created: 2026-02-05

ALTER TABLE children ADD COLUMN daily_coin_limit INTEGER DEFAULT 0;
