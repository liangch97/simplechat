import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SYSU Chat 完整 HTTP 服务器
 * 域名: sysu.asia
 * 
 * 功能：
 *  - 静态文件服务 (HTML, CSS, JS, 图片等)
 *  - SSE 实时消息推送
 *  - REST API 发送消息
 * 
 * 端点：
 *  - GET  /           : 首页 (index.html)
 *  - GET  /*          : 静态文件服务
 *  - GET  /events     : SSE 事件流
 *  - POST /send       : 发送消息 {"name":"昵称","message":"内容"}
 *  - GET  /api/status : 服务器状态
 */
public class WebChatServer {
    
    private static final int DEFAULT_PORT = 7070;
    private static final String WEB_ROOT = "web";
    private static final Set<SseClient> CLIENTS = Collections.synchronizedSet(new HashSet<>());
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final AtomicInteger TOTAL_MESSAGES = new AtomicInteger(0);
    private static final LocalDateTime START_TIME = LocalDateTime.now();
    
    // MIME 类型映射
    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
        Map.entry("html", "text/html; charset=utf-8"),
        Map.entry("htm", "text/html; charset=utf-8"),
        Map.entry("css", "text/css; charset=utf-8"),
        Map.entry("js", "application/javascript; charset=utf-8"),
        Map.entry("json", "application/json; charset=utf-8"),
        Map.entry("png", "image/png"),
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("gif", "image/gif"),
        Map.entry("svg", "image/svg+xml"),
        Map.entry("ico", "image/x-icon"),
        Map.entry("woff", "font/woff"),
        Map.entry("woff2", "font/woff2"),
        Map.entry("ttf", "font/ttf"),
        Map.entry("txt", "text/plain; charset=utf-8")
    );

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try { 
                port = Integer.parseInt(args[0]); 
            } catch (NumberFormatException ignored) {}
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // API 端点
        server.createContext("/events", new EventsHandler());
        server.createContext("/send", new SendHandler());
        server.createContext("/api/status", new StatusHandler());
        
        // 静态文件服务 (放在最后，作为默认处理器)
        server.createContext("/", new StaticFileHandler());
        
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║         SYSU Chat Server Started           ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.println("║  Local:   http://localhost:" + port + "             ║");
        System.out.println("║  Domain:  https://sysu.asia:" + port + "            ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.println("║  Endpoints:                                ║");
        System.out.println("║    GET  /          - Web UI                ║");
        System.out.println("║    GET  /events    - SSE Stream            ║");
        System.out.println("║    POST /send      - Send Message          ║");
        System.out.println("║    GET  /api/status - Server Status        ║");
        System.out.println("╚════════════════════════════════════════════╝");
    }

    /**
     * 静态文件处理器
     */
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // 处理 OPTIONS 预检请求
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            // 默认页面
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }
            
            // 安全检查：防止目录遍历攻击
            if (path.contains("..")) {
                respond(exchange, 403, "Forbidden");
                return;
            }
            
            // 查找文件
            Path filePath = Paths.get(WEB_ROOT + path).normalize();
            File file = filePath.toFile();
            
            if (!file.exists() || !file.isFile()) {
                // 文件不存在，返回 404
                respond(exchange, 404, "Not Found: " + path);
                return;
            }
            
            // 获取 MIME 类型
            String mimeType = getMimeType(path);
            
            // 设置响应头
            Headers headers = exchange.getResponseHeaders();
            addCors(headers);
            headers.add("Content-Type", mimeType);
            headers.add("Cache-Control", getCacheControl(path));
            
            // 发送文件
            byte[] fileBytes = Files.readAllBytes(filePath);
            exchange.sendResponseHeaders(200, fileBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
        }
        
        private String getMimeType(String path) {
            int dotIndex = path.lastIndexOf('.');
            if (dotIndex > 0) {
                String ext = path.substring(dotIndex + 1).toLowerCase();
                return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
            }
            return "application/octet-stream";
        }
        
        private String getCacheControl(String path) {
            // HTML 不缓存，其他资源缓存 1 小时
            if (path.endsWith(".html") || path.endsWith(".htm")) {
                return "no-cache";
            }
            return "public, max-age=3600";
        }
    }

    /**
     * SSE 事件流处理器
     */
    static class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 处理 OPTIONS
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            Headers h = exchange.getResponseHeaders();
            addCors(h);
            h.add("Content-Type", "text/event-stream; charset=utf-8");
            h.add("Cache-Control", "no-cache");
            h.add("Connection", "keep-alive");
            h.add("X-Accel-Buffering", "no"); // 禁用 Nginx 缓冲
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            SseClient client = new SseClient(os);
            CLIENTS.add(client);
            
            // 发送连接成功事件
            client.send("event: info\ndata: {\"type\":\"connected\",\"online\":" + CLIENTS.size() + "}\n\n");
            
            // 广播在线人数更新
            broadcastOnlineCount();
            
            System.out.println("[SSE] Client connected, online=" + CLIENTS.size());

            // 保持连接
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // 发送心跳
                    if (!client.send(": heartbeat\n\n")) {
                        break;
                    }
                    Thread.sleep(30000); // 每 30 秒心跳
                }
            } catch (InterruptedException ignored) {
            } finally {
                CLIENTS.remove(client);
                try { os.close(); } catch (IOException ignored) {}
                broadcastOnlineCount();
                System.out.println("[SSE] Client disconnected, online=" + CLIENTS.size());
            }
        }
    }

    /**
     * 发送消息处理器
     */
    static class SendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 处理 OPTIONS
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "Method Not Allowed");
                return;
            }
            
            addCors(exchange.getResponseHeaders());

            byte[] body = exchange.getRequestBody().readAllBytes();
            String json = new String(body, StandardCharsets.UTF_8).trim();
            
            String name = extract(json, "name");
            String msg = extract(json, "message");
            
            if (name.isEmpty() || msg.isEmpty()) {
                respond(exchange, 400, "Invalid payload: name and message required");
                return;
            }
            
            // 简单的内容过滤
            name = sanitize(name, 20);
            msg = sanitize(msg, 500);
            
            String line = formatMsg(name, msg);
            broadcast(line);
            TOTAL_MESSAGES.incrementAndGet();
            
            respond(exchange, 200, "{\"status\":\"ok\",\"message\":\"sent\"}");
        }
        
        private String sanitize(String input, int maxLength) {
            if (input.length() > maxLength) {
                input = input.substring(0, maxLength);
            }
            // 移除危险字符
            return input.replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /**
     * 服务器状态 API
     */
    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            
            String json = String.format(
                "{\"online\":%d,\"totalMessages\":%d,\"uptime\":\"%s\",\"startTime\":\"%s\"}",
                CLIENTS.size(),
                TOTAL_MESSAGES.get(),
                getUptime(),
                START_TIME.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        
        private String getUptime() {
            java.time.Duration duration = java.time.Duration.between(START_TIME, LocalDateTime.now());
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }

    private static void broadcast(String data) {
        String payload = "data: " + data.replace("\n", "\\n") + "\n\n";
        synchronized (CLIENTS) {
            CLIENTS.removeIf(c -> !c.send(payload));
        }
        System.out.println("[MSG] " + data);
    }
    
    private static void broadcastOnlineCount() {
        String payload = "event: online\ndata: {\"count\":" + CLIENTS.size() + "}\n\n";
        synchronized (CLIENTS) {
            CLIENTS.removeIf(c -> !c.send(payload));
        }
    }

    private static String extract(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return "";
        int colon = json.indexOf(':', idx);
        if (colon < 0) return "";
        int firstQuote = json.indexOf('"', colon + 1);
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (firstQuote < 0 || secondQuote < 0) return "";
        return json.substring(firstQuote + 1, secondQuote);
    }

    private static String formatMsg(String sender, String text) {
        String ts = LocalDateTime.now().format(TS);
        return "[" + ts + "] " + sender + ": " + text;
    }

    private static void respond(HttpExchange ex, int code, String msg) throws IOException {
        addCors(ex.getResponseHeaders());
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { 
            os.write(bytes); 
        }
    }

    private static void addCors(Headers h) {
        h.add("Access-Control-Allow-Origin", "*");
        h.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static class SseClient {
        private final OutputStream out;
        
        SseClient(OutputStream out) { 
            this.out = out; 
        }
        
        boolean send(String payload) {
            try {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.flush();
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }
}
