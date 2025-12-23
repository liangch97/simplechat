# 练习 1: Java 网络编程基础

## 📝 练习题目

### 练习 1.1: 理解代码 (阅读理解)

阅读 `examples/SimpleServer.java`，回答以下问题：

1. `ServerSocket` 和 `Socket` 的区别是什么？
2. `accept()` 方法的作用是什么？为什么说它是"阻塞"的？
3. 如果端口 5050 已被其他程序占用，会发生什么？

---

### 练习 1.2: 动手实验

运行 SimpleServer 和 SimpleClient：

```bash
# 终端 1 - 启动服务器
cd examples
javac SimpleServer.java
java SimpleServer

# 终端 2 - 启动客户端
cd examples  
javac SimpleClient.java
java SimpleClient
```

观察并记录：
- 服务器端显示了什么？
- 客户端显示了什么？
- 消息是如何从客户端传到服务器的？

---

### 练习 1.3: 修改代码 ⭐

修改 `SimpleClient.java`，实现以下功能：

1. 让用户从键盘输入要发送的消息（而不是写死在代码里）
2. 提示：使用 `Scanner` 类读取键盘输入

```java
// 提示代码
Scanner scanner = new Scanner(System.in);
System.out.print("请输入消息: ");
String message = scanner.nextLine();
```

---

### 练习 1.4: 双向通讯 ⭐⭐

修改 SimpleServer 和 SimpleClient，实现双向通讯：

1. 客户端发送消息给服务器
2. 服务器收到后，回复 "服务器已收到: [原消息]"
3. 客户端接收并显示服务器的回复

提示：
- 服务器需要同时使用 `InputStream` 和 `OutputStream`
- 客户端也需要能够读取服务器的响应

---

### 练习 1.5: 扩展回声服务器 ⭐⭐⭐

扩展 `EchoServer.java`，添加以下命令：

| 命令 | 功能 |
|------|------|
| `/time` | 返回服务器当前时间 |
| `/upper 文本` | 将文本转为大写返回 |
| `/reverse 文本` | 将文本反转返回 |
| `/help` | 显示帮助信息 |

示例交互：
```
> /time
服务器时间: 14:30:25

> /upper hello world
HELLO WORLD

> /reverse 你好
好你
```

---

## ✅ 参考答案

完成后，查看 `solution/` 文件夹中的参考答案。

---

## 🎯 下一步

完成这些练习后，进入下一模块学习 [HTTP 服务器原理](../02-http-server/README.md)！
