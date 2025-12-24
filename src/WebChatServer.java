import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import db.Db;
import db.MessageDao;
import db.UserDao;
import util.Env;

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

// 使用默认包下的 MessageDao

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
    
    private static final int DEFAULT_PORT = 8080;
    private static final String WEB_ROOT = "web";
    private static final String UPLOAD_DIR = "web/uploads"; // 上传文件目录
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

    public static void main(String[] args) throws IOException, InterruptedException {
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
        server.createContext("/api/history", new HistoryHandler());
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/verify", new VerifyHandler());
        server.createContext("/api/upload", new UploadHandler()); // 文件上传
        
        // 静态文件服务 (放在最后，作为默认处理器)
        server.createContext("/", new StaticFileHandler());
        
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        // 初始化数据库（可选，未配置则跳过）
        try {
            MessageDao.init();
            UserDao.init();
            System.out.println("[DB] Database tables ready (if DB configured)");
        } catch (Exception e) {
            System.out.println("[DB] init skipped or failed: " + e.getMessage());
        }
        
        // 确保上传目录存在
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        
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
        System.out.println("║    POST /api/upload- Upload File           ║");
        System.out.println("║    GET  /api/status - Server Status        ║");
        System.out.println("╚════════════════════════════════════════════╝");
        
        // 保持服务器运行
        Thread.currentThread().join();
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
            h.add("Cache-Control", "no-cache, no-store, must-revalidate");
            h.add("Pragma", "no-cache");
            h.add("Expires", "0");
            h.add("Connection", "keep-alive");
            h.add("X-Accel-Buffering", "no"); // 禁用 Nginx 缓冲
            h.add("CF-Cache-Status", "DYNAMIC"); // 告诉 Cloudflare 不缓存
            exchange.sendResponseHeaders(200, 0);

            // 从 URL 参数获取用户昵称
            String nickname = null;
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && "nickname".equals(kv[0])) {
                        try {
                            nickname = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            nickname = kv[1];
                        }
                        break;
                    }
                }
            }
            
            OutputStream os = exchange.getResponseBody();
            SseClient client = new SseClient(os, nickname);
            CLIENTS.add(client);
            
            // 发送连接成功事件（包含在线用户列表）
            java.util.List<String> users = getOnlineUsers();
            StringBuilder initSb = new StringBuilder();
            initSb.append("event: info\ndata: {\"type\":\"connected\",\"online\":").append(CLIENTS.size());
            initSb.append(",\"users\":[");
            for (int i = 0; i < users.size(); i++) {
                if (i > 0) initSb.append(",");
                initSb.append("\"").append(users.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            }
            initSb.append("]}\n\n");
            client.send(initSb.toString());
            
            // 广播在线人数更新（通知所有客户端有新用户加入）
            broadcastOnlineCount();
            
            String displayName = nickname != null ? nickname : "anonymous";
            System.out.println("[SSE] Client connected: " + displayName + ", online=" + CLIENTS.size());

            // 保持连接
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // 发送心跳 (更频繁以保持 Cloudflare Tunnel 连接)
                    if (!client.send(": heartbeat\n\n")) {
                        break;
                    }
                    Thread.sleep(15000); // 每 15 秒心跳，避免 Cloudflare 超时
                }
            } catch (InterruptedException ignored) {
            } finally {
                CLIENTS.remove(client);
                try { os.close(); } catch (IOException ignored) {}
                broadcastOnlineCount();
                System.out.println("[SSE] Client disconnected: " + displayName + ", online=" + CLIENTS.size());
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
            name = sanitize(name, 20, false);
            msg = sanitize(msg, 500, true);
            
            String line = formatMsg(name, msg);
            broadcast(line);
            TOTAL_MESSAGES.incrementAndGet();

            // 持久化到数据库（若启用）
            try {
                MessageDao.save(name, msg, LocalDateTime.now());
            } catch (Exception ignored) {}
            
            respond(exchange, 200, "{\"status\":\"ok\",\"message\":\"sent\"}");
        }
        
        private String sanitize(String input, int maxLength, boolean isMessage) {
            // 对于包含表情包或图片的消息，使用更大的长度限制
            if (isMessage && (input.startsWith("[STICKER:") || input.startsWith("[IMAGE:"))) {
                maxLength = 500000; // 500KB 用于 base64 编码的图片
            }
            if (input.length() > maxLength) {
                input = input.substring(0, maxLength);
            }
            // 移除危险字符（但不处理 base64 数据中的特殊字符）
            if (!input.startsWith("[STICKER:") && !input.startsWith("[IMAGE:")) {
                return input.replace("<", "&lt;").replace(">", "&gt;");
            }
            return input;
        }
    }

    /**
     * 历史记录 API：GET /api/history?limit=50&offset=0
     * 返回 JSON 数组，每项为一条格式化消息字符串。
     * 支持分页加载：offset 表示跳过的消息数量
     */
    static class HistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS 和方法
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "Method Not Allowed");
                return;
            }
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");

            int limit = 50;
            int offset = 0;
            try {
                String q = exchange.getRequestURI().getQuery();
                if (q != null) {
                    for (String p : q.split("&")) {
                        String[] kv = p.split("=", 2);
                        if (kv.length == 2) {
                            if (kv[0].equals("limit")) {
                                limit = Math.max(1, Math.min(500, Integer.parseInt(kv[1])));
                            } else if (kv[0].equals("offset")) {
                                offset = Math.max(0, Integer.parseInt(kv[1]));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            java.util.List<String> history = MessageDao.latest(limit, offset);
            int totalCount = MessageDao.getTotalCount();
            boolean hasMore = (offset + history.size()) < totalCount;
            
            StringBuilder sb = new StringBuilder();
            sb.append("{\"total\":").append(totalCount);
            sb.append(",\"offset\":").append(offset);
            sb.append(",\"hasMore\":").append(hasMore);
            sb.append(",\"messages\":[");
            for (int i = 0; i < history.size(); i++) {
                if (i > 0) sb.append(',');
                // 正确转义所有特殊字符
                String msg = history.get(i)
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
                sb.append('"').append(msg).append('"');
            }
            sb.append("]}");

            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
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

    /**
     * 用户注册 API: POST /api/register
     * Body: {"username":"xxx","password":"xxx","nickname":"xxx","secretKey":"xxx"}
     * 注册秘钥: 24336064
     */
    static class RegisterHandler implements HttpHandler {
        private static final String REGISTER_SECRET_KEY = "24336064";
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            
            byte[] body = exchange.getRequestBody().readAllBytes();
            String json = new String(body, StandardCharsets.UTF_8).trim();
            
            String username = extract(json, "username");
            String password = extract(json, "password");
            String nickname = extract(json, "nickname");
            String secretKey = extract(json, "secretKey");
            
            // 验证注册秘钥
            if (!REGISTER_SECRET_KEY.equals(secretKey)) {
                respond(exchange, 403, "{\"success\":false,\"error\":\"注册秘钥错误\"}");
                return;
            }
            
            String error = UserDao.register(username, password, nickname);
            
            if (error == null) {
                respond(exchange, 200, "{\"success\":true,\"message\":\"注册成功\"}");
            } else {
                respond(exchange, 400, "{\"success\":false,\"error\":\"" + escapeJson(error) + "\"}");
            }
        }
    }

    /**
     * 用户登录 API: POST /api/login
     * Body: {"username":"xxx","password":"xxx"}
     */
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            
            byte[] body = exchange.getRequestBody().readAllBytes();
            String json = new String(body, StandardCharsets.UTF_8).trim();
            
            String username = extract(json, "username");
            String password = extract(json, "password");
            
            UserDao.LoginResult result = UserDao.login(username, password);
            
            if (result.success()) {
                respond(exchange, 200, "{\"success\":true,\"token\":\"" + result.token + "\",\"nickname\":\"" + escapeJson(result.nickname) + "\"}");
            } else {
                respond(exchange, 401, "{\"success\":false,\"error\":\"" + escapeJson(result.error) + "\"}");
            }
        }
    }

    /**
     * Token 验证 API: POST /api/verify
     * Body: {"token":"xxx"}
     */
    static class VerifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            
            byte[] body = exchange.getRequestBody().readAllBytes();
            String json = new String(body, StandardCharsets.UTF_8).trim();
            
            String token = extract(json, "token");
            String nickname = UserDao.validateToken(token);
            
            if (nickname != null) {
                respond(exchange, 200, "{\"valid\":true,\"nickname\":\"" + escapeJson(nickname) + "\"}");
            } else {
                respond(exchange, 401, "{\"valid\":false,\"error\":\"Token无效或已过期\"}");
            }
        }
    }

    /**
     * 文件上传 API: POST /api/upload
     * 支持图片和文件上传，使用 multipart/form-data
     */
    static class UploadHandler implements HttpHandler {
        private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            
            try {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.contains("multipart/form-data")) {
                    respond(exchange, 400, "{\"error\":\"需要 multipart/form-data 格式\"}");
                    return;
                }
                
                // 解析 boundary
                String boundary = null;
                for (String part : contentType.split(";")) {
                    part = part.trim();
                    if (part.startsWith("boundary=")) {
                        boundary = part.substring(9);
                        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                            boundary = boundary.substring(1, boundary.length() - 1);
                        }
                        break;
                    }
                }
                
                if (boundary == null) {
                    respond(exchange, 400, "{\"error\":\"缺少 boundary\"}");
                    return;
                }
                
                // 读取请求体
                byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                
                if (bodyBytes.length > MAX_FILE_SIZE) {
                    respond(exchange, 413, "{\"error\":\"文件过大，最大支持100MB\"}");
                    return;
                }
                
                // 使用二进制方式解析 multipart 数据
                byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
                
                String fileName = null;
                String senderName = null;
                String fileType = "file";
                byte[] fileData = null;
                
                int pos = 0;
                while (pos < bodyBytes.length) {
                    // 找到下一个 boundary
                    int boundaryStart = indexOf(bodyBytes, boundaryBytes, pos);
                    if (boundaryStart < 0) break;
                    
                    int partStart = boundaryStart + boundaryBytes.length;
                    if (partStart >= bodyBytes.length) break;
                    
                    // 跳过 \r\n
                    if (bodyBytes[partStart] == '\r' && partStart + 1 < bodyBytes.length && bodyBytes[partStart + 1] == '\n') {
                        partStart += 2;
                    } else if (bodyBytes[partStart] == '-' && partStart + 1 < bodyBytes.length && bodyBytes[partStart + 1] == '-') {
                        // 结束标记 --boundary--
                        break;
                    }
                    
                    // 找到 header 结束位置 (\r\n\r\n)
                    int headerEnd = indexOf(bodyBytes, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), partStart);
                    if (headerEnd < 0) break;
                    
                    String headers = new String(bodyBytes, partStart, headerEnd - partStart, StandardCharsets.ISO_8859_1);
                    int contentStart = headerEnd + 4;
                    
                    // 找到下一个 boundary 来确定内容结束位置
                    int nextBoundary = indexOf(bodyBytes, boundaryBytes, contentStart);
                    if (nextBoundary < 0) nextBoundary = bodyBytes.length;
                    
                    // 内容结束位置（去掉结尾的 \r\n）
                    int contentEnd = nextBoundary;
                    if (contentEnd >= 2 && bodyBytes[contentEnd - 2] == '\r' && bodyBytes[contentEnd - 1] == '\n') {
                        contentEnd -= 2;
                    }
                    
                    int contentLength = contentEnd - contentStart;
                    
                    if (headers.contains("name=\"file\"")) {
                        // 提取文件名
                        int fnStart = headers.indexOf("filename=\"");
                        if (fnStart >= 0) {
                            fnStart += 10;
                            int fnEnd = headers.indexOf("\"", fnStart);
                            if (fnEnd > fnStart) {
                                fileName = headers.substring(fnStart, fnEnd);
                                // 处理 UTF-8 编码的文件名
                                try {
                                    fileName = new String(fileName.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                                } catch (Exception ignored) {}
                            }
                        }
                        // 获取文件二进制数据
                        fileData = new byte[contentLength];
                        System.arraycopy(bodyBytes, contentStart, fileData, 0, contentLength);
                    } else if (headers.contains("name=\"name\"")) {
                        byte[] nameBytes = new byte[contentLength];
                        System.arraycopy(bodyBytes, contentStart, nameBytes, 0, contentLength);
                        senderName = new String(nameBytes, StandardCharsets.UTF_8).trim();
                    } else if (headers.contains("name=\"type\"")) {
                        byte[] typeBytes = new byte[contentLength];
                        System.arraycopy(bodyBytes, contentStart, typeBytes, 0, contentLength);
                        fileType = new String(typeBytes, StandardCharsets.UTF_8).trim();
                    }
                    
                    pos = nextBoundary;
                }
                
                if (fileName == null || fileData == null || senderName == null) {
                    respond(exchange, 400, "{\"error\":\"缺少必要参数\"}");
                    return;
                }
                
                // 生成唯一文件名
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
                String safeFileName = timestamp + "_" + sanitizeFileName(fileName);
                
                // 保存文件
                Path uploadPath = Paths.get(UPLOAD_DIR, safeFileName);
                Files.write(uploadPath, fileData);
                
                System.out.println("[Upload] Saved: " + safeFileName + " (" + fileData.length + " bytes)");
                
                // 生成访问 URL
                String fileUrl = "/uploads/" + safeFileName;
                
                // 广播消息
                String msgContent;
                if ("image".equals(fileType)) {
                    msgContent = "[IMAGE:" + fileUrl + "]";
                } else {
                    msgContent = "[FILE:" + fileName + "|" + fileUrl + "]";
                }
                
                String line = formatMsg(senderName, msgContent);
                broadcast(line);
                TOTAL_MESSAGES.incrementAndGet();
                
                // 持久化到数据库
                try {
                    MessageDao.save(senderName, msgContent, LocalDateTime.now());
                } catch (Exception ignored) {}
                
                respond(exchange, 200, "{\"status\":\"ok\",\"url\":\"" + fileUrl + "\"}");
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"上传失败: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
        
        // 在字节数组中查找子数组
        private int indexOf(byte[] data, byte[] pattern, int start) {
            outer:
            for (int i = start; i <= data.length - pattern.length; i++) {
                for (int j = 0; j < pattern.length; j++) {
                    if (data[i + j] != pattern[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
        
        private String sanitizeFileName(String fileName) {
            // 移除路径分隔符和危险字符
            String safe = fileName.replaceAll("[/\\\\:*?\"<>|]", "_");
            // 限制长度
            if (safe.length() > 100) {
                String ext = "";
                int dotIndex = safe.lastIndexOf('.');
                if (dotIndex > 0) {
                    ext = safe.substring(dotIndex);
                    safe = safe.substring(0, dotIndex);
                }
                safe = safe.substring(0, 100 - ext.length()) + ext;
            }
            return safe;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static void broadcast(String data) {
        String payload = "data: " + data.replace("\n", "\\n") + "\n\n";
        synchronized (CLIENTS) {
            CLIENTS.removeIf(c -> !c.send(payload));
        }
        System.out.println("[MSG] " + data);
    }
    
    private static void broadcastOnlineCount() {
        java.util.List<String> users = getOnlineUsers();
        StringBuilder sb = new StringBuilder();
        sb.append("event: online\ndata: {\"count\":").append(CLIENTS.size());
        sb.append(",\"users\":[");
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(users.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        sb.append("]}\n\n");
        String payload = sb.toString();
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
        if (firstQuote < 0) return "";
        
        // 正确处理转义字符，查找未转义的结束双引号
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = firstQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                // 处理转义字符
                switch (c) {
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    default: result.append(c); break;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                // 找到未转义的结束双引号
                break;
            } else {
                result.append(c);
            }
        }
        return result.toString();
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
        private String nickname;
        
        SseClient(OutputStream out) { 
            this.out = out; 
            this.nickname = null;
        }
        
        SseClient(OutputStream out, String nickname) { 
            this.out = out; 
            this.nickname = nickname;
        }
        
        String getNickname() {
            return nickname;
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
    
    /**
     * 获取在线用户列表
     */
    private static java.util.List<String> getOnlineUsers() {
        java.util.List<String> users = new java.util.ArrayList<>();
        synchronized (CLIENTS) {
            for (SseClient client : CLIENTS) {
                String nick = client.getNickname();
                if (nick != null && !nick.isEmpty() && !users.contains(nick)) {
                    users.add(nick);
                }
            }
        }
        return users;
    }
}
