import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * 简易命令行聊天客户端。
 * 用法: java ChatClient [host] [port]
 */
public class ChatClient {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = 5050;
        if (args.length > 1) {
            try { port = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        try (Socket socket = new Socket(host, port);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in))) {

            // 读取服务器欢迎与昵称提示
            System.out.println(serverIn.readLine());
            String nickname = userIn.readLine();
            serverOut.println(nickname);
            System.out.println(serverIn.readLine());

            // 接收线程
            Thread receiver = new Thread(() -> {
                String line;
                try {
                    while ((line = serverIn.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.out.println("[Client] Disconnected: " + e.getMessage());
                }
            });
            receiver.setDaemon(true);
            receiver.start();

            // 发送循环
            String input;
            while ((input = userIn.readLine()) != null) {
                serverOut.println(input);
                if ("/quit".equalsIgnoreCase(input.trim())) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("[Client] Error: " + e.getMessage());
        }
    }
}
