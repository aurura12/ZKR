-- 腾讯会议集成：创建会议相关表

-- 会议记录表
CREATE TABLE IF NOT EXISTS meeting_record (
    id SERIAL PRIMARY KEY,
    meeting_id VARCHAR(64) UNIQUE NOT NULL,
    topic VARCHAR(200) NOT NULL,
    description TEXT,
    start_time TIMESTAMP NOT NULL,
    duration INTEGER,
    join_url TEXT,
    password VARCHAR(32),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    creator_id VARCHAR(64) REFERENCES sys_user(user_id),
    project_id VARCHAR(64) REFERENCES sys_project(project_id),
    recording_url TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 参会人表
CREATE TABLE IF NOT EXISTS meeting_participant (
    id SERIAL PRIMARY KEY,
    meeting_record_id INTEGER REFERENCES meeting_record(id) ON DELETE CASCADE,
    user_id VARCHAR(64) REFERENCES sys_user(user_id),
    attend_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    joined_at TIMESTAMP,
    left_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(meeting_record_id, user_id)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_meeting_record_creator ON meeting_record(creator_id);
CREATE INDEX IF NOT EXISTS idx_meeting_record_project ON meeting_record(project_id);
CREATE INDEX IF NOT EXISTS idx_meeting_record_status ON meeting_record(status);
CREATE INDEX IF NOT EXISTS idx_meeting_record_start_time ON meeting_record(start_time);
CREATE INDEX IF NOT EXISTS idx_meeting_participant_meeting ON meeting_participant(meeting_record_id);
CREATE INDEX IF NOT EXISTS idx_meeting_participant_user ON meeting_participant(user_id);
