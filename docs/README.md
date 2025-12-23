# SYSU Chat - 中山大学在线聊天平台

> 域名: **sysu.asia** (待启用)

一个简洁、高效的在线聊天平台，专为中山大学学生打造。

## 🚀 快速开始

### 本地开发

1. **启动服务器**

   Windows:
   ```bash
   start.bat
   # 或
   scripts\start-server.bat
   ```

   Linux/Mac:
   ```bash
   chmod +x start.sh scripts/start-server.sh
   ./start.sh
   ```

2. **访问网站**
   
   打开浏览器访问: http://localhost:7070

### 自定义端口

```bash
# Windows
start.bat 8080

# Linux/Mac
./start.sh 8080
```

## 📁 项目结构

```
simplechat/
├── src/                       # Java 源代码
│   ├── WebChatServer.java     # 完整的 HTTP 服务器 (推荐使用)
│   ├── HttpChatServer.java    # 原始 SSE 聊天服务器
│   ├── ChatServer.java        # Socket 聊天服务器
│   └── ChatClient.java        # 命令行客户端
├── web/                       # Web 前端文件
│   ├── index.html             # 主页面
│   ├── favicon.svg            # 网站图标
│   ├── css/
│   │   └── style.css          # 样式表
│   ├── js/
│   │   ├── config.js          # 配置文件
│   │   └── chat.js            # 聊天逻辑
│   └── images/
│       └── logo.svg           # Logo
├── scripts/                   # 启动脚本
│   ├── start-server.bat       # Windows 启动脚本
│   └── start-server.sh        # Linux 启动脚本
├── docs/                      # 文档
│   └── README.md              # 本文档
├── out/                       # 编译输出 (自动生成)
├── start.bat                  # Windows 快捷启动
└── start.sh                   # Linux 快捷启动
```

## 🌟 功能特性

- ✅ 实时消息推送 (SSE)
- ✅ 响应式设计，支持移动端
- ✅ 深色/浅色主题自动适配
- ✅ 昵称本地保存
- ✅ 断线自动重连
- ✅ 在线人数显示
- ✅ 消息时间戳

## 🔧 API 接口

### SSE 事件流
```
GET /events
```
返回服务器推送事件 (Server-Sent Events)

### 发送消息
```
POST /send
Content-Type: application/json

{
    "name": "昵称",
    "message": "消息内容"
}
```

### 服务器状态
```
GET /api/status
```
返回:
```json
{
    "online": 5,
    "totalMessages": 123,
    "uptime": "01:30:45",
    "startTime": "2025-12-16T10:00:00"
}
```

## 🌐 生产环境部署

### 1. 准备服务器

建议配置:
- 操作系统: Ubuntu 22.04 LTS
- 内存: 1GB+
- JDK: 11 或更高版本

### 2. 安装 Java

```bash
sudo apt update
sudo apt install openjdk-17-jdk -y
java -version
```

### 3. 上传文件

```bash
# 使用 SCP 上传项目
scp -r simplechat/ user@your-server:/home/user/
```

### 4. 配置 Nginx 反向代理

```nginx
# /etc/nginx/sites-available/sysu.asia
server {
    listen 80;
    server_name sysu.asia www.sysu.asia;
    
    # 重定向到 HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name sysu.asia www.sysu.asia;
    
    # SSL 证书 (使用 Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/sysu.asia/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/sysu.asia/privkey.pem;
    
    # 反向代理到 Java 服务器
    location / {
        proxy_pass http://127.0.0.1:7070;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    # SSE 特殊配置
    location /events {
        proxy_pass http://127.0.0.1:7070/events;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding off;
    }
}
```

### 5. 申请 SSL 证书

```bash
sudo apt install certbot python3-certbot-nginx -y
sudo certbot --nginx -d sysu.asia -d www.sysu.asia
```

### 6. 使用 Systemd 管理服务

创建服务文件 `/etc/systemd/system/sysuchat.service`:

```ini
[Unit]
Description=SYSU Chat Server
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/home/user/simplechat
ExecStart=/usr/bin/java WebChatServer 7070
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务:

```bash
sudo systemctl daemon-reload
sudo systemctl enable sysuchat
sudo systemctl start sysuchat
sudo systemctl status sysuchat
```

### 7. 配置域名 DNS

在你的域名注册商处添加 DNS 记录:

| 类型 | 主机 | 值 |
|------|------|-----|
| A | @ | 你的服务器IP |
| A | www | 你的服务器IP |

## 📝 配置说明

编辑 `web/js/config.js` 可修改以下配置:

```javascript
const CONFIG = {
    api: {
        production: {
            baseUrl: 'https://sysu.asia:7070',  // 生产环境 API 地址
        }
    },
    chat: {
        maxMessageLength: 500,     // 最大消息长度
        reconnectDelay: 3000,      // 重连延迟 (毫秒)
        maxReconnectAttempts: 5,   // 最大重连次数
    }
};
```

## 🎨 主题定制

编辑 `web/css/style.css` 中的 CSS 变量:

```css
:root {
    --primary-color: #006633;  /* 主色调 - 中大绿 */
    --accent-color: #ffc107;   /* 强调色 */
    /* ... */
}
```

## 🔒 安全建议

1. **生产环境务必使用 HTTPS**
2. 配置防火墙只开放 80/443 端口
3. 定期更新系统和 Java 版本
4. 考虑添加速率限制防止刷屏
5. 添加敏感词过滤功能

## 📄 许可证

MIT License

---

**SYSU Chat** © 2025 | 域名: sysu.asia
