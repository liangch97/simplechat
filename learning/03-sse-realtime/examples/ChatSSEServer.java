import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * 【学习示例】聊天室 SSE 服务器
 * 
 * 完整的聊天室后端，支持：
 * - 多客户端连接
 * - 消息广播
 * - 在线人数统计
 * 
 * 这是项目中 HttpChatServer.java 的简化学习版！
 */
public class ChatSSEServer {
    
    private static final int PORT = 8080;
    
    // 存储所有连接的客户端
    private static final Set<ClientConnection> CLIENTS = 
        Collections.synchronizedSet(new HashSet<>());
    
    public static void main(String[] args) throws IOException {
        System.out.println("╔═══════════════════════════════════╗");
        System.out.println("║      聊天室 SSE 服务器             ║");
        System.out.println("╚═══════════════════════════════════╝");
        
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        server.createContext("/", new PageHandler());
        server.createContext("/events", new SSEHandler());
        server.createContext("/send", new SendHandler());
        
        // 使用线程池处理请求
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.println("服务器已启动: http://localhost:" + PORT);
        System.out.println("\n端点:");
        System.out.println("  GET  /        - 聊天页面");
        System.out.println("  GET  /events  - SSE 连接");
        System.out.println("  POST /send    - 发送消息");
    }
    
    /**
     * 广播消息给所有客户端
     */
    private static void broadcast(String message) {
        String payload = "data: " + message + "\n\n";
        
        synchronized (CLIENTS) {
            // 移除发送失败的客户端
            CLIENTS.removeIf(client -> !client.send(payload));
        }
        
        System.out.println("[广播] " + message + " (在线: " + CLIENTS.size() + ")");
    }
    
