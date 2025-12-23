# HttpExchange 类

`com.sun.net.httpserver.HttpExchange`

## 📌 类简介

HttpExchange 封装了一次 HTTP 请求和响应。它包含：
- 客户端发来的请求信息（URL、方法、请求头、请求体）
- 向客户端发送响应的方法（状态码、响应头、响应体）

可以把它想象成一个"对话"对象，包含了客户端说的话和你要回复的内容。

## 📦 所属包

```java
import com.sun.net.httpserver.HttpExchange;
```

## 📋 获取请求信息

### 请求基本信息

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getRequestMethod()` | `String` | 获取 HTTP 方法（GET、POST 等） |
| `getRequestURI()` | `URI` | 获取请求的 URI |
| `getProtocol()` | `String` | 获取协议版本（HTTP/1.1） |
| `getRemoteAddress()` | `InetSocketAddress` | 获取客户端地址 |

**示例：**
```java
public void handle(HttpExchange exchange) throws IOException {
    // 获取请求方法
    String method = exchange.getRequestMethod();  // "GET" 或 "POST"
    
    // 获取请求路径
    URI uri = exchange.getRequestURI();
    String path = uri.getPath();        // "/api/users"
    String query = uri.getQuery();      // "id=123&name=test"
    
    // 获取客户端 IP
    String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
    
    System.out.println(method + " " + path + " from " + clientIP);
}
```

### 请求头

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getRequestHeaders()` | `Headers` | 获取所有请求头 |

**示例：**
```java
Headers headers = exchange.getRequestHeaders();

// 获取单个头
String contentType = headers.getFirst("Content-Type");
String userAgent = headers.getFirst("User-Agent");

// 遍历所有头
for (String key : headers.keySet()) {
    System.out.println(key + ": " + headers.getFirst(key));
}
```

### 请求体

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getRequestBody()` | `InputStream` | 获取请求体输入流 |

**示例：**
```java
// 读取 POST 请求体
InputStream is = exchange.getRequestBody();
String body = new String(is.readAllBytes(), "UTF-8");

// 如果是 JSON
// {"user": "张三", "message": "你好"}
```

## 📤 发送响应

### 设置响应头

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getResponseHeaders()` | `Headers` | 获取响应头（可修改） |

**示例：**
```java
Headers responseHeaders = exchange.getResponseHeaders();

// 设置内容类型
responseHeaders.set("Content-Type", "application/json; charset=utf-8");

// 设置 CORS（跨域）
responseHeaders.set("Access-Control-Allow-Origin", "*");

// 设置缓存
responseHeaders.set("Cache-Control", "no-cache");

// 添加多个值
responseHeaders.add("Set-Cookie", "session=abc123");
responseHeaders.add("Set-Cookie", "user=zhangsan");
```

### 发送响应头和状态码

```java
exchange.sendResponseHeaders(int statusCode, long responseLength);
```

| 参数 | 说明 |
|------|------|
| statusCode | HTTP 状态码（200、404、500 等） |
| responseLength | 响应体长度（字节数） |

**responseLength 特殊值：**
| 值 | 含义 |
|----|------|
| > 0 | 固定长度响应 |
| 0 | 长度未知，用于流式响应（如 SSE） |
| -1 | 无响应体 |

### 发送响应体

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getResponseBody()` | `OutputStream` | 获取响应体输出流 |

**示例：**
```java
String response = "Hello, World!";
byte[] bytes = response.getBytes("UTF-8");

exchange.sendResponseHeaders(200, bytes.length);
OutputStream os = exchange.getResponseBody();
os.write(bytes);
os.close();  // 必须关闭！
```

### 关闭

| 方法 | 说明 |
|------|------|
| `close()` | 关闭整个 exchange |

## 💡 完整示例

### GET 请求处理

```java
class GetHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        
        // 获取查询参数
        String query = exchange.getRequestURI().getQuery();
        // 解析 query: "name=张三&age=20"
        
        // 构造响应
        String response = "{\"status\": \"ok\"}";
        byte[] bytes = response.getBytes("UTF-8");
        
        // 设置响应头
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        
        // 发送响应
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
```

### POST 请求处理

```java
class PostHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        
        // 读取请求体
        String body = new String(
            exchange.getRequestBody().readAllBytes(), "UTF-8");
        
        System.out.println("收到: " + body);
        
        // 处理并响应
        String response = "{\"received\": true}";
        byte[] bytes = response.getBytes("UTF-8");
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
```

### SSE 流式响应

```java
class SSEHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // SSE 响应头
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream");
        headers.set("Cache-Control", "no-cache");
        headers.set("Connection", "keep-alive");
        
        // 长度未知，使用 0
        exchange.sendResponseHeaders(200, 0);
        
        // 获取输出流，保持连接
        OutputStream os = exchange.getResponseBody();
        
        // 发送事件...
        os.write("data: hello\n\n".getBytes());
        os.flush();
        
        // 注意：SSE 不主动关闭连接
    }
}
```

## 🔢 常用 HTTP 状态码

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| 200 | OK | 成功 |
| 201 | Created | 创建成功 |
| 204 | No Content | 成功但无响应体 |
| 400 | Bad Request | 请求格式错误 |
| 401 | Unauthorized | 未授权 |
| 403 | Forbidden | 禁止访问 |
| 404 | Not Found | 资源不存在 |
| 405 | Method Not Allowed | 方法不允许 |
| 500 | Internal Server Error | 服务器错误 |

## ⚠️ 注意事项

### 1. 必须先发 Headers 再发 Body

```java
// ❌ 错误顺序
exchange.getResponseBody().write(data);
exchange.sendResponseHeaders(200, data.length);

// ✅ 正确顺序
exchange.sendResponseHeaders(200, data.length);
exchange.getResponseBody().write(data);
```

### 2. 必须关闭 exchange

```java
// 推荐使用 try-finally
try {
    // 处理请求...
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
} finally {
    exchange.close();
}
```

### 3. 中文需要注意编码

```java
String response = "你好";

// ❌ 错误：直接用 length()
exchange.sendResponseHeaders(200, response.length());  // 2 字符

// ✅ 正确：转换为字节数组
byte[] bytes = response.getBytes("UTF-8");
exchange.sendResponseHeaders(200, bytes.length);  // 6 字节
```

## 🔗 相关类

- [HttpServer](./HttpServer.md) - HTTP 服务器
- [Handlers](./Handlers.md) - 处理器实现

## 📚 在项目中的使用

在 `WebChatServer.java` 的各个 Handler 中广泛使用：

```java
// StaticFileHandler
String path = exchange.getRequestURI().getPath();
exchange.getResponseHeaders().set("Content-Type", mimeType);
exchange.sendResponseHeaders(200, bytes.length);

// SendHandler
String body = new String(exchange.getRequestBody().readAllBytes());
exchange.sendResponseHeaders(200, -1);

// EventsHandler
exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
exchange.sendResponseHeaders(200, 0);  // 流式响应
```
