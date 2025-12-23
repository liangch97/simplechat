import java.io.*;
import java.net.*;

public class EchoServer{
    private static final int port = 5050;

    public static void main(String[] args){
        System.out.println("=== 回声服务器示例 ===");
        System.out.println("正在启动服务器，端口: " + port);
        
        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("等待连接...");

            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("新客户端连接: " + client.getRemoteSocketAddress());
                handleClient(client);
                
            }
        }catch(IOException e){
            System.err.println("服务器错误: " + e.getMessage());
        }
    }
}
private static void handleClient(Socket client){
    try (
        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
        PrintWriter writer = new PrintWriter(
            client.getOutputStream(),true
        );
    ){
        writer.println("欢迎连接回声服务器！输入 'exit' 断开连接。");
        String line;
        while((line = reader.readLine())!=null){
            System.out.println("收到: " + line);

            if("quit".equalsIgnoreCase(line.trim())){
                writer.println("再见！");
                break;
            }
            writer.println("回声: " + line);

        }
        
        
    } catch (IOException e) {
        System.err.println("处理错误: " + e.getMessage());
    } finally {
        try { client.close(); } catch (IOException ignored) {}
        System.out.println("客户端已断开连接\n");
    }
}