    /**
     * SSE 连接处理
     */
    static class SSEHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置 SSE 响应头
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "text/event-stream; charset=utf-8");
            headers.set("Cache-Control", "no-cache");
            headers.set("Connection", "keep-alive");
            headers.set("Access-Control-Allow-Origin", "*");
            
            exchange.sendResponseHeaders(200, 0);
            
            OutputStream os = exchange.getResponseBody();
            ClientConnection client = new ClientConnection(os);
            
            // 添加到客户端列表
            CLIENTS.add(client);
            System.out.println("[连接] 新客户端, 在线: " + CLIENTS.size());
            
            // 发送欢迎消息
            client.send("data: [系统] 欢迎加入聊天室！当前在线: " + CLIENTS.size() + "\n\n");
            
            // 广播加入通知
            broadcast("[系统] 有新用户加入，当前在线: " + CLIENTS.size());
            
            // 保持连接
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(30000); // 心跳间隔
                    // 发送心跳（注释也是有效的 SSE 数据）
                    if (!client.send(": heartbeat\n\n")) {
                        break;
                    }
                }
            } catch (InterruptedException ignored) {
            } finally {
                CLIENTS.remove(client);
                try { os.close(); } catch (IOException ignored) {}
                System.out.println("[断开] 客户端离开, 在线: " + CLIENTS.size());
                broadcast("[系统] 有用户离开，当前在线: " + CLIENTS.size());
            }
        }
    }
    
    /**
     * 发送消息处理
     */
    static class SendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 处理跨域预检
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }
            
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            // 读取请求体
            String body = new String(
                exchange.getRequestBody().readAllBytes(), 
                StandardCharsets.UTF_8
            );
            
            // 简单解析 JSON {"name":"xxx", "message":"xxx"}
            String name = extractJson(body, "name");
            String message = extractJson(body, "message");
            
            if (name.isEmpty() || message.isEmpty()) {
                sendResponse(exchange, 400, "Bad Request");
                return;
            }
            
            // 格式化并广播
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String formatted = "[" + time + "] " + name + ": " + message;
            broadcast(formatted);
            
            sendResponse(exchange, 200, "OK");
        }
        
        private String extractJson(String json, String key) {
            String pattern = "\"" + key + "\"";
            int idx = json.indexOf(pattern);
            if (idx < 0) return "";
            int colon = json.indexOf(':', idx);
            int start = json.indexOf('"', colon + 1);
            int end = json.indexOf('"', start + 1);
            if (start < 0 || end < 0) return "";
            return json.substring(start + 1, end);
        }
        
        private void sendResponse(HttpExchange ex, int code, String msg) throws IOException {
            byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
    
    /**
     * 客户端连接封装
     */
    static class ClientConnection {
        private final OutputStream out;
        
        ClientConnection(OutputStream out) {
            this.out = out;
        }
        
        boolean send(String data) {
            try {
                out.write(data.getBytes(StandardCharsets.UTF_8));
                out.flush();
                return true;
            } catch (IOException e) {
                return false; // 客户端断开
            }
        }
    }
    
    /**
     * 聊天页面
     */
    static class PageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>SSE 聊天室</title>
                    <style>
                        * { box-sizing: border-box; }
                        body { font-family: Arial; padding: 20px; max-width: 600px; margin: 0 auto; }
                        h1 { color: #006633; }
                        #messages { 
                            height: 300px; 
                            overflow-y: auto; 
                            border: 1px solid #ddd; 
                            padding: 10px;
                            margin-bottom: 10px;
                            border-radius: 8px;
                        }
                        .msg { padding: 8px; margin: 4px 0; background: #f5f5f5; border-radius: 4px; }
                        .system { background: #fff3cd; color: #856404; }
                        .form { display: flex; gap: 10px; }
                        input { flex: 1; padding: 10px; border: 1px solid #ddd; border-radius: 4px; }
                        button { padding: 10px 20px; background: #006633; color: white; border: none; border-radius: 4px; cursor: pointer; }
                        button:hover { background: #004d26; }
                        #status { padding: 8px; margin-bottom: 10px; border-radius: 4px; }
                        .online { background: #d4edda; color: #155724; }
                        .offline { background: #f8d7da; color: #721c24; }
                    </style>
                </head>
                <body>
                    <h1>💬 SSE 聊天室</h1>
                    <div id="status" class="offline">连接中...</div>
                    
                    <div class="form" style="margin-bottom: 15px;">
                        <input type="text" id="nickname" placeholder="输入昵称" value="用户">
                    </div>
                    
                    <div id="messages"></div>
                    
                    <div class="form">
                        <input type="text" id="message" placeholder="输入消息，按回车发送">
                        <button onclick="send()">发送</button>
                    </div>
                    
                    <script>
                        const messagesDiv = document.getElementById('messages');
                        const statusDiv = document.getElementById('status');
                        const nicknameInput = document.getElementById('nickname');
                        const messageInput = document.getElementById('message');
                        
                        // SSE 连接
                        const es = new EventSource('/events');
                        
                        es.onopen = () => {
                            statusDiv.textContent = '✅ 已连接';
                            statusDiv.className = 'online';
                        };
                        
                        es.onmessage = (e) => {
                            addMessage(e.data);
                        };
                        
                        es.onerror = () => {
                            statusDiv.textContent = '❌ 连接断开';
                            statusDiv.className = 'offline';
                        };
                        
                        function addMessage(text) {
                            const div = document.createElement('div');
                            div.className = 'msg' + (text.includes('[系统]') ? ' system' : '');
                            div.textContent = text;
                            messagesDiv.appendChild(div);
                            messagesDiv.scrollTop = messagesDiv.scrollHeight;
                        }
                        
                        async function send() {
                            const name = nicknameInput.value.trim() || '匿名';
                            const msg = messageInput.value.trim();
                            if (!msg) return;
                            
                            await fetch('/send', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/json'},
                                body: JSON.stringify({name, message: msg})
                            });
                            
                            messageInput.value = '';
                        }
                        
                        messageInput.addEventListener('keypress', (e) => {
                            if (e.key === 'Enter') send();
                        });
                    </script>
                </body>
                </html>
                """;
            
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}

/*
 * 💡 学习要点：
 * 
 * 1. 客户端管理
 *    - 用 Set<ClientConnection> 存储所有连接
 *    - 新连接时 add，断开时 remove
 *    - 使用 synchronized 保证线程安全
 * 
 * 2. 广播机制
 *    - 遍历所有客户端，发送相同消息
 *    - 发送失败的（已断开）自动移除
 * 
 * 3. 心跳机制
 *    - 定期发送心跳保持连接
 *    - SSE 注释格式: ": heartbeat\n\n"
 *    - 冒号开头的行会被客户端忽略
 * 
 * 4. 与项目代码对比
 *    - 打开 src/HttpChatServer.java
 *    - 结构几乎完全一样！
 *    - 你已经理解了项目核心代码！
 * 
 * 🎉 恭喜！学到这里，你已经理解了：
 * - SSE 的工作原理
 * - 如何实现实时消息广播
 * - 聊天室后端的核心架构
 */
