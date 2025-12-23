# Client 类

项目自定义类（WebChatServer 内部类）

## 📌 类简介

Client 是对单个客户端连接的封装，主要用于：
- 存储客户端的输出流
- 封装发送消息的逻辑
- 处理发送失败的情况

## 📁 位置

```java
// 在 WebChatServer.java 中
public class WebChatServer {
    
    static class Client {
        // ...
    }
}
```

## 🏗️ 类结构

```java
static class Client {
    private final OutputStream outputStream;
    private final String id;
    
    public Client(OutputStream os) {
        this.outputStream = os;
        this.id = UUID.randomUUID().toString().substring(0, 8);
    }
    
    public boolean send(String data) {
        try {
            outputStream.write(data.getBytes("UTF-8"));
            outputStream.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    public String getId() {
        return id;
    }
}
```

## 📋 成员详解

### 属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `outputStream` | `OutputStream` | 客户端的输出流，用于发送数据 |
| `id` | `String` | 客户端唯一标识（可选） |

### 方法

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `Client(OutputStream os)` | - | 构造函数 |
| `send(String data)` | `boolean` | 发送数据，成功返回 true |
| `getId()` | `String` | 获取客户端 ID |

## 💡 使用示例

### 创建客户端

```java
// 在 EventsHandler 中
public void handle(HttpExchange exchange) throws IOException {
    // ... 设置响应头 ...
    
    exchange.sendResponseHeaders(200, 0);
    OutputStream os = exchange.getResponseBody();
    
    // 创建 Client 对象
    Client client = new Client(os);
    
    // 添加到列表
    clients.add(client);
    
    System.out.println("客户端连接: " + client.getId());
}
```

### 发送消息

```java
// 单独发送
client.send("data: hello\n\n");

// 广播发送
for (Client client : clients) {
    if (!client.send(data)) {
        // 发送失败，客户端可能已断开
    }
}
```

### 移除断开的客户端

```java
// 方式1：使用 removeIf
clients.removeIf(client -> !client.send(data));

// 方式2：使用 Iterator
Iterator<Client> it = clients.iterator();
while (it.hasNext()) {
    Client client = it.next();
    if (!client.send(data)) {
        it.remove();
        System.out.println("客户端断开: " + client.getId());
    }
}
```

## 🔄 为什么需要封装？

### 不封装的写法

```java
// 直接存储 OutputStream
List<OutputStream> clients = new ArrayList<>();

// 广播时
for (OutputStream os : clients) {
    try {
        os.write(data.getBytes());
        os.flush();
    } catch (IOException e) {
        // 需要移除，但在遍历中不好处理
    }
}
```

### 封装后的优势

```java
// 存储 Client 对象
List<Client> clients = new ArrayList<>();

// 广播时 - 一行搞定
clients.removeIf(client -> !client.send(data));
```

## 🔧 扩展版本

可以给 Client 添加更多功能：

```java
static class Client {
    private final OutputStream outputStream;
    private final String id;
    private final long connectedTime;
    private String username;        // 用户名
    private String room;            // 所在房间
    
    public Client(OutputStream os) {
        this.outputStream = os;
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.connectedTime = System.currentTimeMillis();
    }
    
    public boolean send(String data) {
        try {
            outputStream.write(data.getBytes("UTF-8"));
            outputStream.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    // 发送 SSE 事件
    public boolean sendEvent(String event, String data) {
        return send("event: " + event + "\ndata: " + data + "\n\n");
    }
    
    // Getters & Setters
    public String getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
    
    // 获取连接时长
    public long getConnectionDuration() {
        return System.currentTimeMillis() - connectedTime;
    }
}
```

### 使用扩展版本

```java
// 设置用户名
client.setUsername("张三");

// 发送自定义事件
client.sendEvent("typing", "{\"user\": \"张三\"}");

// 按房间广播
for (Client client : clients) {
    if ("room1".equals(client.getRoom())) {
        client.send(data);
    }
}

// 查找特定用户
Client target = clients.stream()
    .filter(c -> "张三".equals(c.getUsername()))
    .findFirst()
    .orElse(null);
```

## ⚠️ 注意事项

### 1. 线程安全

```java
// Client 本身不需要同步，因为：
// - outputStream 只由当前线程写入
// - send() 的返回值用于判断是否断开

// 但 clients 列表需要线程安全
private static final List<Client> clients = new CopyOnWriteArrayList<>();
```

### 2. 资源清理

```java
// Client 不负责关闭流
// 流的关闭由 HttpExchange 管理
// 当客户端断开时，send() 会抛异常并返回 false
```

### 3. 发送失败处理

```java
// send() 返回 false 表示：
// - 客户端已断开连接
// - 网络出现问题
// - 需要从列表中移除

if (!client.send(data)) {
    clients.remove(client);
    // 可以记录日志
    System.out.println("Client " + client.getId() + " disconnected");
}
```

## 🔗 相关类

- [WebChatServer](./WebChatServer.md) - 主服务器类
- [Handlers](./Handlers.md) - 请求处理器
- [InputStream-OutputStream](./InputStream-OutputStream.md) - 流的使用
