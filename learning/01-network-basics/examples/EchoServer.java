import java.io.*;
import java.net.*;

/**
 * 【学习示例】回声服务器 (Echo Server)
 * 
 * 功能：接收客户端消息，原样返回（回声）
 * 
 * 这是网络编程的经典入门示例！
 * 
 * 运行方式：
 *   javac EchoServer.java
 *   java EchoServer
 * 
 * 测试方式：使用 telnet 或 EchoClient 连接
 *   telnet localhost 5050
 */
public class EchoServer {
    
    private static final int PORT = 5050;
    
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════╗");
        System.out.println("║      回声服务器 (Echo Server)      ║");
        System.out.println("╚═══════════════════════════════════╝");
        System.out.println("端口: " + PORT);
        System.out.println("输入的内容会原样返回给你\n");
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("等待连接...");
            
            // 循环接受多个客户端连接
            while (true) {
                // 接受一个新连接
                Socket client = serverSocket.accept();
                System.out.println("新客户端连接: " + client.getRemoteSocketAddress());
                
                // 处理这个客户端（当前是单线程，一次只能服务一个客户端）
                handleClient(client);
            }
            
        } catch (IOException e) {
            System.err.println("服务器错误: " + e.getMessage());
        }
    }
    
    /**
     * 处理单个客户端连接
     */
    private static void handleClient(Socket client) {
        try (
            // 输入流 - 读取客户端消息
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream())
            );
            // 输出流 - 发送回声
            PrintWriter writer = new PrintWriter(
                client.getOutputStream(), true
            );
        ) {
            // 发送欢迎消息
            writer.println("欢迎来到回声服务器！输入 'quit' 退出。");
            
            String line;
            // 循环读取客户端发送的每一行
            while ((line = reader.readLine()) != null) {
                System.out.println("收到: " + line);
                
                // 检查是否退出
                if ("quit".equalsIgnoreCase(line.trim())) {
                    writer.println("再见！");
                    break;
                }
                
                // 回声 - 原样返回
                writer.println("回声: " + line);
            }
            
        } catch (IOException e) {
            System.err.println("客户端处理错误: " + e.getMessage());
        } finally {
            try {
                client.close();
                System.out.println("客户端已断开");
            } catch (IOException ignored) {}
        }
    }
}

/*
 * 💡 学习要点：
 * 
 * 1. 无限循环 while(true)
 *    - 让服务器持续运行
 *    - 每次循环处理一个客户端
 * 
 * 2. 双向通讯
 *    - 同时使用 InputStream 和 OutputStream
 *    - 可以读取客户端消息，也可以发送响应
 * 
 * 3. 协议设计
 *    - "quit" 作为退出命令
 *    - 这就是最简单的"协议"
 * 
 * ⚠️ 当前版本的问题：
 * - 单线程：一次只能服务一个客户端
 * - 下一个示例会讲解多线程解决方案
 * 
 * 🔧 练习：
 * 1. 添加 /time 命令返回当前时间
 * 2. 添加 /upper 命令将文本转为大写
 * 3. 统计每个客户端发送的消息数量
 */
