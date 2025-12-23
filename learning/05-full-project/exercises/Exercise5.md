# 模块 5 练习：项目扩展

## 练习 5.1：添加消息时间戳 ⭐

**目标：** 在服务端记录消息发送时间，并显示在聊天界面。

### 要求

1. 修改 `SendHandler`，在广播的 JSON 中添加时间字段
2. 修改 `chat.js` 的 `addChatMessage`，使用服务器时间

### 提示

后端添加时间：
```java
String json = String.format(
    "{\"user\":\"%s\",\"message\":\"%s\",\"time\":\"%s\"}",
    user, message, LocalDateTime.now().toString()
);
```

前端解析时间：
```javascript
const time = new Date(data.time).toLocaleTimeString();
```

---

## 练习 5.2：添加在线人数 ⭐⭐

**目标：** 在界面上显示当前在线人数。

### 要求

1. 添加新端点 `/online` 返回在线人数
2. 前端定时获取并显示

### 提示

新端点：
```java
class OnlineHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        String response = "{\"count\":" + clients.size() + "}";
        // ... 返回 JSON
    }
}
```

前端定时获取：
```javascript
setInterval(async () => {
    const res = await fetch('/online');
    const data = await res.json();
    document.getElementById('online-count').textContent = data.count;
}, 5000);
```

---

## 练习 5.3：添加"正在输入"提示 ⭐⭐

**目标：** 当有人正在输入时，显示"某某正在输入..."

### 要求

1. 监听输入框的 `input` 事件
2. 发送 typing 状态到服务器
3. 服务器广播 typing 事件

### 提示

前端发送 typing：
```javascript
let typingTimeout;
input.addEventListener('input', () => {
    clearTimeout(typingTimeout);
    // 发送正在输入
    fetch('/typing', {
        method: 'POST',
        body: JSON.stringify({ user: username })
    });
    // 2秒后停止
    typingTimeout = setTimeout(() => {
        fetch('/stop-typing', { method: 'POST' });
    }, 2000);
});
```

---

## 练习 5.4：添加私聊功能 ⭐⭐⭐

**目标：** 支持 `/msg 用户名 消息` 格式的私聊。

### 要求

1. 解析消息，判断是否是 `/msg` 命令
2. 只发送给指定用户
3. 私聊消息用不同颜色显示

### 思考题

- 怎么找到目标用户的连接？
- 服务端需要存储什么额外信息？

---

## 练习 5.5：添加消息历史 ⭐⭐⭐

**目标：** 新用户加入时，发送最近 10 条消息。

### 要求

1. 服务端用 List 存储消息历史
2. 新连接建立时，发送历史消息
3. 限制历史消息数量

### 提示

存储历史：
```java
private static final List<String> history = new ArrayList<>();
private static final int MAX_HISTORY = 10;

// 广播时保存
public void broadcast(String message) {
    synchronized (history) {
        history.add(message);
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }
    // ... 广播逻辑
}
```

发送历史：
```java
// 在 EventsHandler 中，添加客户端后发送历史
synchronized (history) {
    for (String msg : history) {
        client.send("data: " + msg + "\n\n");
    }
}
```

---

## 挑战练习 5.6：添加聊天房间 ⭐⭐⭐⭐

**目标：** 支持多个聊天房间，用户可以选择加入不同房间。

### 要求

1. 修改前端，添加房间选择/创建功能
2. 修改 SSE 连接，支持 `/events?room=xxx`
3. 修改服务端，按房间分组管理客户端
4. 只广播给同一房间的用户

### 架构提示

```java
// 按房间分组
private static final Map<String, List<Client>> rooms = new ConcurrentHashMap<>();

// 获取房间参数
String room = exchange.getRequestURI().getQuery().split("=")[1];

// 加入房间
rooms.computeIfAbsent(room, k -> new ArrayList<>()).add(client);

// 广播到房间
public void broadcastToRoom(String room, String message) {
    List<Client> roomClients = rooms.get(room);
    if (roomClients != null) {
        for (Client client : roomClients) {
            client.send(message);
        }
    }
}
```

---

## 提交检查清单

完成练习后，检查：

- [ ] 代码能正常编译运行
- [ ] 功能按预期工作
- [ ] 没有明显的 bug
- [ ] 代码有适当的注释
- [ ] 变量命名清晰

## 参考答案

参考答案在 `solutions/` 文件夹中（建议先自己尝试！）
