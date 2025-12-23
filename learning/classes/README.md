# 📖 类参考文档

本文件夹包含项目中使用的所有重要类的详细说明。

## 目录

### Java 标准库类

| 文档 | 类名 | 用途 |
|------|------|------|
| [Socket.md](./Socket.md) | `java.net.Socket` | 客户端网络连接 |
| [ServerSocket.md](./ServerSocket.md) | `java.net.ServerSocket` | 服务端监听 |
| [HttpServer.md](./HttpServer.md) | `com.sun.net.httpserver.HttpServer` | HTTP 服务器 |
| [HttpExchange.md](./HttpExchange.md) | `com.sun.net.httpserver.HttpExchange` | HTTP 请求响应 |
| [InputStream-OutputStream.md](./InputStream-OutputStream.md) | I/O 流 | 数据读写 |
| [Thread.md](./Thread.md) | `java.lang.Thread` | 多线程 |
| [Collections.md](./Collections.md) | 集合类 | 数据存储 |

### 项目自定义类

| 文档 | 类名 | 用途 |
|------|------|------|
| [WebChatServer.md](./WebChatServer.md) | `WebChatServer` | 主服务器类 |
| [Client.md](./Client.md) | `Client` | 客户端连接封装 |
| [Handlers.md](./Handlers.md) | 各种 Handler | 请求处理器 |

### JavaScript 类

| 文档 | 类名 | 用途 |
|------|------|------|
| [ChatApp.md](./ChatApp.md) | `ChatApp` | 前端应用类 |
| [EventSource.md](./EventSource.md) | `EventSource` | SSE 客户端 |

## 如何阅读

每个文档包含：
- **类简介** - 这个类是干什么的
- **构造方法** - 如何创建实例
- **常用方法** - 主要方法说明
- **使用示例** - 代码示例
- **注意事项** - 常见问题

---

建议先阅读 [Socket.md](./Socket.md) 和 [ServerSocket.md](./ServerSocket.md) 开始！
