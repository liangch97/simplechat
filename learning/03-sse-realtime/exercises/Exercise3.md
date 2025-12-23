# 练习 3: SSE 实时通讯

## 📝 练习题目

### 练习 3.1: 理解 SSE (理论)

回答以下问题：

1. SSE 与普通 HTTP 请求的区别是什么？
2. SSE 响应必须包含哪些响应头？
3. SSE 消息格式中，`data:`、`event:`、`id:` 分别有什么作用？
4. 为什么调用 `flush()` 很重要？

---

### 练习 3.2: 运行示例

1. 运行 SimpleSSEServer：
```bash
cd examples
javac SimpleSSEServer.java
java SimpleSSEServer
```

2. 打开浏览器访问 http://localhost:8080
3. 观察消息推送效果
4. 打开开发者工具 (F12) → Network → 找到 events 请求
   - 观察响应头
   - 观察 EventStream 数据

---

### 练习 3.3: 聊天室测试

1. 运行 ChatSSEServer
2. 打开**多个浏览器标签**访问 http://localhost:8080
3. 在不同标签发送消息
4. 观察消息是如何广播的

---

### 练习 3.4: 添加功能 ⭐

修改 `ChatSSEServer.java`，实现以下功能：

1. 显示消息发送者的 IP 地址
2. 添加 `/online` 接口，返回当前在线人数

提示：
```java
// 获取客户端 IP
String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
```

---

### 练习 3.5: 自定义事件 ⭐⭐

修改服务器，使用自定义 SSE 事件：

1. `event: message` - 普通聊天消息
2. `event: system` - 系统通知（加入/离开）
3. `event: online` - 在线人数更新

服务器端格式：
```
event: system
data: 有用户加入

event: online
data: {"count": 5}
```

客户端监听：
```javascript
eventSource.addEventListener('system', (e) => {
    console.log('系统消息:', e.data);
});
```

---

### 练习 3.6: 对比项目代码 ⭐⭐⭐

打开项目的 `src/HttpChatServer.java`：

1. 找出 SSE 连接处理代码
2. 找出消息广播代码
3. 对比与 ChatSSEServer.java 的异同

写一份简短的对比报告（100字左右）。

---

## ✅ 检验标准

完成后，你应该能够：
- [ ] 解释 SSE 的工作原理
- [ ] 手写 SSE 服务器代码
- [ ] 实现消息广播功能
- [ ] 使用 JavaScript EventSource API

---

## 🎯 下一步

进入 [模块4: 前端 JavaScript](../04-frontend-js/README.md) 学习前端实现！
