# 模块 4: 前端 JavaScript

## 🎯 学习目标

- 理解前端在聊天室中的作用
- 掌握 DOM 操作和事件处理
- 学会使用 Fetch API 发送请求
- 理解 EventSource 接收 SSE

## 📖 核心概念

### 前端的职责

```
┌─────────────────────────────────────────────────────────┐
│                      前端 (浏览器)                       │
├─────────────────────────────────────────────────────────┤
│  1. 用户界面 (HTML/CSS)                                  │
│     - 登录表单                                          │
│     - 消息列表                                          │
│     - 发送框                                            │
│                                                         │
│  2. 用户交互 (JavaScript)                               │
│     - 点击、输入事件                                     │
│     - 表单验证                                          │
│                                                         │
│  3. 与服务器通讯 (JavaScript)                           │
│     - SSE 接收消息                                       │
│     - Fetch 发送消息                                     │
└─────────────────────────────────────────────────────────┘
```

### DOM 操作基础

```javascript
// 获取元素
const element = document.getElementById('myId');
const elements = document.querySelectorAll('.myClass');

// 修改内容
element.textContent = '纯文本';
element.innerHTML = '<b>HTML内容</b>';

// 修改样式
element.style.color = 'red';
element.classList.add('active');
element.classList.remove('active');

// 创建元素
const div = document.createElement('div');
div.textContent = '新消息';
container.appendChild(div);
```

### 事件处理

```javascript
// 点击事件
button.addEventListener('click', function() {
    console.log('按钮被点击');
});

// 表单提交
form.addEventListener('submit', function(e) {
    e.preventDefault(); // 阻止默认提交
    // 自定义处理
});

// 键盘事件
input.addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        send();
    }
});
```

### Fetch API

```javascript
// GET 请求
const response = await fetch('/api/data');
const data = await response.json();

// POST 请求
await fetch('/send', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json'
    },
    body: JSON.stringify({
        name: '用户',
        message: '你好'
    })
});
```

### EventSource (SSE 客户端)

```javascript
// 建立 SSE 连接
const eventSource = new EventSource('/events');

// 连接成功
eventSource.onopen = function() {
    console.log('已连接');
};

// 接收消息
eventSource.onmessage = function(event) {
    console.log('收到:', event.data);
};

// 连接错误（会自动重连）
eventSource.onerror = function() {
    console.log('连接断开');
};

// 关闭连接
eventSource.close();
```

## 📁 本模块文件

```
04-frontend-js/
├── README.md
├── examples/
│   ├── 01-dom-basics.html      # DOM 操作基础
│   ├── 02-event-handling.html  # 事件处理
│   ├── 03-fetch-api.html       # Fetch API
│   ├── 04-sse-client.html      # SSE 客户端
│   └── 05-mini-chat.html       # 迷你聊天室
└── exercises/
    └── Exercise4.md
```

## ▶️ 下一步

打开 [examples/01-dom-basics.html](./examples/01-dom-basics.html) 开始学习！
