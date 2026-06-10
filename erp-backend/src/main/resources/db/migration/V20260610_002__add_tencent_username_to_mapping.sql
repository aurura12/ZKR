-- 在映射表中增加腾讯会议用户名，供参会人选单显示

ALTER TABLE tencent_user_mapping ADD COLUMN IF NOT EXISTS tencent_username VARCHAR(128);
