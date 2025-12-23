import java.io.*;
import java.net.*;

/**
 * 【学习示例】最简单的客户端
 * 
 * 功能：连接服务器，发送一条消息
 * 
 * 运行方式：
 *   先启动 SimpleServer，然后：
 *   javac SimpleClient.java
 *   java SimpleClient
 * 
 * 知识点：
 *   1. Socket - 创建到服务器的连接
 *   2. OutputStream - 向服务器发送数据
 */
public class SimpleClient {
    
    public static void main(String[] args) {
        // 服务器地址和端口
        String host = "127.0.0.1";  // localhost 本机地址
        int port = 5050;
        
        System.out.println("=== 简单客户端示例 ===");
        System.out.println("正在连接服务器 " + host + ":" + port);
        
        // 创建 Socket 连接到服务器
        // 这里会尝试与服务器建立 TCP 连接
        try (Socket socket = new Socket(host, port)) {
            
            System.out.println("连接成功！");
            
            // 获取输出流，用于发送数据
            // PrintWriter 方便发送文本，autoFlush=true 自动刷新缓冲区
            PrintWriter writer = new PrintWriter(
                socket.getOutputStream(), 
                true  // autoFlush
            );
            
            // 发送一条消息
            String message = "你好，服务器！这是我的第一条消息。";
            writer.println(message);
            System.out.println("已发送: " + message);
            
            System.out.println("消息发送完毕，连接即将关闭");
            
        } catch (ConnectException e) {
            System.err.println("连接失败！请确保服务器已启动。");
            System.err.println("提示: 先运行 java SimpleServer");
        } catch (IOException e) {
            System.err.println("通讯错误: " + e.getMessage());
        }
        
        System.out.println("客户端已退出");
    }
}

/*
 * 💡 学习要点：
 * 
 * 1. Socket(host, port) - 创建客户端套接字
 *    - host: 服务器的IP地址或主机名
 *    - port: 服务器监听的端口号
 *    - 创建时会自动尝试连接
 * 
 * 2. 常见的 host 值：
 *    - "127.0.0.1" 或 "localhost" - 本机
 *    - "192.168.x.x" - 局域网其他电脑
 *    - "xxx.com" - 域名
 * 
 * 3. PrintWriter vs OutputStream
 *    - OutputStream: 原始字节流
 *    - PrintWriter: 方便的文本输出，支持 println()
 * 
 * 4. autoFlush 参数
 *    - true: 每次 println 后自动发送
 *    - false: 需要手动调用 flush()
 * 
 * 🔧 练习思考：
 * - 如何让客户端也能接收服务器的响应？
 * - 如何实现多次发送消息？
 */
