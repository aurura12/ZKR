ALTER TABLE tencent_user_mapping ADD COLUMN can_create BOOLEAN NOT NULL DEFAULT FALSE;

-- 预标记 admin 的映射为可创建（后续管理员可以手动设置其他人）
UPDATE tencent_user_mapping SET can_create = TRUE WHERE erp_user_id = '000001';
