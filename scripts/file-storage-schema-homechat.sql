-- 文件存储系统数据库表结构 (SQL Server 版 - homechat 数据库)
-- 用于类网盘功能

USE homechat;
GO

-- 用户文件表
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'user_files' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
  CREATE TABLE dbo.user_files (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_name NVARCHAR(512) NOT NULL,
    file_path NVARCHAR(1024) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    file_type NVARCHAR(128) NULL,
    file_extension NVARCHAR(32) NULL,
    storage_path NVARCHAR(1024) NOT NULL,
    is_public BIT NOT NULL DEFAULT 0,
    download_count BIGINT NOT NULL DEFAULT 0,
    folder_path NVARCHAR(1024) DEFAULT '/',
    room_key NVARCHAR(128) NOT NULL DEFAULT 'public',
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
  );
  CREATE INDEX idx_user_files_user_id ON dbo.user_files(user_id);
  CREATE INDEX idx_user_files_room_key ON dbo.user_files(room_key);
  CREATE INDEX idx_user_files_folder_path ON dbo.user_files(folder_path);
  CREATE INDEX idx_user_files_created_at ON dbo.user_files(created_at);
END
GO

-- 用户存储配额表
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'user_storage_quota' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
  CREATE TABLE dbo.user_storage_quota (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    total_quota BIGINT NOT NULL DEFAULT 1073741824,
    used_space BIGINT NOT NULL DEFAULT 0,
    file_count INT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
  );
  CREATE INDEX idx_user_storage_quota_user_id ON dbo.user_storage_quota(user_id);
END
GO

-- 文件分享表
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'file_shares' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
  CREATE TABLE dbo.file_shares (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    file_id BIGINT NOT NULL,
    share_code NVARCHAR(64) NOT NULL UNIQUE,
    password NVARCHAR(64) NULL,
    expire_at DATETIME2 NULL,
    download_limit INT NULL,
    download_count INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
  );
  CREATE INDEX idx_file_shares_file_id ON dbo.file_shares(file_id);
  CREATE INDEX idx_file_shares_share_code ON dbo.file_shares(share_code);
  CREATE INDEX idx_file_shares_created_by ON dbo.file_shares(created_by);
END
GO

-- 文件夹表
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'user_folders' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
  CREATE TABLE dbo.user_folders (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    folder_name NVARCHAR(512) NOT NULL,
    folder_path NVARCHAR(900) NOT NULL,
    parent_path NVARCHAR(900) DEFAULT '/',
    room_key NVARCHAR(128) NOT NULL DEFAULT 'public',
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uk_folder_path_room_homechat UNIQUE (folder_path, room_key)
  );
  CREATE INDEX idx_user_folders_user_id ON dbo.user_folders(user_id);
  CREATE INDEX idx_user_folders_folder_path ON dbo.user_folders(folder_path);
  CREATE INDEX idx_user_folders_parent_path ON dbo.user_folders(parent_path);
END
GO

PRINT 'File storage tables created successfully in homechat database!';
GO
