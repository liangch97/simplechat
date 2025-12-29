-- 迁移脚本：修改 user_folders 表的唯一约束
-- 从 (user_id, folder_path) 改为 (folder_path, room_key)
-- 这样同一房间内的用户可以共享文件夹

-- SQL Server 版本
-- 1. 先删除旧约束
IF EXISTS (SELECT * FROM sys.objects WHERE name = 'uk_user_folder_homechat' AND type = 'UQ')
BEGIN
    ALTER TABLE dbo.user_folders DROP CONSTRAINT uk_user_folder_homechat;
END
GO

IF EXISTS (SELECT * FROM sys.objects WHERE name = 'uk_user_folder' AND type = 'UQ')
BEGIN
    ALTER TABLE dbo.user_folders DROP CONSTRAINT uk_user_folder;
END
GO

-- 2. 添加新约束
IF NOT EXISTS (SELECT * FROM sys.objects WHERE name = 'uk_folder_path_room' AND type = 'UQ')
BEGIN
    ALTER TABLE dbo.user_folders ADD CONSTRAINT uk_folder_path_room UNIQUE (folder_path, room_key);
END
GO

PRINT 'Constraint migration completed!';
GO
