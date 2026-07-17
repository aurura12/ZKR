CREATE TABLE IF NOT EXISTS work_order (
    id              BIGSERIAL       PRIMARY KEY,
    project_id      VARCHAR(64)     NOT NULL,
    creator_id      VARCHAR(64)     NOT NULL,
    assignee_id     VARCHAR(64)     NOT NULL,
    creator_name    VARCHAR(120),
    assignee_name   VARCHAR(120),
    title           VARCHAR(200)    NOT NULL,
    description     TEXT,
    expected_output TEXT,
    deadline        TIMESTAMPTZ,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_work_order_project ON work_order(project_id);
CREATE INDEX idx_work_order_assignee ON work_order(assignee_id);
CREATE INDEX idx_work_order_status ON work_order(status);
