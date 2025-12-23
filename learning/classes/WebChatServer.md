# WebChatServer 类

项目自定义类

## 📌 类简介

WebChatServer 是整个聊天室项目的**主服务器类**，它整合了：
- 静态文件服务（HTML、CSS、JS）
- SSE 实时推送
- 消息发送 API

这是一个"全功能"的 HTTP 服务器，可以直接在浏览器中使用。

## 📁 文件位置

```
src/WebChatServer.java
```

## 🏗️ 类结构

```java
public class WebChatServer {
    
    // ============ 静态成员 ============
    private static final List<Client> clients;     // 客户端列表
    private static final Map<String, String> MIME_TYPES;  // 文件类型映射
    
    // ============ 内部类 ============
    static class Client { ... }              // 客户端封装
    static class StaticFileHandler { ... }   // 静态文件处理
    static class EventsHandler { ... }       // SSE 处理
    static class SendHandler { ... }         // 消息发送处理
    
    // ============ 方法 ============
    public static void main(String[] args);  // 入口
    private static void broadcast(String message);  // 广播
    private static void startHeartbeat();    // 心跳
}
```

## 🔧 核心组件详解

### 1. 客户端列表

```java
private static final List<Client> clients = new CopyOnWriteArrayList<>();
```

- 存储所有连接的客户端
- 使用 `CopyOnWriteArrayList` 保证线程安全
- 每个客户端用 `Client` 对象封装

### 2. MIME 类型映射

```java
private static final Map<String, String> MIME_TYPES = Map.of(
    ".html", "text/html",
    ".css", "text/css",
    ".js", "application/javascript",
    ".json", "application/json",
    ".png", "image/png",
    ".svg", "image/svg+xml"
);
```

用于根据文件扩展名返回正确的 Content-Type。

### 3. 主函数

```java
public static void main(String[] args) throws Exception {
    // 1. 解析端口
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 7070;
    
    // 2. 创建服务器
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    
    // 3. 注册处理器
    server.createContext("/", new StaticFileHandler());
    server.createContext("/events", new EventsHandler());
    server.createContext("/send", new SendHandler());
    
    // 4. 配置线程池
    server.setExecutor(Executors.newCachedThreadPool());
    
    // 5. 启动心跳
    startHeartbeat();
    
    // 6. 启动服务器
    server.start();
    System.out.println("服务器启动: http://localhost:" + port);
}
```

### 4. 广播方法

```java
private static void broadcast(String message) {
    String data = "data: " + message + "\n\n";
    
    // 遍历所有客户端发送
    clients.removeIf(client -> !client.send(data));
}
```

- 将消息格式化为 SSE 格式
- 发送给所有客户端
- 自动移除发送失败的客户端

### 5. 心跳机制

```java
private static void startHeartbeat() {
    new Thread(() -> {
        while (true) {
            try {
                Thread.sleep(30000);  // 30秒
                broadcast(":heartbeat");
            } catch (InterruptedException e) {
                break;
            }
        }
    }).start();
}
```

- 每30秒发送一次心跳
- 保持连接活跃
- 检测断开的客户端

## 📊 请求处理流程

```
浏览器请求                    WebChatServer
    │                              │
    │  GET /                       │
    │ ────────────────────────────►│
    │                              │ StaticFileHandler
    │ ◄──────── index.html ────────│
    │                              │
    │  GET /css/style.css          │
    │ ────────────────────────────►│
    │                              │ StaticFileHandler
    │ ◄──────── CSS 文件 ──────────│
    │                              │
    │  GET /events                 │
    │ ────────────────────────────►│
    │                              │ EventsHandler
    │ ◄──────── SSE 连接 ──────────│
    │           (保持连接)          │
    │                              │
    │  POST /send                  │
    │ ────────────────────────────►│
    │                              │ SendHandler
    │                              │ → broadcast()
    │ ◄──────── 200 OK ────────────│
    │                              │
    │ ◄──────── SSE 消息 ──────────│ (广播给所有客户端)
```

## 🚀 运行方式

### 编译

```bash
cd src
javac WebChatServer.java
```

### 运行

```bash
# 默认端口 7070
java WebChatServer

# 指定端口
java WebChatServer 8080
```

### 访问

打开浏览器访问：`http://localhost:7070`

## ⚙️ 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 端口 | 7070 | 可通过命令行参数修改 |
| 心跳间隔 | 30秒 | 防止连接超时 |
| 静态文件目录 | web/ | HTML、CSS、JS 文件位置 |

## 🔄 扩展建议

### 添加新 API

```java
// 1. 创建新的 Handler
static class NewHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 处理逻辑...
    }
}

// 2. 在 main 中注册
server.createContext("/api/new", new NewHandler());
```

### 添加日志

```java
// 在每个 Handler 开始处添加
System.out.println("[" + LocalTime.now() + "] " + 
    exchange.getRequestMethod() + " " + 
    exchange.getRequestURI());
```

### 添加 CORS 支持

```java
Headers headers = exchange.getResponseHeaders();
headers.set("Access-Control-Allow-Origin", "*");
headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
headers.set("Access-Control-Allow-Headers", "Content-Type");
```

## 🔗 相关类

- [Client](./Client.md) - 客户端封装
- [Handlers](./Handlers.md) - 各个处理器详解
- [HttpServer](./HttpServer.md) - Java HTTP 服务器

## 📚 学习路径

理解 WebChatServer 需要先掌握：
1. [HttpServer](./HttpServer.md) - HTTP 服务器基础
2. [HttpExchange](./HttpExchange.md) - 请求响应处理
3. [Thread](./Thread.md) - 多线程和线程池
4. [Collections](./Collections.md) - 集合类使用
