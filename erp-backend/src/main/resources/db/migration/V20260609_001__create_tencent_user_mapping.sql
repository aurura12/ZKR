-- 腾讯会议集成：ERP用户ID与腾讯会议用户ID映射表
-- 用于将ERP系统内部的userId转换为腾讯会议API所需的userid
-- phone 字段用于通过手机号匹配钉钉目录实现自动绑定

CREATE TABLE IF NOT EXISTS tencent_user_mapping (
    erp_user_id VARCHAR(64) PRIMARY KEY REFERENCES sys_user(user_id),
    tencent_user_id VARCHAR(128) NOT NULL,
    tencent_username VARCHAR(128),
    phone VARCHAR(32),
    remark VARCHAR(200),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 插入 admin 的映射（方式一：手动种子数据）
INSERT INTO tencent_user_mapping (erp_user_id, tencent_user_id, remark)
VALUES ('000001', 'wemeeting8151462', 'admin的腾讯会议账号')
ON CONFLICT (erp_user_id) DO NOTHING;
