# ServerSocket 类

`java.net.ServerSocket`

## 📌 类简介

ServerSocket 是服务端的"监听器"，用于等待客户端的连接请求。

可以把 ServerSocket 想象成一个"前台接待"：
- 在某个端口守候（bind）
- 等待客人来访（accept）
- 客人来了就分配一个专属通道（返回 Socket）
- 继续等待下一位客人

## 📦 所属包

```java
import java.net.ServerSocket;
```

## 🔨 构造方法

### 1. 绑定到指定端口

```java
ServerSocket serverSocket = new ServerSocket(int port);
```

| 参数 | 说明 | 示例 |
|------|------|------|
| port | 监听端口号 | `7070` |

**示例：**
```java
// 在 7070 端口监听
ServerSocket serverSocket = new ServerSocket(7070);
```

### 2. 指定等待队列大小

```java
ServerSocket serverSocket = new ServerSocket(int port, int backlog);
```

| 参数 | 说明 | 默认值 |
|------|------|--------|
| backlog | 等待连接的队列长度 | 50 |

### 3. 绑定到指定地址

```java
ServerSocket serverSocket = new ServerSocket(7070, 50, 
    InetAddress.getByName("127.0.0.1"));
```

只接受来自本机的连接。

## 📋 常用方法

### 核心方法

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `accept()` | `Socket` | **阻塞等待**客户端连接 |
| `close()` | `void` | 关闭服务器 |

### 状态查询

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getLocalPort()` | `int` | 获取监听端口 |
| `isBound()` | `boolean` | 是否已绑定端口 |
| `isClosed()` | `boolean` | 是否已关闭 |

### 配置方法

| 方法 | 说明 |
|------|------|
| `setSoTimeout(int ms)` | 设置 accept() 超时时间 |
| `setReuseAddress(boolean on)` | 允许端口重用 |

## 💡 完整示例

### 单线程服务器（一次处理一个客户端）

```java
import java.net.*;
import java.io.*;

public class SimpleServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(7070);
        System.out.println("服务器启动，等待连接...");
        
        while (true) {
            // 阻塞等待客户端连接
            Socket clientSocket = serverSocket.accept();
            System.out.println("客户端已连接: " + 
                clientSocket.getRemoteSocketAddress());
            
            // 处理客户端
            handleClient(clientSocket);
        }
    }
    
    static void handleClient(Socket socket) throws IOException {
        try (socket) {
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(
                socket.getOutputStream(), true);
            
            String message = in.readLine();
            out.println("收到: " + message);
        }
    }
}
```

### 多线程服务器（同时处理多个客户端）

```java
import java.net.*;
import java.io.*;

public class MultiThreadServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(7070);
        System.out.println("多线程服务器启动...");
        
        while (true) {
            Socket clientSocket = serverSocket.accept();
            
            // 为每个客户端创建新线程
            new Thread(() -> {
                try {
                    handleClient(clientSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
    
    static void handleClient(Socket socket) throws IOException {
        // 同上...
    }
}
```

### 使用线程池的服务器（推荐）

```java
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class ThreadPoolServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(7070);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        
        System.out.println("线程池服务器启动...");
        
        while (true) {
            Socket clientSocket = serverSocket.accept();
            
            // 提交给线程池处理
            pool.submit(() -> {
                try {
                    handleClient(clientSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
```

## ⚠️ 注意事项

### 1. accept() 是阻塞的

```java
// 这行代码会一直等待，直到有客户端连接
Socket client = serverSocket.accept();
// 连接到达后才会执行到这里
```

### 2. 端口被占用

```java
try {
    ServerSocket ss = new ServerSocket(7070);
} catch (BindException e) {
    System.out.println("端口 7070 已被占用！");
}
```

解决方法：
- 换一个端口
- 关闭占用端口的程序
- 设置端口重用：`serverSocket.setReuseAddress(true)`

### 3. 端口号范围

| 范围 | 说明 |
|------|------|
| 0-1023 | 系统端口，需要管理员权限 |
| 1024-49151 | 注册端口，推荐使用 |
| 49152-65535 | 动态端口 |

### 4. 优雅关闭

```java
ServerSocket serverSocket = new ServerSocket(7070);

// 注册关闭钩子
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    try {
        serverSocket.close();
        System.out.println("服务器已关闭");
    } catch (IOException e) {}
}));
```

## 🔄 工作流程图

```
ServerSocket                    Socket (客户端)
     │                               │
     │  new ServerSocket(7070)       │
     ▼                               │
  [绑定端口]                          │
     │                               │
     │  accept() 阻塞等待             │
     ▼                               │
  [等待中...]                         │
     │                               │
     │ ◄───────── 连接请求 ───────────┤ new Socket("localhost", 7070)
     │                               │
     ▼                               │
  [返回 Socket] ─────────────────────►│
     │                               │
     │  继续 accept()                 │
     ▼                               ▼
  [等待下一个...]              [通信中...]
```

## 🔗 相关类

- [Socket](./Socket.md) - 客户端连接
- [HttpServer](./HttpServer.md) - HTTP 服务器（更高层）

## 📚 在项目中的使用

在 `ChatServer.java` 中：

```java
ServerSocket serverSocket = new ServerSocket(7070);
while (true) {
    Socket client = serverSocket.accept();
    clients.add(client);
    new Thread(new ClientHandler(client)).start();
}
```
