-- 服务器管理本地 SQLite  schema
-- 用户表：每条记录对应「一个用户 + 一台服务器」

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS servers (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL UNIQUE,   -- 服务器名称，如 srv1
    host          TEXT    NOT NULL,
    port          INTEGER NOT NULL DEFAULT 22,
    ops_user      TEXT    NOT NULL,          -- 运维账号
    password      TEXT,                      -- SSH 登录密码（可为空，走密钥）
    sudo_password TEXT,                      -- sudo 密码（可为空）
    created_at    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS users (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,            -- Linux 用户名
    server_name TEXT    NOT NULL,            -- 关联 servers.name
    ssh_key     TEXT    NOT NULL,            -- SSH 公钥（单行）
    state       TEXT    NOT NULL DEFAULT 'present'
                        CHECK (state IN ('present', 'absent')),
    created_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    UNIQUE (name, server_name),
    FOREIGN KEY (server_name) REFERENCES servers (name)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_users_name ON users (name);
CREATE INDEX IF NOT EXISTS idx_users_server ON users (server_name);
CREATE INDEX IF NOT EXISTS idx_users_state ON users (state);

CREATE TABLE IF NOT EXISTS groups (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL UNIQUE,
    created_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS user_groups (
    user_id  INTEGER NOT NULL,
    group_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, group_id),
    FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES groups (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_groups_user ON user_groups (user_id);
CREATE INDEX IF NOT EXISTS idx_user_groups_group ON user_groups (group_id);
