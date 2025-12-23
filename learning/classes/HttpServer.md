# HttpServer 类

`com.sun.net.httpserver.HttpServer`

## 📌 类简介

HttpServer 是 Java 内置的 HTTP 服务器，可以处理 HTTP 请求并返回响应。

比起直接使用 ServerSocket，HttpServer 帮你处理了：
- HTTP 协议解析
- 请求头/响应头处理
- URL 路由
- 多线程管理

## 📦 所属包

```java
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
```

> ⚠️ 注意：这是 Sun/Oracle 的实现，不是标准 Java SE API，但在所有主流 JDK 中都可用。

## 🔨 创建服务器

### 基本创建

```java
HttpServer server = HttpServer.create(
    new InetSocketAddress(7070),  // 绑定地址和端口
    0                              // backlog，0 表示使用默认值
);
```

### 绑定到所有网络接口

```java
// 监听所有 IP
HttpServer server = HttpServer.create(
    new InetSocketAddress(7070), 0);

// 只监听本地
HttpServer server = HttpServer.create(
    new InetSocketAddress("127.0.0.1", 7070), 0);
```

## 📋 常用方法

### 路由注册

| 方法 | 说明 |
|------|------|
| `createContext(String path, HttpHandler handler)` | 注册路径处理器 |
| `removeContext(String path)` | 移除路径 |

```java
// 注册根路径
server.createContext("/", new RootHandler());

// 注册 API 路径
server.createContext("/api/users", new UsersHandler());
```

### 服务器控制

| 方法 | 说明 |
|------|------|
| `start()` | 启动服务器 |
| `stop(int delay)` | 停止服务器，delay 为等待秒数 |
| `setExecutor(Executor executor)` | 设置线程池 |

## 💡 完整示例

### 最简单的服务器

```java
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;

public class SimpleHttpServer {
    public static void main(String[] args) throws IOException {
        // 1. 创建服务器
        HttpServer server = HttpServer.create(
            new InetSocketAddress(7070), 0);
        
        // 2. 注册处理器
        server.createContext("/", exchange -> {
            String response = "Hello, World!";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        });
        
        // 3. 启动
        server.start();
        System.out.println("服务器运行在 http://localhost:7070");
    }
}
```

### 多路由服务器

```java
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class MultiRouteServer {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
            new InetSocketAddress(7070), 0);
        
        // 首页
        server.createContext("/", new HomeHandler());
        
        // API 端点
        server.createContext("/api/hello", new HelloHandler());
        server.createContext("/api/time", new TimeHandler());
        
        // 使用线程池
        server.setExecutor(Executors.newFixedThreadPool(10));
        
        server.start();
        System.out.println("服务器启动!");
    }
    
    // 首页处理器
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = "<h1>欢迎访问</h1><a href='/api/hello'>Hello API</a>";
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.getBytes("UTF-8").length);
            exchange.getResponseBody().write(html.getBytes("UTF-8"));
            exchange.close();
        }
    }
    
    // Hello API
    static class HelloHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = "{\"message\": \"Hello, API!\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length());
            exchange.getResponseBody().write(json.getBytes());
            exchange.close();
        }
    }
    
    // Time API
    static class TimeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = "{\"time\": \"" + java.time.LocalDateTime.now() + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length());
            exchange.getResponseBody().write(json.getBytes());
            exchange.close();
        }
    }
}
```

## 🛣️ 路由匹配规则

HttpServer 使用**最长前缀匹配**：

```java
server.createContext("/", handler1);        // 匹配所有
server.createContext("/api", handler2);     // 匹配 /api 开头
server.createContext("/api/users", handler3); // 匹配 /api/users 开头
```

| 请求路径 | 匹配的处理器 |
|----------|-------------|
| `/` | handler1 |
| `/about` | handler1 |
| `/api` | handler2 |
| `/api/test` | handler2 |
| `/api/users` | handler3 |
| `/api/users/123` | handler3 |

## ⚙️ 线程池配置

### 默认（单线程）

```java
server.setExecutor(null);  // 单线程，不推荐
```

### 缓存线程池（推荐）

```java
server.setExecutor(Executors.newCachedThreadPool());
```
- 按需创建线程
- 空闲线程会被回收

### 固定线程池

```java
server.setExecutor(Executors.newFixedThreadPool(10));
```
- 固定 10 个线程
- 适合负载可预测的场景

## ⚠️ 注意事项

### 1. 必须关闭 exchange

```java
// ❌ 错误：忘记关闭
public void handle(HttpExchange exchange) throws IOException {
    String response = "Hello";
    exchange.sendResponseHeaders(200, response.length());
    exchange.getResponseBody().write(response.getBytes());
    // 忘记关闭！客户端会一直等待
}

// ✅ 正确：关闭 exchange
public void handle(HttpExchange exchange) throws IOException {
    String response = "Hello";
    exchange.sendResponseHeaders(200, response.length());
    exchange.getResponseBody().write(response.getBytes());
    exchange.close();  // 或者 exchange.getResponseBody().close();
}
```

### 2. 设置正确的 Content-Length

```java
String response = "你好";  // 中文！
byte[] bytes = response.getBytes("UTF-8");

// ❌ 错误：使用字符数
exchange.sendResponseHeaders(200, response.length());  // 2

// ✅ 正确：使用字节数
exchange.sendResponseHeaders(200, bytes.length);  // 6
```

### 3. 处理不同 HTTP 方法

```java
public void handle(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    
    switch (method) {
        case "GET":
            handleGet(exchange);
            break;
        case "POST":
            handlePost(exchange);
            break;
        default:
            exchange.sendResponseHeaders(405, -1);  // Method Not Allowed
    }
}
```

## 🔗 相关类

- [HttpExchange](./HttpExchange.md) - 请求/响应对象
- [Handlers](./Handlers.md) - 处理器实现

## 📚 在项目中的使用

在 `WebChatServer.java` 中：

```java
HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

server.createContext("/", new StaticFileHandler());      // 静态文件
server.createContext("/events", new EventsHandler());    // SSE
server.createContext("/send", new SendHandler());        // 发消息

server.setExecutor(Executors.newCachedThreadPool());
server.start();
```
