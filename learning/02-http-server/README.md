# 模块 2: HTTP 服务器原理

## 🎯 学习目标

- 理解 HTTP 协议的基本结构
- 学会用 Java 手写一个简易 HTTP 服务器
- 掌握 `com.sun.net.httpserver` 的使用

## 📖 核心概念

### HTTP 协议简介

HTTP（超文本传输协议）是 Web 的基础。浏览器和服务器之间就是用 HTTP 通讯。

```
浏览器                                    服务器
   │                                        │
   │  ─────── HTTP 请求 (Request) ────────► │
   │    GET /index.html HTTP/1.1            │
   │    Host: localhost                     │
   │                                        │
   │  ◄────── HTTP 响应 (Response) ──────── │
   │    HTTP/1.1 200 OK                     │
   │    Content-Type: text/html             │
   │    <html>...</html>                    │
   │                                        │
```

### HTTP 请求格式

```
GET /path HTTP/1.1          ← 请求行（方法 路径 版本）
Host: localhost:8080        ← 请求头
User-Agent: Chrome/120      ← 请求头
Accept: text/html           ← 请求头
                            ← 空行
(请求体，GET通常没有)        ← 请求体
```

### HTTP 响应格式

```
HTTP/1.1 200 OK             ← 状态行（版本 状态码 描述）
Content-Type: text/html     ← 响应头
Content-Length: 1234        ← 响应头
                            ← 空行
<html>...</html>            ← 响应体
```

### 常见 HTTP 状态码

| 状态码 | 含义 |
|--------|------|
| 200 | OK - 成功 |
| 301 | 永久重定向 |
| 400 | Bad Request - 请求错误 |
| 404 | Not Found - 页面不存在 |
| 500 | Internal Server Error - 服务器内部错误 |

### 常见 HTTP 方法

| 方法 | 用途 |
|------|------|
| GET | 获取资源（打开网页） |
| POST | 提交数据（表单、登录） |
| PUT | 更新资源 |
| DELETE | 删除资源 |

## 📁 本模块文件

```
02-http-server/
├── README.md                    # 本文档
├── examples/
│   ├── RawHttpServer.java       # 手写 HTTP 服务器（理解原理）
│   ├── SimpleHttpServer.java    # 使用 HttpServer API
│   └── StaticFileServer.java    # 静态文件服务器
└── exercises/
    ├── Exercise2.md             # 练习题目
    └── solution/
```

## ▶️ 下一步

查看 [examples/RawHttpServer.java](./examples/RawHttpServer.java) 了解 HTTP 底层原理！
