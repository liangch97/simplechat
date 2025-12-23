import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * 【练习答案】练习 1.3 - 从键盘输入消息
 */
public class InteractiveClient {
    
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 5050;
        
        System.out.println("=== 交互式客户端 ===");
        
        try (
            Socket socket = new Socket(host, port);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("已连接到服务器！");
            System.out.print("请输入要发送的消息: ");
            
            // 从键盘读取用户输入
            String message = scanner.nextLine();
            
            // 发送给服务器
            writer.println(message);
            System.out.println("已发送: " + message);
            
        } catch (ConnectException e) {
            System.err.println("连接失败！请先启动服务器。");
        } catch (IOException e) {
            System.err.println("错误: " + e.getMessage());
        }
    }
}
