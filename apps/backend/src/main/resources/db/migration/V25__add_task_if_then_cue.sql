-- Optional private implementation-intention text for a task.
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS cue_when TEXT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS cue_action TEXT;
