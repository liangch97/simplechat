
-- SimpleChat SQL Server schema (SSMS friendly)

IF DB_ID(N'simplechat') IS NULL
BEGIN
  CREATE DATABASE simplechat;
END
GO

USE simplechat;
GO

-- 用户表
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
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
  );
END
GO