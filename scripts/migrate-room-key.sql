-- 聊天记录迁移脚本
-- 将原有的 room_key='public' 消息更新为正确的秘钥
-- 
-- 说明：
-- - simplechat 数据库对应秘钥 24336064
-- - homechat 数据库对应秘钥 061318

-- ===========================================
-- 对于 simplechat 数据库执行：
-- ===========================================
-- MySQL:
-- USE simplechat;
-- UPDATE messages SET room_key = '24336064' WHERE room_key = 'public';

-- SQL Server:
-- USE simplechat;
-- UPDATE messages SET room_key = '24336064' WHERE room_key = 'public';

-- ===========================================
-- 对于 homechat 数据库执行：
-- ===========================================
-- MySQL:
-- USE homechat;
-- UPDATE messages SET room_key = '061318' WHERE room_key = 'public';

-- SQL Server:
-- USE homechat;
-- UPDATE messages SET room_key = '061318' WHERE room_key = 'public';

-- ===========================================
-- 同样需要更新 users 表中没有 room_key 的用户
-- ===========================================
-- simplechat 数据库：
-- UPDATE users SET room_key = '24336064' WHERE room_key = '' OR room_key IS NULL;

-- homechat 数据库：
-- UPDATE users SET room_key = '061318' WHERE room_key = '' OR room_key IS NULL;
