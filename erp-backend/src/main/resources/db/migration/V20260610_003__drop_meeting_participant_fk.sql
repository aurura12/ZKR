-- 参会人表的 user_id 不再强制关联 sys_user
-- 因为参会人可以是腾讯会议中的任意用户，不一定有 ERP 账号

ALTER TABLE meeting_participant DROP CONSTRAINT IF EXISTS meeting_participant_user_id_fkey;
