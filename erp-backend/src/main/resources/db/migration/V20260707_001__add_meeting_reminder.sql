ALTER TABLE meeting_record ADD COLUMN IF NOT EXISTS last_reminded_at TIMESTAMPTZ;
