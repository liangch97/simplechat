-- 为 simplechat 数据库的用户授权并添加 room_key 列
-- 使用 sa 或管理员账号执行此脚本

-- ========== simplechat 数据库 ==========
USE simplechat;
GO

-- 授予 simplechat_user ALTER 权限
GRANT ALTER ON dbo.users TO simplechat_user;
GO

-- 添加 room_key 列
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'users' AND COLUMN_NAME = 'room_key')
BEGIN
    ALTER TABLE dbo.users ADD room_key NVARCHAR(128) NULL DEFAULT '';
    PRINT 'simplechat: room_key 列已添加';
END
ELSE
BEGIN
    PRINT 'simplechat: room_key 列已存在';
END
GO

-- 更新现有用户的 room_key
UPDATE dbo.users SET room_key = '24336064' WHERE room_key = '' OR room_key IS NULL;
GO

-- ========== homechat 数据库 ==========
USE homechat;
GO

-- 授予 simplechat_user ALTER 权限 (如果使用同一用户)
GRANT ALTER ON dbo.users TO simplechat_user;
GO

-- 添加 room_key 列
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'users' AND COLUMN_NAME = 'room_key')
BEGIN
    ALTER TABLE dbo.users ADD room_key NVARCHAR(128) NULL DEFAULT '';
    PRINT 'homechat: room_key 列已添加';
END
ELSE
BEGIN
    PRINT 'homechat: room_key 列已存在';
END
GO

-- 更新现有用户的 room_key
UPDATE dbo.users SET room_key = '061318' WHERE room_key = '' OR room_key IS NULL;
GO

PRINT '完成！';
