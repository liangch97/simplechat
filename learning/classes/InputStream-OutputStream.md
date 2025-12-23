# InputStream 与 OutputStream

`java.io.InputStream` / `java.io.OutputStream`

## 📌 类简介

InputStream 和 OutputStream 是 Java I/O 的基础抽象类：
- **InputStream** - 输入流，用于**读取**数据（从外部到程序）
- **OutputStream** - 输出流，用于**写入**数据（从程序到外部）

想象一下水管：
- InputStream 是进水管，水流进来
- OutputStream 是出水管，水流出去

## 📦 所属包

```java
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
```

## 🔵 InputStream（输入流）

### 核心方法

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `read()` | `int` | 读取一个字节，返回 -1 表示结束 |
| `read(byte[] b)` | `int` | 读取多个字节到数组，返回读取数量 |
| `read(byte[] b, int off, int len)` | `int` | 读取指定数量到数组指定位置 |
| `readAllBytes()` | `byte[]` | 读取所有字节（Java 9+） |
| `available()` | `int` | 返回可读取的字节数 |
| `close()` | `void` | 关闭流 |

### 使用示例

```java
// 从 Socket 读取数据
InputStream in = socket.getInputStream();

// 方式1：一次读一个字节
int b;
while ((b = in.read()) != -1) {
    System.out.print((char) b);
}

// 方式2：读取到缓冲区
byte[] buffer = new byte[1024];
int len = in.read(buffer);
String message = new String(buffer, 0, len, "UTF-8");

// 方式3：读取全部（Java 9+）
byte[] allBytes = in.readAllBytes();
String content = new String(allBytes, "UTF-8");
```

### 常用子类

| 类名 | 用途 |
|------|------|
| `FileInputStream` | 从文件读取 |
| `ByteArrayInputStream` | 从字节数组读取 |
| `BufferedInputStream` | 带缓冲的读取 |
| `ObjectInputStream` | 读取对象 |

## 🔴 OutputStream（输出流）

### 核心方法

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `write(int b)` | `void` | 写入一个字节 |
| `write(byte[] b)` | `void` | 写入字节数组 |
| `write(byte[] b, int off, int len)` | `void` | 写入数组的一部分 |
| `flush()` | `void` | 刷新缓冲区 |
| `close()` | `void` | 关闭流 |

### 使用示例

```java
// 向 Socket 写入数据
OutputStream out = socket.getOutputStream();

// 方式1：写入字节数组
String message = "Hello";
out.write(message.getBytes("UTF-8"));
out.flush();  // 确保发送

// 方式2：写入单个字节
out.write('H');
out.write('i');
out.flush();
```

### 常用子类

| 类名 | 用途 |
|------|------|
| `FileOutputStream` | 写入文件 |
| `ByteArrayOutputStream` | 写入字节数组 |
| `BufferedOutputStream` | 带缓冲的写入 |
| `ObjectOutputStream` | 写入对象 |

## 🔄 包装流（装饰器模式）

为了方便使用，通常会用高级流包装基础流：

### BufferedReader / BufferedWriter

```java
// 包装 InputStream 为行读取器
InputStream in = socket.getInputStream();
BufferedReader reader = new BufferedReader(
    new InputStreamReader(in, "UTF-8"));

String line = reader.readLine();  // 读一行
```

### PrintWriter

```java
// 包装 OutputStream 为打印器
OutputStream out = socket.getOutputStream();
PrintWriter writer = new PrintWriter(
    new OutputStreamWriter(out, "UTF-8"), true);  // true = 自动刷新

writer.println("Hello");  // 自动添加换行并刷新
```

### DataInputStream / DataOutputStream

```java
// 读写基本类型
DataOutputStream dos = new DataOutputStream(out);
dos.writeInt(123);
dos.writeDouble(3.14);
dos.writeUTF("你好");

DataInputStream dis = new DataInputStream(in);
int num = dis.readInt();
double pi = dis.readDouble();
String str = dis.readUTF();
```

## 💡 完整示例

### 网络通信

```java
import java.net.*;
import java.io.*;

public class StreamExample {
    public static void main(String[] args) throws IOException {
        // 客户端
        try (Socket socket = new Socket("localhost", 7070)) {
            
            // 获取流
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            
            // 包装为更方便的读写器
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, "UTF-8"));
            PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(out, "UTF-8"), true);
            
            // 发送
            writer.println("Hello, Server!");
            
            // 接收
            String response = reader.readLine();
            System.out.println("收到: " + response);
        }
    }
}
```

### 文件读写

```java
import java.io.*;

public class FileStreamExample {
    public static void main(String[] args) throws IOException {
        // 写入文件
        try (FileOutputStream fos = new FileOutputStream("test.txt")) {
            fos.write("Hello, File!".getBytes("UTF-8"));
        }
        
        // 读取文件
        try (FileInputStream fis = new FileInputStream("test.txt")) {
            byte[] data = fis.readAllBytes();
            System.out.println(new String(data, "UTF-8"));
        }
    }
}
```

## ⚠️ 注意事项

### 1. 必须关闭流

```java
// ❌ 错误：忘记关闭
InputStream in = new FileInputStream("file.txt");
byte[] data = in.readAllBytes();
// 资源泄漏！

// ✅ 正确：try-with-resources
try (InputStream in = new FileInputStream("file.txt")) {
    byte[] data = in.readAllBytes();
}  // 自动关闭
```

### 2. flush() 的重要性

```java
OutputStream out = socket.getOutputStream();
out.write("Hello".getBytes());
// 数据可能还在缓冲区，没有真正发送！

out.flush();  // 强制发送
```

### 3. 字符编码

```java
// ❌ 可能乱码（使用系统默认编码）
String s = new String(bytes);

// ✅ 指定编码
String s = new String(bytes, "UTF-8");
```

### 4. 读取可能不完整

```java
byte[] buffer = new byte[1024];

// ❌ read() 不一定填满整个 buffer
in.read(buffer);

// ✅ 使用返回值确定实际读取量
int len = in.read(buffer);
String message = new String(buffer, 0, len);
```

## 🔀 流的分类

```
                    ┌─── InputStream ───┐
                    │                   │
按方向分     ───────┤                   ├─── 字节流
                    │                   │
                    └─── OutputStream ──┘
                    
                    ┌─── Reader ────────┐
                    │                   │
                ────┤                   ├─── 字符流
                    │                   │
                    └─── Writer ────────┘
```

| | 字节流 | 字符流 |
|------|--------|--------|
| 输入 | InputStream | Reader |
| 输出 | OutputStream | Writer |
| 单位 | byte (8位) | char (16位) |
| 用途 | 二进制数据 | 文本数据 |

## 📚 在项目中的使用

### SSE 推送

```java
OutputStream os = exchange.getResponseBody();
String data = "data: " + message + "\n\n";
os.write(data.getBytes("UTF-8"));
os.flush();  // 立即发送
```

### 读取 POST 请求体

```java
InputStream is = exchange.getRequestBody();
String body = new String(is.readAllBytes(), "UTF-8");
```

### 发送静态文件

```java
InputStream fileStream = new FileInputStream(file);
OutputStream responseStream = exchange.getResponseBody();

byte[] buffer = new byte[8192];
int len;
while ((len = fileStream.read(buffer)) != -1) {
    responseStream.write(buffer, 0, len);
}
```
