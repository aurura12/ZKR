CREATE TABLE IF NOT EXISTS user_status_log (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    action VARCHAR(20) NOT NULL CHECK (action IN ('CREATE', 'DEACTIVATE', 'ACTIVATE')),
    operator_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_status_log_user_id ON user_status_log(user_id);
CREATE INDEX IF NOT EXISTS idx_user_status_log_created_at ON user_status_log(created_at);
