-- 为 messages 表添加 room_key 列并迁移数据
-- 使用 Windows 集成身份验证执行：
-- sqlcmd -S localhost -E -C -i "d:\simplechat\simplechat\scripts\add-room-key-to-messages.sql"

-- ========== simplechat 数据库 ==========
USE simplechat;
GO

-- 添加 room_key 列到 messages 表
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'messages' AND COLUMN_NAME = 'room_key')
BEGIN
    ALTER TABLE dbo.messages ADD room_key NVARCHAR(128) NULL DEFAULT '';
    PRINT 'simplechat.messages: room_key column added';
END
ELSE
BEGIN
    PRINT 'simplechat.messages: room_key column already exists';
END
GO

-- 将所有消息迁移到 24336064 房间
UPDATE dbo.messages SET room_key = '24336064' WHERE room_key = '' OR room_key IS NULL OR room_key = 'public';
PRINT 'simplechat.messages: migrated to room_key 24336064';
GO

-- ========== homechat 数据库 ==========
USE homechat;
GO

-- 添加 room_key 列到 messages 表
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'messages' AND COLUMN_NAME = 'room_key')
BEGIN
    ALTER TABLE dbo.messages ADD room_key NVARCHAR(128) NULL DEFAULT '';
    PRINT 'homechat.messages: room_key column added';
END
ELSE
BEGIN
    PRINT 'homechat.messages: room_key column already exists';
END
GO

-- 将所有消息迁移到 061318 房间
UPDATE dbo.messages SET room_key = '061318' WHERE room_key = '' OR room_key IS NULL OR room_key = 'public';
PRINT 'homechat.messages: migrated to room_key 061318';
GO

PRINT 'Done!';
