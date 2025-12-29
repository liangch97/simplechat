# 文件管理系统 - 类网盘功能

## 概述

这是一个为SimpleChat系统添加的完整文件管理功能（类网盘），为每个用户提供独立的存储分区，支持文件上传、下载、删除、搜索等功能。

## 功能特性

### 核心功能
- ✅ **用户独立分区** - 每个用户拥有独立的存储空间
- ✅ **存储配额管理** - 默认1GB存储空间，可自定义
- ✅ **文件上传** - 支持拖放上传和多文件上传
- ✅ **文件下载** - 安全的文件下载功能
- ✅ **文件删除** - 删除文件并自动释放配额
- ✅ **文件搜索** - 按文件名搜索
- ✅ **文件排序** - 支持按名称、日期、大小、类型排序
- ✅ **权限控制** - 用户只能访问自己的文件
- ✅ **房间隔离** - 不同房间的文件完全隔离

### 数据库表结构
- `user_files` - 文件元信息表
- `user_storage_quota` - 用户存储配额表
- `file_shares` - 文件分享表（预留）
- `user_folders` - 文件夹表（预留）

## 安装步骤

### 1. 创建数据库表

执行SQL脚本创建必要的数据库表：

```bash
mysql -u your_user -p simplechat < scripts/file-storage-schema.sql
```

或在MySQL客户端中执行 `scripts/file-storage-schema.sql` 中的SQL语句。

### 2. 编译Java代码

确保所有新添加的Java类都已编译：

```bash
cd simplechat
rm -rf out && mkdir -p out
javac -encoding UTF-8 -sourcepath src -d out -cp "lib/*" src/*.java src/db/*.java src/util/*.java
```

### 3. 创建存储目录

确保存储目录存在并有正确的权限：

```bash
mkdir -p storage
chmod 755 storage
```

### 4. 启动服务器

使用现有的启动脚本：

```bash
# Windows
start.bat

# Linux/Mac
./start.sh
```

## 使用说明

### 访问文件管理器

1. 登录聊天系统
2. 访问 `http://localhost:8080/files.html`
3. 或在聊天界面添加文件管理入口链接

### API端点

#### 1. 上传文件
```
POST /api/files/upload?roomKey={roomKey}&folder={folderPath}
Headers:
  Authorization: Bearer {token}
  Content-Type: {file-mime-type}
  X-File-Name: {filename}
Body: 文件二进制数据
```

#### 2. 获取文件列表
```
GET /api/files/list?roomKey={roomKey}&folder={folderPath}
Headers:
  Authorization: Bearer {token}
```

#### 3. 下载文件
```
GET /api/files/download?roomKey={roomKey}&fileId={fileId}
Headers:
  Authorization: Bearer {token}
```

#### 4. 删除文件
```
POST /api/files/delete
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "roomKey": "xxx",
  "fileId": 123
}
```

#### 5. 搜索文件
```
GET /api/files/search?roomKey={roomKey}&keyword={keyword}
Headers:
  Authorization: Bearer {token}
```

#### 6. 获取存储配额
```
GET /api/files/quota?roomKey={roomKey}
Headers:
  Authorization: Bearer {token}
```

## 存储结构

文件按以下结构存储在服务器上：

```
storage/
├── room_{roomKey}/
│   ├── user_{userId}/
│   │   ├── {timestamp}_{filename}
│   │   ├── subfolder/
│   │   │   └── {timestamp}_{filename}
│   │   └── ...
│   └── user_{userId2}/
│       └── ...
└── room_{roomKey2}/
    └── ...
```

## 配置说明

### 修改默认配额

在 `FileManager.java` 中修改：
```java
private static final long DEFAULT_QUOTA = 1073741824L;  // 1GB
```

或在数据库中修改特定用户的配额：
```sql
UPDATE user_storage_quota 
SET total_quota = 2147483648  -- 2GB
WHERE user_id = 123;
```

### 修改存储路径

在 `FileManager.java` 中修改：
```java
private static final String BASE_STORAGE_PATH = "storage";
```

## 安全考虑

1. **路径遍历防护** - 自动过滤 `..` 等危险字符
2. **权限验证** - 每次操作都验证用户token
3. **文件隔离** - 每个用户独立目录，无法访问他人文件
4. **房间隔离** - 不同房间的文件完全隔离
5. **配额限制** - 防止存储空间被滥用

## 扩展功能（TODO）

- [ ] 文件夹管理
- [ ] 文件分享功能
- [ ] 缩略图生成
- [ ] 文件版本控制
- [ ] 回收站功能
- [ ] 批量操作
- [ ] 文件预览
- [ ] 视频/音频流式播放

## 故障排除

### 文件上传失败
- 检查存储目录权限
- 检查存储配额是否已满
- 检查数据库连接

### 文件下载失败
- 检查文件物理路径是否存在
- 检查用户权限
- 检查数据库中的文件记录

### 配额显示不正确
- 执行以下SQL同步配额：
```sql
UPDATE user_storage_quota uq
SET used_space = (
    SELECT COALESCE(SUM(file_size), 0) 
    FROM user_files uf 
    WHERE uf.user_id = uq.user_id
),
file_count = (
    SELECT COUNT(*) 
    FROM user_files uf 
    WHERE uf.user_id = uq.user_id
)
WHERE user_id = {userId};
```

## 技术栈

- **后端**: Java (JDK 11+)
- **数据库**: MySQL 5.7+
- **前端**: HTML5 + CSS3 + Vanilla JavaScript
- **图标**: Lucide Icons

## 许可证

与SimpleChat项目保持一致

## 作者

SimpleChat Team

## 更新日志

### v1.0.0 (2025-12-25)
- 初始版本
- 基础文件管理功能
- 用户独立分区
- 存储配额管理
