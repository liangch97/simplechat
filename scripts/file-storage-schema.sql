-- 文件存储系统数据库表结构
-- 用于类网盘功能

USE `simplechat`;

-- 用户文件表
CREATE TABLE IF NOT EXISTS `user_files` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `file_name` VARCHAR(512) NOT NULL COMMENT '文件名',
  `file_path` VARCHAR(1024) NOT NULL COMMENT '文件存储路径',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `file_type` VARCHAR(128) DEFAULT NULL COMMENT '文件MIME类型',
  `file_extension` VARCHAR(32) DEFAULT NULL COMMENT '文件扩展名',
  `storage_path` VARCHAR(1024) NOT NULL COMMENT '用户分区路径',
  `is_public` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否公开（0私有，1公开）',
  `download_count` BIGINT NOT NULL DEFAULT 0 COMMENT '下载次数',
  `folder_path` VARCHAR(1024) DEFAULT '/' COMMENT '文件夹路径',
  `room_key` VARCHAR(128) NOT NULL DEFAULT 'public' COMMENT '所属房间',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_room_key` (`room_key`),
  INDEX `idx_folder_path` (`folder_path`(255)),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户文件表';

-- 用户存储配额表
CREATE TABLE IF NOT EXISTS `user_storage_quota` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL UNIQUE COMMENT '用户ID',
  `total_quota` BIGINT NOT NULL DEFAULT 1073741824 COMMENT '总配额（字节，默认1GB）',
  `used_space` BIGINT NOT NULL DEFAULT 0 COMMENT '已使用空间（字节）',
  `file_count` INT NOT NULL DEFAULT 0 COMMENT '文件数量',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户存储配额表';

-- 文件分享表
CREATE TABLE IF NOT EXISTS `file_shares` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `file_id` BIGINT NOT NULL COMMENT '文件ID',
  `share_code` VARCHAR(64) NOT NULL UNIQUE COMMENT '分享码',
  `password` VARCHAR(64) DEFAULT NULL COMMENT '提取密码',
  `expire_at` TIMESTAMP NULL DEFAULT NULL COMMENT '过期时间（NULL表示永久）',
  `download_limit` INT DEFAULT NULL COMMENT '下载次数限制（NULL表示无限制）',
  `download_count` INT NOT NULL DEFAULT 0 COMMENT '已下载次数',
  `created_by` BIGINT NOT NULL COMMENT '创建者用户ID',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_file_id` (`file_id`),
  INDEX `idx_share_code` (`share_code`),
  INDEX `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件分享表';

-- 文件夹表
CREATE TABLE IF NOT EXISTS `user_folders` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `folder_name` VARCHAR(512) NOT NULL COMMENT '文件夹名称',
  `folder_path` VARCHAR(1024) NOT NULL COMMENT '完整路径',
  `parent_path` VARCHAR(1024) DEFAULT '/' COMMENT '父级路径',
  `room_key` VARCHAR(128) NOT NULL DEFAULT 'public' COMMENT '所属房间',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_folder_path` (`folder_path`(255)),
  INDEX `idx_parent_path` (`parent_path`(255)),
  UNIQUE KEY `uk_folder_path_room` (`folder_path`(255), `room_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户文件夹表';
