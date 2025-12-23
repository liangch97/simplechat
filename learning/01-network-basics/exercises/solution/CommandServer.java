import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 【练习答案】练习 1.5 - 扩展回声服务器
 * 
 * 支持的命令:
 *   /time           - 返回当前时间
 *   /upper 文本     - 转大写
 *   /reverse 文本   - 反转字符串
 *   /help          - 显示帮助
 *   quit           - 退出
 */
public class CommandServer {
    
    private static final int PORT = 5050;
    
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════╗");
        System.out.println("║       命令服务器 v1.0              ║");
        System.out.println("╚═══════════════════════════════════╝");
        System.out.println("端口: " + PORT + "\n");
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("等待连接...\n");
            
            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("新连接: " + client.getRemoteSocketAddress());
                handleClient(client);
            }
            
        } catch (IOException e) {
            System.err.println("服务器错误: " + e.getMessage());
        }
    }
    
    private static void handleClient(Socket client) {
        try (
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream())
            );
            PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
        ) {
            // 发送帮助信息
            sendHelp(writer);
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                System.out.println("收到: " + line);
                
                // 处理命令
                String response = processCommand(line);
                writer.println(response);
                
                if ("quit".equalsIgnoreCase(line)) {
                    break;
                }
            }
            
        } catch (IOException e) {
            System.err.println("处理错误: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
            System.out.println("客户端已断开\n");
        }
    }
    
    /**
     * 处理用户命令
     */
    private static String processCommand(String input) {
        // 退出命令
        if ("quit".equalsIgnoreCase(input)) {
            return "再见！";
        }
        
        // /time 命令
        if ("/time".equalsIgnoreCase(input)) {
            String time = LocalTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")
            );
            return "服务器时间: " + time;
        }
        
        // /help 命令
        if ("/help".equalsIgnoreCase(input)) {
            return "命令: /time, /upper <文本>, /reverse <文本>, /help, quit";
        }
        
        // /upper 命令
        if (input.toLowerCase().startsWith("/upper ")) {
            String text = input.substring(7);  // 去掉 "/upper "
            return text.toUpperCase();
        }
        
        // /reverse 命令
        if (input.toLowerCase().startsWith("/reverse ")) {
            String text = input.substring(9);  // 去掉 "/reverse "
            return new StringBuilder(text).reverse().toString();
        }
        
        // 默认回声
        return "回声: " + input;
    }
    
    private static void sendHelp(PrintWriter writer) {
        writer.println("=== 欢迎使用命令服务器 ===");
        writer.println("可用命令:");
        writer.println("  /time          - 获取服务器时间");
        writer.println("  /upper <文本>  - 转为大写");
        writer.println("  /reverse <文本> - 反转字符串");
        writer.println("  /help          - 显示此帮助");
        writer.println("  quit           - 退出");
        writer.println("其他输入会原样返回（回声）");
        writer.println("============================");
    }
}
