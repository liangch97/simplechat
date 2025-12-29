# 文件管理系统 - 快速开始指南

## 🚀 快速开始

### 第一步：初始化数据库

在MySQL中执行以下命令创建文件管理所需的表：

```bash
mysql -u root -p simplechat < scripts/file-storage-schema.sql
```

或手动执行 `scripts/file-storage-schema.sql` 文件中的SQL语句。

### 第二步：重新编译项目（如果需要）

如果你修改了Java代码，需要重新编译：

```bash
cd simplechat
# 删除旧的class文件
rm -rf out/

# 重新编译
javac -encoding UTF-8 -sourcepath src -d out -cp "lib/*" src/app/*.java src/db/*.java src/util/*.java
```

**Windows用户**：
```cmd
cd simplechat
rmdir /s /q out
mkdir out
javac -encoding UTF-8 -sourcepath src -d out -cp "lib\*" src\app\*.java src\db\*.java src\util\*.java
```

### 第三步：启动服务器

```bash
# Windows
start.bat

# Linux/Mac
./start.sh
```

### 第四步：访问文件管理器

1. 打开浏览器访问：`http://localhost:8080/`
2. 登录你的账号
3. 访问文件管理器：`http://localhost:8080/files.html`

## 📁 功能演示

### 上传文件

1. 点击右上角的"上传文件"按钮
2. 选择要上传的文件，或直接拖放文件到上传区域
3. 等待上传完成

### 下载文件

- **方法1**：双击文件图标
- **方法2**：右键点击文件 → 选择"下载"

### 删除文件

1. 右键点击文件
2. 选择"删除"
3. 确认删除操作

### 搜索文件

在右上角的搜索框中输入文件名关键词，实时搜索。

### 查看存储空间

右上角显示当前存储使用情况和配额。

## 🎯 使用技巧

### 1. 多文件上传
可以一次选择多个文件进行上传，系统会自动排队处理。

### 2. 拖放上传
直接将文件从资源管理器拖放到上传区域，更快捷！

### 3. 文件排序
使用工具栏中的排序选择器，可以按名称、日期、大小、类型排序。

### 4. 右键菜单
右键点击文件可以快速访问常用操作：
- 下载
- 重命名（开发中）
- 详细信息
- 删除

## ⚙️ 配置

### 修改存储配额

默认每个用户1GB存储空间。修改方法：

**方法1：修改源码**
编辑 `src/util/FileManager.java`：
```java
private static final long DEFAULT_QUOTA = 2147483648L;  // 改为2GB
```

**方法2：修改数据库**
```sql
-- 为特定用户设置5GB配额
UPDATE user_storage_quota 
SET total_quota = 5368709120 
WHERE user_id = 123;

-- 为所有用户设置2GB配额
UPDATE user_storage_quota 
SET total_quota = 2147483648;
```

### 修改存储路径

编辑 `src/util/FileManager.java`：
```java
private static final String BASE_STORAGE_PATH = "/data/files";  // 自定义路径
```

确保该目录存在且有写权限：
```bash
mkdir -p /data/files
chmod 755 /data/files
```

## 🔒 安全说明

### 访问控制
- 所有API都需要Bearer Token验证
- 用户只能访问自己上传的文件
- 不同房间的文件完全隔离

### 文件存储
- 文件按用户和房间分区存储
- 自动防止路径遍历攻击
- 文件名自动添加时间戳防止冲突

### 配额管理
- 上传前检查配额
- 删除文件自动释放空间
- 实时更新使用情况

## 🐛 常见问题

### Q: 上传文件失败怎么办？
A: 检查以下几点：
1. 是否已登录（需要token）
2. 存储空间是否已满
3. 服务器 `storage` 目录是否有写权限
4. 数据库连接是否正常

### Q: 文件上传后看不到？
A: 尝试以下操作：
1. 点击刷新按钮
2. 检查是否在正确的文件夹路径
3. 查看浏览器控制台是否有错误

