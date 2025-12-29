package app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 简易多线程聊天服务器，适合 10 人左右同时在线。
 * 协议：
 *  - 客户端连接后第一行发送昵称。
 *  - 之后每行即为聊天内容，服务器会广播到所有在线用户。
 *  - 发送 /quit 断开连接。
 */
public class ChatServer {
    private static final int PORT = 5050;
    private static final Set<ClientHandler> CLIENTS = Collections.synchronizedSet(new HashSet<>());
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        int port = PORT;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        System.out.println("[Server] Starting chat server on port " + port + " ...");
        try (ServerSocket server = new ServerSocket(port)) {
            while (true) {
                Socket socket = server.accept();
                ClientHandler handler = new ClientHandler(socket);
                CLIENTS.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error: " + e.getMessage());
        }
    }

    private static void broadcast(String message, ClientHandler sender) {
        synchronized (CLIENTS) {
            for (ClientHandler client : CLIENTS) {
                if (client != sender) {
                    client.send(message);
                }
            }
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private String name = "Anonymous";

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                this.out = writer;
                writer.println("Welcome to Cyber Chat! Enter your nickname:");
                String nick = in.readLine();
                if (nick != null && !nick.isBlank()) {
                    name = nick.trim();
                }
                writer.println("Hi, " + name + "! Type /quit to exit.");
                String joinMsg = formatMsg("SERVER", name + " joined the room");
                System.out.println("[Server] " + joinMsg);
                broadcast(joinMsg, this);

                String line;
                while ((line = in.readLine()) != null) {
                    if ("/quit".equalsIgnoreCase(line.trim())) {
                        break;
                    }
                    String chat = formatMsg(name, line);
                    System.out.println(chat);
                    broadcast(chat, this);
                }
            } catch (IOException e) {
                System.err.println("[Server] Connection error: " + e.getMessage());
            } finally {
                CLIENTS.remove(this);
                String left = formatMsg("SERVER", name + " left the room");
                System.out.println("[Server] " + left);
                broadcast(left, this);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        void send(String message) {
            if (out != null) {
                out.println(message);
            }
        }
    }

    private static String formatMsg(String sender, String text) {
        String ts = LocalDateTime.now().format(TS);
        return "[" + ts + "] " + sender + ": " + text;
    }
}
