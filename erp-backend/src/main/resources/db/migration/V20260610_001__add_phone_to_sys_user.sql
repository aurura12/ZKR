-- 给 sys_user 表增加手机号字段，用于腾讯会议用户匹配
-- 同时从钉钉目录表回填已有的手机号

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS phone VARCHAR(32);

-- 从钉钉目录表回填已有手机号（如果钉钉有数据的话）
UPDATE sys_user u
SET phone = d.mobile
FROM dingtalk_user_directory d
WHERE u.user_id = d.user_id
  AND d.mobile IS NOT NULL
  AND u.phone IS NULL;
