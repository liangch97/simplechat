-- SimpleChat SQL Server schema for homechat database (秘钥: 061318)
-- 在 SSMS 中执行此脚本创建 homechat 数据库

IF DB_ID(N'homechat') IS NULL
BEGIN
  CREATE DATABASE homechat;
END
GO

USE homechat;
GO

-- 用户表（可选，如果需要独立用户系统）
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'users' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
  CREATE TABLE dbo.users (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    username NVARCHAR(64) NOT NULL UNIQUE,
    password_hash NVARCHAR(128) NOT NULL,
    nickname NVARCHAR(64) NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    last_login DATETIME2 NULL
  );
  CREATE INDEX idx_users_username ON dbo.users(username);
END
GO

-- 消息表
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'messages' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
  CREATE TABLE dbo.messages (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    nickname NVARCHAR(64) NOT NULL,
    content NVARCHAR(MAX) NOT NULL,
    room_key NVARCHAR(128) NOT NULL DEFAULT '061318',
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
  );
  CREATE INDEX idx_messages_room_key ON dbo.messages(room_key);
  CREATE INDEX idx_messages_created_at ON dbo.messages(created_at);
END
GO

PRINT 'homechat 数据库初始化完成！';
PRINT '房间秘钥: 061318';
GO
