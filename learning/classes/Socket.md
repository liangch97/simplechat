# Socket 类

`java.net.Socket`

## 📌 类简介

Socket（套接字）是网络通信的端点，用于在客户端和服务器之间建立 TCP 连接。

可以把 Socket 想象成一个"电话"：
- 你拨打电话号码（IP + 端口）
- 对方接听后建立连接
- 双方可以互相说话（发送/接收数据）
- 通话结束后挂断（关闭连接）

## 📦 所属包

```java
import java.net.Socket;
```

## 🔨 构造方法

### 1. 连接到指定服务器

```java
Socket socket = new Socket(String host, int port);
```

| 参数 | 说明 | 示例 |
|------|------|------|
| host | 服务器地址 | `"localhost"` 或 `"192.168.1.1"` |
| port | 端口号 | `7070` |

**示例：**
```java
// 连接到本地 7070 端口
Socket socket = new Socket("localhost", 7070);

// 连接到远程服务器
Socket socket = new Socket("sysu.asia", 80);
```

### 2. 创建未连接的 Socket

```java
Socket socket = new Socket();
socket.connect(new InetSocketAddress("localhost", 7070), 5000); // 5秒超时
```

## 📋 常用方法

### 获取流（最重要！）

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getInputStream()` | `InputStream` | 获取输入流（读取数据） |
| `getOutputStream()` | `OutputStream` | 获取输出流（发送数据） |

**示例：**
```java
Socket socket = new Socket("localhost", 7070);

// 获取输出流，发送数据
OutputStream out = socket.getOutputStream();
out.write("Hello".getBytes());

// 获取输入流，接收数据
InputStream in = socket.getInputStream();
byte[] buffer = new byte[1024];
int len = in.read(buffer);
String message = new String(buffer, 0, len);
```

### 连接信息

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getInetAddress()` | `InetAddress` | 获取远程 IP 地址 |
| `getPort()` | `int` | 获取远程端口号 |
| `getLocalPort()` | `int` | 获取本地端口号 |
| `isConnected()` | `boolean` | 是否已连接 |
| `isClosed()` | `boolean` | 是否已关闭 |

### 关闭连接

| 方法 | 说明 |
|------|------|
| `close()` | 关闭 Socket 连接 |
| `shutdownInput()` | 关闭输入流 |
| `shutdownOutput()` | 关闭输出流 |

## 💡 完整示例

### 简单客户端

```java
import java.net.*;
import java.io.*;

public class SimpleClient {
    public static void main(String[] args) {
        // 使用 try-with-resources 自动关闭
        try (Socket socket = new Socket("localhost", 7070)) {
            
            // 发送消息
            PrintWriter out = new PrintWriter(
                socket.getOutputStream(), true);
            out.println("Hello, Server!");
            
            // 接收响应
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
            String response = in.readLine();
            System.out.println("服务器说: " + response);
            
        } catch (IOException e) {
            System.out.println("连接失败: " + e.getMessage());
        }
    }
}
```

## ⚠️ 注意事项

### 1. 必须关闭 Socket

```java
// ❌ 错误：不关闭会导致资源泄漏
Socket socket = new Socket("localhost", 7070);
// 使用 socket...
// 忘记关闭！

// ✅ 正确：使用 try-with-resources
try (Socket socket = new Socket("localhost", 7070)) {
    // 使用 socket...
} // 自动关闭
```

### 2. 连接可能失败

```java
try {
    Socket socket = new Socket("localhost", 7070);
} catch (ConnectException e) {
    // 服务器未启动
} catch (UnknownHostException e) {
    // 找不到主机
} catch (IOException e) {
    // 其他 I/O 错误
}
```

### 3. 设置超时

```java
Socket socket = new Socket();
// 连接超时 5 秒
socket.connect(new InetSocketAddress("localhost", 7070), 5000);
// 读取超时 10 秒
socket.setSoTimeout(10000);
```

## 🔗 相关类

- [ServerSocket](./ServerSocket.md) - 服务端监听
- [InputStream/OutputStream](./InputStream-OutputStream.md) - 数据流
- [InetSocketAddress](https://docs.oracle.com/javase/8/docs/api/java/net/InetSocketAddress.html) - 地址封装

## 📚 在项目中的使用

在 `ChatClient.java` 中：

```java
Socket socket = new Socket("localhost", 7070);
BufferedReader in = new BufferedReader(
    new InputStreamReader(socket.getInputStream()));
PrintWriter out = new PrintWriter(
    socket.getOutputStream(), true);
```
