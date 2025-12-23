import java.io.*;
import java.net.*;

/**
 * 【学习示例】最简单的服务器
 * 
 * 功能：接收客户端连接，读取一条消息并打印
 * 
 * 运行方式：
 *   javac SimpleServer.java
 *   java SimpleServer
 * 
 * 知识点：
 *   1. ServerSocket - 创建服务器监听端口
 *   2. accept() - 阻塞等待客户端连接
 *   3. InputStream - 读取客户端发送的数据
 */
public class SimpleServer {
    
    public static void main(String[] args) {
        // 定义服务器监听的端口号
        int port = 5050;
        
        System.out.println("=== 简单服务器示例 ===");
        System.out.println("正在启动服务器，端口: " + port);
        
        // try-with-resources 语法，自动关闭资源
        // ServerSocket 用于监听指定端口的连接请求
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            
            System.out.println("服务器已启动，等待客户端连接...");
            System.out.println("提示: 打开新终端运行 SimpleClient 来测试");
            
            // accept() 方法会阻塞，直到有客户端连接
            // 返回一个 Socket 对象，用于与客户端通讯
            Socket clientSocket = serverSocket.accept();
            
            // 获取客户端的 IP 地址
            String clientAddress = clientSocket.getInetAddress().getHostAddress();
            System.out.println("客户端已连接: " + clientAddress);
            
            // 获取输入流，读取客户端发送的数据
            // BufferedReader 用于按行读取文本
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream())
            );
            
            // 读取一行数据
            String message = reader.readLine();
            System.out.println("收到消息: " + message);
            
            // 关闭连接
            clientSocket.close();
            System.out.println("连接已关闭");
            
        } catch (IOException e) {
            System.err.println("服务器错误: " + e.getMessage());
        }
        
        System.out.println("服务器已停止");
    }
}

/*
 * 💡 学习要点：
 * 
 * 1. ServerSocket(port) - 创建服务器套接字，绑定到指定端口
 *    - 端口范围: 0-65535
 *    - 1024以下的端口需要管理员权限
 *    - 常用测试端口: 5050, 8080, 9999
 * 
 * 2. accept() - 接受连接请求
 *    - 这是一个阻塞方法，程序会在这里等待
 *    - 直到有客户端连接才会继续执行
 *    - 返回一个新的 Socket 用于与该客户端通讯
 * 
 * 3. InputStream / OutputStream
 *    - 每个 Socket 都有两个流
 *    - InputStream: 读取对方发送的数据
 *    - OutputStream: 向对方发送数据
 * 
 * 🔧 练习思考：
 * - 如果多个客户端同时连接会发生什么？
 * - 如何让服务器能够持续接受新连接？
 */
