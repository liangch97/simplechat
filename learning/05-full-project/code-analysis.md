# 完整项目代码分析指南

本文档帮助你逐步理解项目的核心代码。

## 第一部分：后端服务器分析

### WebChatServer.java 结构

打开 `src/WebChatServer.java`，它的结构如下：

```
WebChatServer.java
├── main()                    # 程序入口
├── 内部类
│   ├── Client               # 表示一个连接的客户端
│   ├── StaticFileHandler    # 处理静态文件请求
│   ├── EventsHandler        # 处理 SSE 连接
│   └── SendHandler          # 处理发送消息
├── 成员变量
│   ├── clients              # 所有连接的客户端列表
│   └── MIME_TYPES           # 文件类型映射
└── 方法
    ├── broadcast()          # 广播消息给所有客户端
    └── startHeartbeat()     # 发送心跳包
```

### 关键代码解读

#### 1. 主函数

```java
public static void main(String[] args) throws Exception {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 7070;
    
    // 创建 HTTP 服务器
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    
    // 注册处理器
    server.createContext("/", new StaticFileHandler());      // 静态文件
    server.createContext("/events", new EventsHandler());    // SSE
    server.createContext("/send", new SendHandler());        // 发消息
    
    // 启动
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
}
```

**问题思考：**
- 为什么用 `CachedThreadPool`？
- 如果改成单线程会怎样？

#### 2. SSE 连接处理

```java
class EventsHandler implements HttpHandler {
    public void handle(HttpExchange exchange) {
        // 设置 SSE 响应头
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream");
        headers.set("Cache-Control", "no-cache");
        headers.set("Connection", "keep-alive");
        
        // 发送响应头（-1 表示长度未知）
        exchange.sendResponseHeaders(200, 0);
        
        // 获取输出流
        OutputStream os = exchange.getResponseBody();
        
        // 创建客户端对象并加入列表
        Client client = new Client(os);
        clients.add(client);
    }
}
```

**问题思考：**
- `sendResponseHeaders(200, 0)` 中的 0 是什么意思？
- 为什么要设置 `Cache-Control: no-cache`？

#### 3. 广播实现

```java
public void broadcast(String message) {
    String data = "data: " + message + "\n\n";  // SSE 格式
    
    synchronized (clients) {
        Iterator<Client> it = clients.iterator();
        while (it.hasNext()) {
            Client client = it.next();
            if (!client.send(data)) {
                it.remove();  // 发送失败则移除
            }
        }
    }
}
```

**问题思考：**
- 为什么要用 `synchronized`？
- 为什么在迭代时移除用 `Iterator`？

---

## 第二部分：前端代码分析

### chat.js 结构

打开 `web/js/chat.js`，它的结构如下：

```
chat.js
├── ChatApp 类
│   ├── constructor()        # 初始化
│   ├── init()               # 设置事件监听
│   ├── login()              # 登录
│   ├── connect()            # 连接 SSE
│   ├── sendMessage()        # 发送消息
│   ├── addChatMessage()     # 添加消息到界面
│   └── addSystemMessage()   # 添加系统消息
└── 初始化代码
    └── new ChatApp()        # 创建实例
```

### 关键代码解读

#### 1. SSE 连接

```javascript
connect() {
    this.eventSource = new EventSource(CONFIG.API_URL + '/events');
    
    // 收到消息
    this.eventSource.onmessage = (event) => {
        const data = JSON.parse(event.data);
        this.addChatMessage(data.user, data.message);
    };
    
    // 连接错误
    this.eventSource.onerror = () => {
        this.eventSource.close();
        // 3秒后重连
        setTimeout(() => this.connect(), 3000);
    };
}
```

**问题思考：**
- 为什么需要断线重连？
- 如果重连间隔设为 0 会怎样？

#### 2. 发送消息

```javascript
async sendMessage() {
    const message = this.input.value.trim();
    if (!message) return;
    
    // 清空输入框
    this.input.value = '';
    
    // 发送请求
    await fetch(CONFIG.API_URL + '/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            user: this.username,
            message: message
        })
    });
}
```

**问题思考：**
- 为什么先清空输入框再发送？
- 如果请求失败怎么办？

#### 3. 消息渲染

```javascript
addChatMessage(user, message) {
    const isMe = user === this.username;
    
    const div = document.createElement('div');
    div.className = `chat-message ${isMe ? 'self' : ''}`;
    div.innerHTML = `
        <div class="message-header">
            <span class="username">${user}</span>
            <span class="time">${new Date().toLocaleTimeString()}</span>
        </div>
        <div class="message-content">${message}</div>
    `;
    
    this.messages.appendChild(div);
    
    // 滚动到底部
    this.messages.scrollTop = this.messages.scrollHeight;
}
```

**问题思考：**
- 怎么判断是自己发的消息？
- 为什么要滚动到底部？

---

## 第三部分：数据流分析

### 完整的消息发送流程

```
用户输入 "Hello"
     │
     ▼
┌──────────────────────────────────────────────────────────────┐
│ 前端 (chat.js)                                               │
│  1. 监听 form 的 submit 事件                                  │
│  2. 获取输入框的值: "Hello"                                   │
│  3. 构造 JSON: {"user":"张三","message":"Hello"}             │
│  4. 使用 fetch 发送 POST 请求到 /send                         │
└──────────────────────────────────────────────────────────────┘
     │
     ▼ HTTP POST /send
     │
┌──────────────────────────────────────────────────────────────┐
│ 后端 (SendHandler)                                           │
│  1. 接收请求                                                  │
│  2. 解析 JSON 数据                                            │
│  3. 调用 broadcast() 广播给所有客户端                          │
│  4. 返回 200 OK                                               │
└──────────────────────────────────────────────────────────────┘
     │
     ▼ SSE 推送
     │
┌──────────────────────────────────────────────────────────────┐
│ 前端 (所有连接的浏览器)                                        │
│  1. EventSource 收到 message 事件                             │
│  2. 解析 JSON 数据                                            │
│  3. 调用 addChatMessage() 显示消息                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 练习：代码追踪

按照下面的步骤，在代码中找到对应的位置：

### 练习 A：追踪登录流程

1. 用户点击"进入聊天"按钮
2. → 触发什么事件？在哪行代码？
3. → 调用什么方法？
4. → 界面发生什么变化？

### 练习 B：追踪心跳机制

1. 服务器在哪里启动心跳？
2. → 心跳间隔是多少？
3. → 发送什么内容？
4. → 客户端怎么处理心跳？

### 练习 C：追踪错误处理

1. 如果服务器关闭了会怎样？
2. → 前端会检测到什么？
3. → 会执行什么代码？
4. → 多久后重连？

---

完成这些分析后，你应该对整个项目有了深入的理解！