### Q: 配额显示不准确？
A: 执行以下SQL修复：
```sql
UPDATE user_storage_quota uq
SET used_space = (
    SELECT COALESCE(SUM(file_size), 0) 
    FROM user_files uf 
    WHERE uf.user_id = uq.user_id AND uf.room_key = 'YOUR_ROOM_KEY'
),
file_count = (
    SELECT COUNT(*) 
    FROM user_files uf 
    WHERE uf.user_id = uq.user_id AND uf.room_key = 'YOUR_ROOM_KEY'
)
WHERE user_id = YOUR_USER_ID;
```

### Q: 删除文件后仍占用空间？
A: 检查：
1. 物理文件是否真的被删除（查看storage目录）
2. 数据库中的配额是否更新
3. 刷新页面查看最新配额

## 📊 存储目录结构

```
simplechat/
├── storage/                    # 文件存储根目录
│   ├── room_24336064/         # 房间分区
│   │   ├── user_1/            # 用户1的文件
│   │   │   ├── 1703500000000_photo.jpg
│   │   │   ├── 1703500001000_document.pdf
│   │   │   └── ...
│   │   ├── user_2/            # 用户2的文件
│   │   │   └── ...
│   │   └── ...
│   └── room_061318/           # 另一个房间
│       └── ...
├── web/
│   ├── files.html             # 文件管理器页面
│   ├── css/
│   │   └── files.css          # 样式文件
│   └── js/
│       └── files.js           # JavaScript逻辑
├── src/
│   ├── db/
│   │   └── FileDao.java       # 文件数据访问
│   ├── util/
│   │   └── FileManager.java   # 文件管理服务
│   └── WebChatServer.java     # 包含文件API
└── scripts/
    └── file-storage-schema.sql # 数据库表结构
```

## 🎨 界面功能说明

### 顶部工具栏
- **存储进度条**：显示空间使用百分比
- **刷新按钮**：重新加载文件列表
- **上传按钮**：打开上传对话框

### 文件列表
- **单击**：选中文件
- **双击**：下载文件
- **右键**：显示操作菜单

### 工具栏
- **新建文件夹**：创建新文件夹（开发中）
- **搜索框**：实时搜索文件
- **排序选择器**：改变文件排序方式

## 📝 API集成示例

### JavaScript示例

```javascript
// 获取token
const token = localStorage.getItem('token');
const roomKey = '24336064';

// 上传文件
async function uploadFile(file) {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await fetch(
        `/api/files/upload?roomKey=${roomKey}&folder=/`,
        {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'X-File-Name': file.name
            },
            body: await file.arrayBuffer()
        }
    );
    
    return await response.json();
}

// 获取文件列表
async function getFiles() {
    const response = await fetch(
        `/api/files/list?roomKey=${roomKey}&folder=/`,
        {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        }
    );
    
    return await response.json();
}

// 下载文件
async function downloadFile(fileId) {
    const response = await fetch(
        `/api/files/download?roomKey=${roomKey}&fileId=${fileId}`,
        {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        }
    );
    
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'filename.ext';
    a.click();
}
```

## 🚧 路线图

即将推出的功能：

- [ ] 文件夹创建和管理
- [ ] 文件重命名
- [ ] 文件移动/复制
- [ ] 文件分享（生成分享链接）
- [ ] 图片缩略图预览
- [ ] 视频在线播放
- [ ] 批量操作（批量下载、删除）
- [ ] 文件上传进度显示
- [ ] 回收站功能
- [ ] 文件版本历史

## 💡 提示

1. **定期备份**：建议定期备份 `storage` 目录和数据库
2. **监控存储**：定期检查存储使用情况，避免磁盘空间不足
3. **性能优化**：大文件建议使用分片上传（后续版本支持）
4. **安全更新**：保持系统和依赖库更新到最新版本

## 📞 支持

如有问题或建议，请联系开发团队或提交Issue。

---

**享受你的云盘体验！** 🎉
