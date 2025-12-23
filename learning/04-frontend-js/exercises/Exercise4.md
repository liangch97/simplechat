# 练习 4: 前端 JavaScript

## 📝 练习题目

### 练习 4.1: DOM 操作

打开 `examples/01-dom-basics.html`，完成以下练习：

1. 阅读代码，理解 `getElementById`、`createElement`、`appendChild` 的用法
2. 修改计数器，添加"x2"按钮（将当前值翻倍）
3. 添加颜色选择器，让用户可以选择计数器的颜色

---

### 练习 4.2: Fetch API

打开 `examples/03-fetch-api.html`，回答：

1. GET 和 POST 请求的区别是什么？
2. `Content-Type: application/json` 有什么作用？
3. 为什么要使用 `JSON.stringify()` 处理请求体？

---

### 练习 4.3: SSE 客户端

1. 先运行 `learning/03-sse-realtime/examples/SimpleSSEServer.java`
2. 打开 `examples/04-sse-client.html`
3. 点击连接按钮，观察消息
4. 手动关闭服务器，观察重连行为

---

### 练习 4.4: 迷你聊天室 ⭐

1. 运行 `learning/03-sse-realtime/examples/ChatSSEServer.java`
2. 打开 `examples/05-mini-chat.html`
3. 打开多个标签页测试聊天功能

思考：
- 消息是如何从服务器推送到所有客户端的？
- 前端如何区分自己发的消息和别人发的消息？

---

### 练习 4.5: 添加功能 ⭐⭐

修改 `05-mini-chat.html`，添加以下功能：

1. **表情按钮**: 点击插入 😀 表情
2. **消息计数**: 显示已收到多少条消息
3. **发送中状态**: 发送时按钮显示"发送中..."

---

### 练习 4.6: 对比项目代码 ⭐⭐⭐

打开项目的 `web/js/chat.js`：

1. 找到 SSE 连接代码
2. 找到发送消息代码
3. 理解 `ChatApp` 类的结构
4. 写一份简短的代码分析（100字）

---

## ✅ 检验标准

完成后，你应该能够：
- [ ] 使用 DOM API 操作页面元素
- [ ] 使用 Fetch API 发送 GET/POST 请求
- [ ] 使用 EventSource 接收 SSE 消息
- [ ] 理解前端聊天室的完整逻辑

---

## 🎯 下一步

进入最后一个模块 [模块5: 完整项目实战](../05-full-project/README.md)！
