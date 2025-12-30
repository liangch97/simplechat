package app;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import db.Db;
import db.MessageDao;
import db.UserDao;
import db.FileDao;
import util.Env;
import util.FileManager;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final String DEFAULT_ROOM = "public";
    private static final int MAX_ROOM_KEY_LENGTH = 128;
    private static final Map<String, Set<SseClient>> CLIENTS_BY_ROOM = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> ROOM_MESSAGE_COUNT = new ConcurrentHashMap<>();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");
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
        server.createContext("/api/admin/users", new AdminUsersHandler()); // 管理员-用户列表
        server.createContext("/api/admin/user/delete", new AdminDeleteUserHandler()); // 管理员-删除用户
        server.createContext("/api/admin/user/update", new AdminUpdateUserHandler()); // 管理员-更新用户
        server.createContext("/api/admin/user/reset-password", new AdminResetPasswordHandler()); // 管理员-重置密码
        server.createContext("/api/ping", new PingHandler()); // 客户端心跳检测
        server.createContext("/api/disconnect", new DisconnectHandler()); // 客户端主动断开通知
        
        // 文件管理 API
        server.createContext("/api/files/upload", new FileUploadHandler()); // 用户文件上传
        server.createContext("/api/files/list", new FileListHandler()); // 文件列表
        server.createContext("/api/files/download", new FileDownloadHandler()); // 文件下载
        server.createContext("/api/files/delete", new FileDeleteHandler()); // 删除文件
        server.createContext("/api/files/search", new FileSearchHandler()); // 搜索文件
        server.createContext("/api/files/quota", new FileQuotaHandler()); // 存储配额
        server.createContext("/api/files/rename", new FileRenameHandler()); // 重命名文件
        server.createContext("/api/files/move", new FileMoveHandler()); // 移动文件
        
        // 文件夹管理 API
        server.createContext("/api/folders/create", new FolderCreateHandler()); // 创建文件夹
        server.createContext("/api/folders/list", new FolderListHandler()); // 文件夹列表
        server.createContext("/api/folders/delete", new FolderDeleteHandler()); // 删除文件夹
        server.createContext("/api/folders/rename", new FolderRenameHandler()); // 重命名文件夹
        server.createContext("/api/folders/contents", new FolderContentsHandler()); // 获取文件夹内容（文件夹+文件）
        
        // 静态文件服务 (放在最后，作为默认处理器)
        server.createContext("/", new StaticFileHandler());
        
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        // 启动定时清理任务（每10秒清理超时客户端）
        startClientCleanupTask();

        // 初始化数据库（可选，未配置则跳过）
        try {
            MessageDao.init();
            UserDao.init();
            FileDao.init();
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

            String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
            
            // 验证房间秘钥是否有效
            if (!Db.isValidRoomKey(roomKey)) {
                OutputStream errOs = exchange.getResponseBody();
                String errPayload = "event: error\ndata: {\"type\":\"invalid_room_key\",\"message\":\"无效的房间秘钥\"}\n\n";
                errOs.write(errPayload.getBytes(StandardCharsets.UTF_8));
                errOs.flush();
                errOs.close();
                return;
            }

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
            Set<SseClient> clients = getRoomClients(roomKey);
            clients.add(client);
            
            // 发送连接成功事件（包含在线用户列表）
            // 在线人数应该是去重后的用户数
            java.util.List<String> users = getOnlineUsers(roomKey);
            int onlineCount = users.size();
            StringBuilder initSb = new StringBuilder();
            initSb.append("event: info\ndata: {\"type\":\"connected\",\"online\":").append(onlineCount);
            initSb.append(",\"room\":\"").append(roomKey.replace("\\", "\\\\").replace("\"", "\\\""));
            initSb.append("\",\"users\":[");
            for (int i = 0; i < users.size(); i++) {
                if (i > 0) initSb.append(",");
                initSb.append("\"").append(users.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            }
            initSb.append("]}\n\n");
            client.send(initSb.toString());
            
            // 广播在线人数更新（通知所有客户端有新用户加入）
            broadcastOnlineCount(roomKey);
            
            String displayName = nickname != null ? nickname : "anonymous";
            System.out.println("[SSE] Client connected: " + displayName + " @" + roomKey + ", online=" + onlineCount);

            // 保持连接
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // 发送心跳 (更频繁以保持连接并快速检测断开)
                    long now = System.currentTimeMillis();
                    String heartbeatPayload = ": heartbeat " + now + "\n\n";
                    if (!client.send(heartbeatPayload)) {
                        break;
                    }
                    client.updateLastActive();
                    Thread.sleep(5000); // 每 5 秒心跳，快速检测断开
                }
            } catch (InterruptedException ignored) {
            } finally {
                clients.remove(client);
                try { os.close(); } catch (IOException ignored) {}
                broadcastOnlineCount(roomKey);
                int remainingOnline = getOnlineUsers(roomKey).size();
                System.out.println("[SSE] Client disconnected: " + displayName + " @" + roomKey + ", online=" + remainingOnline);
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
            String roomKey = normalizeRoomKey(extract(json, "roomKey"));
            
            // 验证房间秘钥是否有效
            if (!Db.isValidRoomKey(roomKey)) {
                respond(exchange, 403, "{\"status\":\"error\",\"message\":\"无效的房间秘钥\"}");
                return;
            }
            
            if (name.isEmpty() || msg.isEmpty()) {
                respond(exchange, 400, "Invalid payload: name and message required");
                return;
            }
            
            // 简单的内容过滤
            name = sanitize(name, 20, false);
            msg = sanitize(msg, 500, true);
            
            String line = formatMsg(name, msg);
            broadcast(line, roomKey);
            incrementMessageCount(roomKey);

            // 持久化到数据库（若启用）
            try {
                MessageDao.save(name, msg, LocalDateTime.now(), roomKey);
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

            String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
            
            // 验证房间秘钥是否有效
            if (!Db.isValidRoomKey(roomKey)) {
                respond(exchange, 403, "{\"error\":\"无效的房间秘钥\"}");
                return;
            }

            int limit = 50;
            int offset = 0;
            long since = 0; // 增量加载时间戳
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
                            } else if (kv[0].equals("since")) {
                                since = Long.parseLong(kv[1]);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            // 如果有 since 参数，使用增量加载
            if (since > 0) {
                java.util.List<String> newMessages = MessageDao.latestSince(since, roomKey);
                StringBuilder sb = new StringBuilder();
                sb.append("{\"messages\":[");
                for (int i = 0; i < newMessages.size(); i++) {
                    if (i > 0) sb.append(',');
                    String msg = newMessages.get(i)
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t");
                    sb.append('"').append(msg).append('"');
                }
                sb.append("],\"timestamp\":").append(System.currentTimeMillis()).append("}");
                byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                return;
            }

            java.util.List<String> history = MessageDao.latest(limit, offset, roomKey);
            int totalCount = MessageDao.getTotalCount(roomKey);
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
            String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
            
            // 验证房间秘钥是否有效
            if (!Db.isValidRoomKey(roomKey)) {
                respond(exchange, 403, "{\"error\":\"无效的房间秘钥\"}");
                return;
            }
            
            // 在线人数应该是去重后的用户数
            int online = getOnlineUsers(roomKey).size();
            int totalMessages = getMessageCount(roomKey);
            
            String json = String.format(
                "{\"room\":\"%s\",\"online\":%d,\"totalMessages\":%d,\"uptime\":\"%s\",\"startTime\":\"%s\"}",
                roomKey,
                online,
                totalMessages,
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
     * 注册秘钥同时作为用户的房间秘钥
     */
    static class RegisterHandler implements HttpHandler {
        
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
            
            // 注册时传入秘钥，UserDao 会验证秘钥并保存为用户的房间秘钥
            String error = UserDao.register(username, password, nickname, secretKey);
            
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
                respond(exchange, 200, "{\"success\":true,\"token\":\"" + result.token + "\",\"nickname\":\"" + escapeJson(result.nickname) + "\",\"roomKey\":\"" + escapeJson(result.roomKey) + "\",\"isAdmin\":" + result.isAdmin + "}");
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
            UserDao.UserInfo userInfo = UserDao.validateToken(token);
            
            if (userInfo != null) {
                respond(exchange, 200, "{\"valid\":true,\"nickname\":\"" + escapeJson(userInfo.nickname) + "\",\"roomKey\":\"" + escapeJson(userInfo.roomKey) + "\",\"isAdmin\":" + userInfo.isAdmin + "}");
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
        // 不限制单文件大小，由存储配额控制总空间
        
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
                
                // 读取请求体（不限制大小）
                byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                
                // 使用二进制方式解析 multipart 数据
                byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
                
                String fileName = null;
                String senderName = null;
                String fileType = "file";
                // 优先从 URL 查询参数获取 roomKey，如果没有则从 FormData 中获取
                String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
                
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
                    } else if (headers.contains("name=\"roomKey\"")) {
                        byte[] roomBytes = new byte[contentLength];
                        System.arraycopy(bodyBytes, contentStart, roomBytes, 0, contentLength);
                        roomKey = normalizeRoomKey(new String(roomBytes, StandardCharsets.UTF_8).trim());
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
                
                // 验证房间秘钥是否有效（在解析完 FormData 后验证）
                if (!Db.isValidRoomKey(roomKey)) {
                    respond(exchange, 403, "{\"error\":\"无效的房间秘钥\"}");
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
                broadcast(line, roomKey);
                incrementMessageCount(roomKey);
                
                // 持久化到数据库
                try {
                    MessageDao.save(senderName, msgContent, LocalDateTime.now(), roomKey);
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

    private static void broadcast(String data, String roomKey) {
        String payload = "data: " + data.replace("\n", "\\n") + "\n\n";
        Set<SseClient> clients = getRoomClients(roomKey);
        synchronized (clients) {
            clients.removeIf(c -> !c.send(payload));
        }
        System.out.println("[MSG][" + roomKey + "] " + data);
    }
    
    private static void broadcastOnlineCount(String roomKey) {
        java.util.List<String> users = getOnlineUsers(roomKey);
        Set<SseClient> clients = getRoomClients(roomKey);
        // 在线人数应该是去重后的用户数，而不是连接数
        int onlineCount = users.size();
        StringBuilder sb = new StringBuilder();
        sb.append("event: online\ndata: {\"count\":").append(onlineCount);
        sb.append(",\"users\":[");
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(users.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        sb.append("]}\n\n");
        String payload = sb.toString();
        synchronized (clients) {
            clients.removeIf(c -> !c.send(payload));
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

    /**
     * 从 JSON 中提取数字值（支持整数）
     */
    private static String extractNumber(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return "";
        int colon = json.indexOf(':', idx);
        if (colon < 0) return "";
        
        // 跳过冒号后的空白字符
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) return "";
        
        // 检查是否是字符串值（带引号）
        if (json.charAt(start) == '"') {
            // 字符串值，使用 extract 方法
            return extract(json, key);
        }
        
        // 数字值，查找结束位置（逗号、}、] 或空白）
        StringBuilder result = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                break;
            }
            result.append(c);
        }
        return result.toString();
    }

    private static String formatMsg(String sender, String text) {
        String ts = LocalDateTime.now().format(TS);
        return "[" + ts + "] " + sender + ": " + text;
    }

    private static void respond(HttpExchange ex, int code, String msg) throws IOException {
        addCors(ex.getResponseHeaders());
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { 
            os.write(bytes); 
        }
    }

    private static void addCors(Headers h) {
        // 使用 set 而不是 add，防止重复添加导致 "*, *" 的问题
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static class SseClient {
        private final OutputStream out;
        private String nickname;
        private volatile long lastActiveTime;
        private volatile long connectedTime;
        
        SseClient(OutputStream out) { 
            this.out = out; 
            this.nickname = null;
            this.connectedTime = System.currentTimeMillis();
            this.lastActiveTime = this.connectedTime;
        }
        
        SseClient(OutputStream out, String nickname) { 
            this.out = out; 
            this.nickname = nickname;
            this.connectedTime = System.currentTimeMillis();
            this.lastActiveTime = this.connectedTime;
        }
        
        String getNickname() {
            return nickname;
        }
        
        long getLastActiveTime() {
            return lastActiveTime;
        }
        
        void updateLastActive() {
            this.lastActiveTime = System.currentTimeMillis();
        }
        
        boolean isStale(long timeoutMs) {
            return System.currentTimeMillis() - lastActiveTime > timeoutMs;
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
    private static java.util.List<String> getOnlineUsers(String roomKey) {
        java.util.List<String> users = new java.util.ArrayList<>();
        Set<SseClient> clients = getRoomClients(roomKey);
        synchronized (clients) {
            for (SseClient client : clients) {
                String nick = client.getNickname();
                if (nick != null && !nick.isEmpty() && !users.contains(nick)) {
                    users.add(nick);
                }
            }
        }
        return users;
    }

    private static Set<SseClient> getRoomClients(String roomKey) {
        return CLIENTS_BY_ROOM.computeIfAbsent(roomKey, k -> Collections.synchronizedSet(new HashSet<>()));
    }

    private static AtomicInteger getRoomCounter(String roomKey) {
        return ROOM_MESSAGE_COUNT.computeIfAbsent(roomKey, k -> new AtomicInteger(0));
    }

    private static void incrementMessageCount(String roomKey) {
        getRoomCounter(roomKey).incrementAndGet();
    }

    private static int getMessageCount(String roomKey) {
        return getRoomCounter(roomKey).get();
    }

    private static String normalizeRoomKey(String roomKey) {
        if (roomKey == null) return "public";
        String trimmed = roomKey.trim();
        if (trimmed.isEmpty()) return "public";
        // 仅允许字母、数字、下划线和中划线，其他字符移除
        String safe = trimmed.replaceAll("[^A-Za-z0-9_-]", "");
        if (safe.length() > MAX_ROOM_KEY_LENGTH) {
            safe = safe.substring(0, MAX_ROOM_KEY_LENGTH);
        }
        // 返回归一化后的秘钥（不在此处验证，由调用方验证）
        return safe.isEmpty() ? "public" : safe;
    }

    private static String getQueryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return "";
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && name.equals(kv[0])) {
                try {
                    return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return kv[1];
                }
            }
        }
        return "";
    }
    
    // ========== 管理员 API ==========
    
    /**
     * 验证管理员身份并返回用户信息
     */
    private static UserDao.UserInfo validateAdminToken(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        UserDao.UserInfo userInfo = UserDao.validateToken(token);
        if (userInfo == null || !userInfo.isAdmin) {
            return null;
        }
        return userInfo;
    }
    
    /**
     * 管理员-获取用户列表
     * GET /api/admin/users?roomKey=xxx
     */
    static class AdminUsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            
            // 验证管理员身份
            UserDao.UserInfo adminInfo = validateAdminToken(exchange);
            if (adminInfo == null) {
                respond(exchange, 403, "{\"error\":\"无管理员权限\"}");
                return;
            }
            
            String roomKey = getQueryParam(exchange, "roomKey");
            java.util.List<UserDao.UserListItem> users = UserDao.listUsers(roomKey.isEmpty() ? null : roomKey);
            
            StringBuilder sb = new StringBuilder();
            sb.append("{\"users\":[");
            for (int i = 0; i < users.size(); i++) {
                if (i > 0) sb.append(",");
                UserDao.UserListItem u = users.get(i);
                sb.append("{\"id\":").append(u.id);
                sb.append(",\"username\":\"").append(escapeJson(u.username)).append("\"");
                sb.append(",\"nickname\":\"").append(escapeJson(u.nickname)).append("\"");
                sb.append(",\"roomKey\":\"").append(escapeJson(u.roomKey)).append("\"");
                sb.append(",\"createdAt\":\"").append(escapeJson(u.createdAt)).append("\"");
                sb.append(",\"lastLogin\":\"").append(escapeJson(u.lastLogin)).append("\"");
                sb.append(",\"isAdmin\":").append(u.isAdmin);
                sb.append("}");
            }
            sb.append("]}");
            
            respond(exchange, 200, sb.toString());
        }
    }
    
    /**
     * 管理员-删除用户
     * POST /api/admin/user/delete
     * Body: {"userId":123}
     */
    static class AdminDeleteUserHandler implements HttpHandler {
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
            
            // 验证管理员身份
            UserDao.UserInfo adminInfo = validateAdminToken(exchange);
            if (adminInfo == null) {
                respond(exchange, 403, "{\"error\":\"无管理员权限\"}");
                return;
            }
            
            byte[] body = exchange.getRequestBody().readAllBytes();
            String json = new String(body, StandardCharsets.UTF_8).trim();
            
            long userId;
            try {
                userId = Long.parseLong(extractNumber(json, "userId"));
            } catch (NumberFormatException e) {
                respond(exchange, 400, "{\"error\":\"无效的用户ID\"}");
                return;
            }
            
            String error = UserDao.deleteUser(userId, adminInfo.username);
            if (error == null) {
                respond(exchange, 200, "{\"success\":true,\"message\":\"用户已删除\"}");
            } else {
                respond(exchange, 400, "{\"success\":false,\"error\":\"" + escapeJson(error) + "\"}");
            }
        }
    }
    
    /**
     * 管理员-更新用户信息
     * POST /api/admin/user/update
     * Body: {"userId":123,"nickname":"新昵称","roomKey":"新秘钥"}
     */
    static class AdminUpdateUserHandler implements HttpHandler {
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
            
            // 验证管理员身份
            UserDao.UserInfo adminInfo = validateAdminToken(exchange);
            if (adminInfo == null) {
                respond(exchange, 403, "{\"error\":\"无管理员权限\"}");
                return;
            }
            
            byte[] body = exchange.getRequestBody().readAllBytes();
            String json = new String(body, StandardCharsets.UTF_8).trim();
            
            long userId;
            try {
                userId = Long.parseLong(extractNumber(json, "userId"));
            } catch (NumberFormatException e) {
                respond(exchange, 400, "{\"error\":\"无效的用户ID\"}");
                return;
            }
            
            String nickname = extract(json, "nickname");
            String roomKey = extract(json, "roomKey");
            
            String error = UserDao.updateUser(userId, nickname.isEmpty() ? null : nickname, 
                                              roomKey.isEmpty() ? null : roomKey, adminInfo.username);
            if (error == null) {
                respond(exchange, 200, "{\"success\":true,\"message\":\"用户信息已更新\"}");
            } else {
                respond(exchange, 400, "{\"success\":false,\"error\":\"" + escapeJson(error) + "\"}");
            }
        }
    }
    
    /**
     * 管理员-重置用户密码
     * POST /api/admin/user/reset-password
     * Body: {"userId":123,"newPassword":"xxx"}
     */
    static class AdminResetPasswordHandler implements HttpHandler {
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
            
            // 验证管理员身份
            UserDao.UserInfo adminInfo = validateAdminToken(exchange);
            if (adminInfo == null) {
                respond(exchange, 403, "{\"error\":\"无管理员权限\"}");
                return;
            }
            
            byte[] body = exchange.getRequestBody().readAllBytes();
            String json = new String(body, StandardCharsets.UTF_8).trim();
            
            long userId;
            try {
                userId = Long.parseLong(extractNumber(json, "userId"));
            } catch (NumberFormatException e) {
                respond(exchange, 400, "{\"error\":\"无效的用户ID\"}");
                return;
            }
            
            String newPassword = extract(json, "newPassword");
            if (newPassword.isEmpty()) {
                respond(exchange, 400, "{\"error\":\"新密码不能为空\"}");
                return;
            }
            
            String error = UserDao.resetPassword(userId, newPassword, adminInfo.username);
            if (error == null) {
                respond(exchange, 200, "{\"success\":true,\"message\":\"密码已重置\"}");
            } else {
                respond(exchange, 400, "{\"success\":false,\"error\":\"" + escapeJson(error) + "\"}");
            }
        }
    }
    
    /**
     * Ping 端点 - 客户端定期调用以保持连接活跃
     * GET /api/ping?roomKey=xxx&nickname=xxx
     */
    static class PingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            
            String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
            String nickname = getQueryParam(exchange, "nickname");
            
            // 更新该用户的所有连接的最后活动时间
            Set<SseClient> clients = getRoomClients(roomKey);
            int updatedCount = 0;
            synchronized (clients) {
                for (SseClient client : clients) {
                    if (nickname != null && nickname.equals(client.getNickname())) {
                        client.updateLastActive();
                        updatedCount++;
                        // 不 break，更新所有该用户的连接
                    }
                }
            }
            
            // 在线人数应该是去重后的用户数，而不是连接数
            java.util.List<String> users = getOnlineUsers(roomKey);
            int onlineCount = users.size();
            
            StringBuilder sb = new StringBuilder();
            sb.append("{\"pong\":true,\"time\":").append(System.currentTimeMillis());
            sb.append(",\"online\":").append(onlineCount);
            sb.append(",\"found\":").append(updatedCount > 0);
            sb.append(",\"users\":[");
            for (int i = 0; i < users.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(users.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            }
            sb.append("]}\n");
            
            respond(exchange, 200, sb.toString());
        }
    }
    
    /**
     * 客户端主动断开通知
     * POST /api/disconnect
     * Body: {"roomKey":"xxx","nickname":"xxx"}
     */
    static class DisconnectHandler implements HttpHandler {
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
            
            String roomKey = normalizeRoomKey(extract(json, "roomKey"));
            String nickname = extract(json, "nickname");
            
            // 移除该用户的所有客户端连接
            Set<SseClient> clients = getRoomClients(roomKey);
            int removedCount = 0;
            synchronized (clients) {
                java.util.Iterator<SseClient> it = clients.iterator();
                while (it.hasNext()) {
                    SseClient client = it.next();
                    if (nickname != null && nickname.equals(client.getNickname())) {
                        it.remove();
                        removedCount++;
                        // 不 break，移除所有该用户的连接
                    }
                }
            }
            
            if (removedCount > 0) {
                System.out.println("[SSE] Client force disconnected: " + nickname + " @" + roomKey + ", removed " + removedCount + " connections");
                // 立即广播在线人数更新
                broadcastOnlineCount(roomKey);
            }
            
            respond(exchange, 200, "{\"success\":true,\"removed\":" + removedCount + "}");
        }
    }
    
    /**
     * 启动定时清理任务
     */
    private static void startClientCleanupTask() {
        java.util.concurrent.ScheduledExecutorService scheduler = 
            java.util.concurrent.Executors.newScheduledThreadPool(1);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                long timeout = 20000; // 20秒无活动则视为断开
                
                for (Map.Entry<String, Set<SseClient>> entry : CLIENTS_BY_ROOM.entrySet()) {
                    String roomKey = entry.getKey();
                    Set<SseClient> clients = entry.getValue();
                    int beforeSize = clients.size();
                    
                    synchronized (clients) {
                        java.util.Iterator<SseClient> it = clients.iterator();
                        while (it.hasNext()) {
                            SseClient client = it.next();
                            if (client.isStale(timeout)) {
                                it.remove();
                                System.out.println("[CLEANUP] Removed stale client: " + 
                                    client.getNickname() + " @" + roomKey);
                            }
                        }
                    }
                    
                    int afterSize = clients.size();
                    if (beforeSize != afterSize) {
                        // 有客户端被清理，广播更新
                        broadcastOnlineCount(roomKey);
                    }
                }
            } catch (Exception e) {
                System.err.println("[CLEANUP] Error during cleanup: " + e.getMessage());
            }
        }, 10, 10, java.util.concurrent.TimeUnit.SECONDS);
        
        System.out.println("[CLEANUP] Started client cleanup task (every 10s, timeout 20s)");
    }
    
    // ========== 文件管理 API ==========
    
    /**
     * 用户文件上传
     * POST /api/files/upload?roomKey=xxx&folder=/path
     */
    static class FileUploadHandler implements HttpHandler {
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
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
                String folderPath = getQueryParam(exchange, "folder");
                if (folderPath.isEmpty()) {
                    folderPath = "/";
                }
                
                // 读取上传的文件数据
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                byte[] fileData = exchange.getRequestBody().readAllBytes();
                
                // 从header获取文件名
                String fileName = exchange.getRequestHeaders().getFirst("X-File-Name");
                if (fileName == null || fileName.isEmpty()) {
                    fileName = "unnamed_" + System.currentTimeMillis();
                } else {
                    // URL解码文件名（前端使用encodeURIComponent编码）
                    try {
                        fileName = java.net.URLDecoder.decode(fileName, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        // 解码失败则使用原始值
                    }
                }
                
                // 保存文件
                FileDao.FileInfo fileInfo = FileManager.saveFile(
                    userInfo.userId, roomKey, fileName, folderPath, fileData, contentType
                );
                
                // 返回文件信息
                StringBuilder sb = new StringBuilder();
                sb.append("{\"success\":true,\"file\":{");
                sb.append("\"id\":").append(fileInfo.id);
                sb.append(",\"name\":\"").append(escapeJson(fileInfo.fileName)).append("\"");
                sb.append(",\"size\":").append(fileInfo.fileSize);
                sb.append(",\"type\":\"").append(escapeJson(fileInfo.fileType)).append("\"");
                sb.append(",\"folder\":\"").append(escapeJson(fileInfo.folderPath)).append("\"");
                sb.append(",\"createdAt\":\"").append(fileInfo.createdAt).append("\"");
                sb.append("}}");
                
                respond(exchange, 200, sb.toString());
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 获取用户文件列表
     * GET /api/files/list?roomKey=xxx&folder=/path
     */
    static class FileListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
                String folderPath = getQueryParam(exchange, "folder");
                if (folderPath.isEmpty()) {
                    folderPath = "/";
                }
                
                // 获取文件列表
                List<FileDao.FileInfo> files = FileManager.getUserFiles(userInfo.userId, folderPath, roomKey);
                // 获取文件夹列表
                List<FileDao.FolderInfo> folders = FileManager.getUserFolders(userInfo.userId, folderPath, roomKey);
                
                // 构建JSON响应
                StringBuilder sb = new StringBuilder();
                sb.append("{\"success\":true,\"folders\":[");
                for (int i = 0; i < folders.size(); i++) {
                    if (i > 0) sb.append(",");
                    FileDao.FolderInfo folder = folders.get(i);
                    // 获取文件夹内的文件数量
                    int fileCount = FileDao.getFileCountInFolder(userInfo.userId, folder.folderPath, roomKey);
                    sb.append("{\"id\":").append(folder.id);
                    sb.append(",\"name\":\"").append(escapeJson(folder.folderName)).append("\"");
                    sb.append(",\"path\":\"").append(escapeJson(folder.folderPath)).append("\"");
                    sb.append(",\"parentPath\":\"").append(escapeJson(folder.parentPath)).append("\"");
                    sb.append(",\"fileCount\":").append(fileCount);
                    sb.append(",\"createdAt\":\"").append(folder.createdAt).append("\"");
                    sb.append("}");
                }
                sb.append("],\"files\":[");
                for (int i = 0; i < files.size(); i++) {
                    if (i > 0) sb.append(",");
                    FileDao.FileInfo f = files.get(i);
                    sb.append("{\"id\":").append(f.id);
                    sb.append(",\"name\":\"").append(escapeJson(f.fileName)).append("\"");
                    sb.append(",\"size\":").append(f.fileSize);
                    sb.append(",\"sizeFormatted\":\"").append(FileManager.formatFileSize(f.fileSize)).append("\"");
                    sb.append(",\"type\":\"").append(escapeJson(f.fileType != null ? f.fileType : "")).append("\"");
                    sb.append(",\"extension\":\"").append(escapeJson(f.fileExtension != null ? f.fileExtension : "")).append("\"");
                    sb.append(",\"folder\":\"").append(escapeJson(f.folderPath)).append("\"");
                    sb.append(",\"downloads\":").append(f.downloadCount);
                    sb.append(",\"createdAt\":\"").append(f.createdAt).append("\"");
                    sb.append("}");
                }
                sb.append("]}");
                
                respond(exchange, 200, sb.toString());
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 下载文件
     * GET /api/files/download?roomKey=xxx&fileId=123&token=xxx
     * 支持两种认证方式：
     * 1. Authorization header (用于 fetch 请求)
     * 2. URL 参数 token (用于浏览器直接下载，显示进度)
     */
    static class FileDownloadHandler implements HttpHandler {
        // 异步线程池用于更新下载计数
        private static final java.util.concurrent.ExecutorService downloadCountExecutor = 
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "download-count-updater");
                t.setDaemon(true);
                return t;
            });
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            
            try {
                // 支持两种认证方式：header 或 URL 参数
                String token = null;
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                } else {
                    // 从 URL 参数获取 token（用于浏览器直接下载）
                    token = getQueryParam(exchange, "token");
                }
                
                if (token == null || token.isEmpty()) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
                
                // 验证 roomKey 是否有效
                if (!db.Db.isValidRoomKey(roomKey)) {
                    respond(exchange, 400, "{\"error\":\"无效的房间秘钥\"}");
                    return;
                }
                
                // 验证 roomKey 与用户绑定的房间一致
                if (userInfo.roomKey != null && !userInfo.roomKey.isEmpty() 
                    && !roomKey.equals(normalizeRoomKey(userInfo.roomKey))) {
                    respond(exchange, 403, "{\"error\":\"无权限访问该房间的文件\"}");
                    return;
                }
                
                String fileIdStr = getQueryParam(exchange, "fileId");
                if (fileIdStr == null || fileIdStr.isEmpty()) {
                    respond(exchange, 400, "{\"error\":\"缺少文件ID\"}");
                    return;
                }
                long fileId = Long.parseLong(fileIdStr);
                
                // 获取文件信息
                FileDao.FileInfo fileInfo = FileDao.getFileById(fileId, roomKey);
                if (fileInfo == null) {
                    respond(exchange, 404, "{\"error\":\"文件不存在\"}");
                    return;
                }
                
                // 同一房间的成员都可以访问该房间的文件（群文件共享）
                // 不再检查 fileInfo.userId != userInfo.userId
                
                // 检查文件是否存在
                java.nio.file.Path filePath = java.nio.file.Paths.get(fileInfo.filePath);
                if (!java.nio.file.Files.exists(filePath)) {
                    respond(exchange, 404, "{\"error\":\"文件物理存储不存在\"}");
                    return;
                }
                
                // 获取文件大小
                long fileSize = java.nio.file.Files.size(filePath);
                
                // 异步更新下载次数（不阻塞响应）
                final long fid = fileId;
                final String rk = roomKey;
                downloadCountExecutor.submit(() -> {
                    try {
                        FileDao.incrementDownloadCount(fid, rk);
                    } catch (Exception e) {
                        System.err.println("更新下载次数失败: " + e.getMessage());
                    }
                });
                
                // 设置响应头
                exchange.getResponseHeaders().add("Content-Type", 
                    fileInfo.fileType != null ? fileInfo.fileType : "application/octet-stream");
                exchange.getResponseHeaders().add("Content-Disposition", 
                    "attachment; filename=\"" + fileInfo.fileName + "\"");
                
                // 使用流式传输发送文件（避免一次性读入内存）
                exchange.sendResponseHeaders(200, fileSize);
                try (InputStream is = java.nio.file.Files.newInputStream(filePath);
                     OutputStream os = exchange.getResponseBody()) {
                    byte[] buffer = new byte[8192]; // 8KB 缓冲区
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 删除文件
     * POST /api/files/delete
     * Body: {"roomKey":"xxx","fileId":123}
     */
    static class FileDeleteHandler implements HttpHandler {
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
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                // 解析请求体
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String roomKey = normalizeRoomKey(extract(body, "roomKey"));
                String fileIdStr = extractNumber(body, "fileId");
                if (fileIdStr.isEmpty()) {
                    respond(exchange, 400, "{\"error\":\"缺少文件ID\"}");
                    return;
                }
                long fileId = Long.parseLong(fileIdStr);
                
                // 删除文件
                boolean deleted = FileManager.deleteFile(fileId, userInfo.userId, roomKey);
                
                if (deleted) {
                    respond(exchange, 200, "{\"success\":true}");
                } else {
                    respond(exchange, 404, "{\"error\":\"文件不存在或无权限删除\"}");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 搜索文件
     * GET /api/files/search?roomKey=xxx&keyword=test
     */
    static class FileSearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
                String keyword = getQueryParam(exchange, "keyword");
                
                // 搜索文件
                List<FileDao.FileInfo> files = FileManager.searchFiles(userInfo.userId, keyword, roomKey);
                
                // 构建JSON响应
                StringBuilder sb = new StringBuilder();
                sb.append("{\"success\":true,\"files\":[");
                for (int i = 0; i < files.size(); i++) {
                    if (i > 0) sb.append(",");
                    FileDao.FileInfo f = files.get(i);
                    sb.append("{\"id\":").append(f.id);
                    sb.append(",\"name\":\"").append(escapeJson(f.fileName)).append("\"");
                    sb.append(",\"size\":").append(f.fileSize);
                    sb.append(",\"sizeFormatted\":\"").append(FileManager.formatFileSize(f.fileSize)).append("\"");
                    sb.append(",\"type\":\"").append(escapeJson(f.fileType != null ? f.fileType : "")).append("\"");
                    sb.append(",\"folder\":\"").append(escapeJson(f.folderPath)).append("\"");
                    sb.append(",\"createdAt\":\"").append(f.createdAt).append("\"");
                    sb.append("}");
                }
                sb.append("]}");
                
                respond(exchange, 200, sb.toString());
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 获取用户存储配额信息
     * GET /api/files/quota?roomKey=xxx
     */
    static class FileQuotaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
                
                // 获取存储信息
                FileDao.StorageQuota quota = FileManager.getUserStorageInfo(userInfo.userId, roomKey);
                
                // 构建JSON响应
                StringBuilder sb = new StringBuilder();
                sb.append("{\"success\":true,\"quota\":{");
                sb.append("\"total\":").append(quota.totalQuota);
                sb.append(",\"used\":").append(quota.usedSpace);
                sb.append(",\"available\":").append(quota.totalQuota - quota.usedSpace);
                sb.append(",\"fileCount\":").append(quota.fileCount);
                sb.append(",\"totalFormatted\":\"").append(FileManager.formatFileSize(quota.totalQuota)).append("\"");
                sb.append(",\"usedFormatted\":\"").append(FileManager.formatFileSize(quota.usedSpace)).append("\"");
                sb.append(",\"availableFormatted\":\"").append(FileManager.formatFileSize(quota.totalQuota - quota.usedSpace)).append("\"");
                sb.append(",\"usagePercent\":").append((quota.usedSpace * 100) / quota.totalQuota);
                sb.append("}}");
                
                respond(exchange, 200, sb.toString());
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 重命名文件
     * POST /api/files/rename
     * Body: {"roomKey":"xxx","fileId":123,"newName":"新文件名"}
     */
    static class FileRenameHandler implements HttpHandler {
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
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                // 解析请求体
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String roomKey = normalizeRoomKey(extract(body, "roomKey"));
                String fileIdStr = extractNumber(body, "fileId");
                String newName = extract(body, "newName");
                
                if (fileIdStr.isEmpty() || newName.isEmpty()) {
                    respond(exchange, 400, "{\"error\":\"缺少必要参数\"}");
                    return;
                }
                
                long fileId = Long.parseLong(fileIdStr);
                
                // 重命名文件
                boolean renamed = FileManager.renameFile(fileId, userInfo.userId, newName, roomKey);
                
                if (renamed) {
                    respond(exchange, 200, "{\"success\":true}");
                } else {
                    respond(exchange, 404, "{\"error\":\"文件不存在或无权限\"}");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 移动文件到其他文件夹
     * POST /api/files/move
     * Body: {"roomKey":"xxx","fileId":123,"targetFolder":"/newPath"}
     */
    static class FileMoveHandler implements HttpHandler {
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
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                // 解析请求体
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String roomKey = normalizeRoomKey(extract(body, "roomKey"));
                String fileIdStr = extractNumber(body, "fileId");
                String targetFolder = extract(body, "targetFolder");
                
                if (fileIdStr.isEmpty()) {
                    respond(exchange, 400, "{\"error\":\"缺少文件ID\"}");
                    return;
                }
                
                long fileId = Long.parseLong(fileIdStr);
                if (targetFolder.isEmpty()) {
                    targetFolder = "/";
                }
                
                // 移动文件
                boolean moved = FileManager.moveFile(fileId, userInfo.userId, targetFolder, roomKey);
                
                if (moved) {
                    respond(exchange, 200, "{\"success\":true}");
                } else {
                    respond(exchange, 404, "{\"error\":\"文件不存在或无权限\"}");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    // ========== 文件夹管理 API ==========
    
    /**
     * 创建文件夹
     * POST /api/folders/create
     * Body: {"roomKey":"xxx","folderName":"文件夹名","parentPath":"/"}
     */
    static class FolderCreateHandler implements HttpHandler {
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
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                // 解析请求体
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String roomKey = normalizeRoomKey(extract(body, "roomKey"));
                String folderName = extract(body, "folderName");
                String parentPath = extract(body, "parentPath");
                
                if (folderName.isEmpty()) {
                    respond(exchange, 400, "{\"error\":\"文件夹名称不能为空\"}");
                    return;
                }
                
                if (parentPath.isEmpty()) {
                    parentPath = "/";
                }
                
                // 创建文件夹
                FileDao.FolderInfo folder = FileManager.createFolder(userInfo.userId, folderName, parentPath, roomKey);
                
                // 返回文件夹信息
                StringBuilder sb = new StringBuilder();
                sb.append("{\"success\":true,\"folder\":{");
                sb.append("\"id\":").append(folder.id);
                sb.append(",\"name\":\"").append(escapeJson(folder.folderName)).append("\"");
                sb.append(",\"path\":\"").append(escapeJson(folder.folderPath)).append("\"");
                sb.append(",\"parentPath\":\"").append(escapeJson(folder.parentPath)).append("\"");
                sb.append(",\"createdAt\":\"").append(folder.createdAt).append("\"");
                sb.append("}}");
                
                respond(exchange, 200, sb.toString());
                
            } catch (Exception e) {
                e.printStackTrace();
                String errorMsg = e.getMessage();
                // MySQL: "Duplicate entry", SQL Server: "Violation of UNIQUE KEY constraint" 或 "Cannot insert duplicate key"
                if (errorMsg != null && (errorMsg.contains("Duplicate entry") || 
                    errorMsg.contains("UNIQUE KEY constraint") || 
                    errorMsg.contains("duplicate key"))) {
                    respond(exchange, 400, "{\"error\":\"文件夹已存在\"}");
                } else {
                    respond(exchange, 500, "{\"error\":\"" + escapeJson(errorMsg) + "\"}");
                }
            }
        }
    }
    
    /**
     * 获取文件夹列表
     * GET /api/folders/list?roomKey=xxx&parentPath=/
     */
    static class FolderListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
                String parentPath = getQueryParam(exchange, "parentPath");
                if (parentPath.isEmpty()) {
                    parentPath = "/";
                }
                
                // 获取文件夹列表
                List<FileDao.FolderInfo> folders = FileManager.getUserFolders(userInfo.userId, parentPath, roomKey);
                
                // 构建JSON响应
                StringBuilder sb = new StringBuilder();
                sb.append("{\"success\":true,\"folders\":[");
                for (int i = 0; i < folders.size(); i++) {
                    if (i > 0) sb.append(",");
                    FileDao.FolderInfo f = folders.get(i);
                    sb.append("{\"id\":").append(f.id);
                    sb.append(",\"name\":\"").append(escapeJson(f.folderName)).append("\"");
                    sb.append(",\"path\":\"").append(escapeJson(f.folderPath)).append("\"");
                    sb.append(",\"parentPath\":\"").append(escapeJson(f.parentPath)).append("\"");
                    sb.append(",\"createdAt\":\"").append(f.createdAt).append("\"");
                    sb.append("}");
                }
                sb.append("]}");
                
                respond(exchange, 200, sb.toString());
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 删除文件夹
     * POST /api/folders/delete
     * Body: {"roomKey":"xxx","folderId":123,"recursive":false}
     */
    static class FolderDeleteHandler implements HttpHandler {
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
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                // 解析请求体
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String roomKey = normalizeRoomKey(extract(body, "roomKey"));
                String folderIdStr = extractNumber(body, "folderId");
                String folderPath = extract(body, "folderPath");
                boolean recursive = "true".equals(extract(body, "recursive"));
                
                boolean deleted = false;
                
                if (!folderPath.isEmpty() && recursive) {
                    // 递归删除文件夹及其所有内容
                    deleted = FileManager.deleteFolderRecursive(userInfo.userId, folderPath, roomKey);
                } else if (!folderIdStr.isEmpty()) {
                    // 普通删除（需要文件夹为空）
                    long folderId = Long.parseLong(folderIdStr);
                    
                    // 先检查文件夹是否为空
                    if (!folderPath.isEmpty()) {
                        boolean isEmpty = FileManager.isFolderEmpty(userInfo.userId, folderPath, roomKey);
                        if (!isEmpty) {
                            respond(exchange, 400, "{\"error\":\"文件夹不为空，请先删除其中的文件和子文件夹，或使用递归删除\"}");
                            return;
                        }
                    }
                    
                    deleted = FileManager.deleteFolder(folderId, userInfo.userId, roomKey);
                } else {
                    respond(exchange, 400, "{\"error\":\"缺少文件夹ID或路径\"}");
                    return;
                }
                
                if (deleted) {
                    respond(exchange, 200, "{\"success\":true}");
                } else {
                    respond(exchange, 404, "{\"error\":\"文件夹不存在或无权限\"}");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    
    /**
     * 重命名文件夹
     * POST /api/folders/rename
     * Body: {"roomKey":"xxx","folderId":123,"newName":"新名称"}
     */
    static class FolderRenameHandler implements HttpHandler {
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
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                // 解析请求体
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String roomKey = normalizeRoomKey(extract(body, "roomKey"));
                String folderIdStr = extractNumber(body, "folderId");
                String newName = extract(body, "newName");
                
                if (folderIdStr.isEmpty()) {
                    respond(exchange, 400, "{\"error\":\"缺少文件夹ID\"}");
                    return;
                }
                
                if (newName.isEmpty()) {
                    respond(exchange, 400, "{\"error\":\"新名称不能为空\"}");
                    return;
                }
                
                long folderId = Long.parseLong(folderIdStr);
                
                boolean renamed = FileManager.renameFolder(folderId, userInfo.userId, newName, roomKey);
                
                if (renamed) {
                    respond(exchange, 200, "{\"success\":true}");
                } else {
                    respond(exchange, 404, "{\"error\":\"文件夹不存在或无权限\"}");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("Duplicate entry") || 
                    errorMsg.contains("UNIQUE KEY constraint") || 
                    errorMsg.contains("duplicate key"))) {
                    respond(exchange, 400, "{\"error\":\"文件夹名称已存在\"}");
                } else {
                    respond(exchange, 500, "{\"error\":\"" + escapeJson(errorMsg) + "\"}");
                }
            }
        }
    }
    
    /**
     * 获取文件夹内容（文件夹 + 文件）
     * GET /api/folders/contents?roomKey=xxx&path=/
     */
    static class FolderContentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            addCors(exchange.getResponseHeaders());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            
            try {
                // 验证用户token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    respond(exchange, 401, "{\"error\":\"未授权\"}");
                    return;
                }
                String token = authHeader.substring(7);
                UserDao.UserInfo userInfo = UserDao.validateToken(token);
                if (userInfo == null) {
                    respond(exchange, 401, "{\"error\":\"无效的token\"}");
                    return;
                }
                
                String roomKey = normalizeRoomKey(getQueryParam(exchange, "roomKey"));
                String path = getQueryParam(exchange, "path");
                if (path.isEmpty()) {
                    path = "/";
                }
                
                // 获取子文件夹
                List<FileDao.FolderInfo> folders = FileManager.getUserFolders(userInfo.userId, path, roomKey);
                // 获取文件
                List<FileDao.FileInfo> files = FileManager.getUserFiles(userInfo.userId, path, roomKey);
                
                // 构建JSON响应
                StringBuilder sb = new StringBuilder();
                sb.append("{\"success\":true,\"currentPath\":\"").append(escapeJson(path)).append("\",");
                
                // 文件夹列表
                sb.append("\"folders\":[");
                for (int i = 0; i < folders.size(); i++) {
                    if (i > 0) sb.append(",");
                    FileDao.FolderInfo f = folders.get(i);
                    sb.append("{\"id\":").append(f.id);
                    sb.append(",\"name\":\"").append(escapeJson(f.folderName)).append("\"");
                    sb.append(",\"path\":\"").append(escapeJson(f.folderPath)).append("\"");
                    sb.append(",\"type\":\"folder\"");
                    sb.append(",\"createdAt\":\"").append(f.createdAt).append("\"");
                    sb.append("}");
                }
                sb.append("],");
                
                // 文件列表
                sb.append("\"files\":[");
                for (int i = 0; i < files.size(); i++) {
                    if (i > 0) sb.append(",");
                    FileDao.FileInfo f = files.get(i);
                    sb.append("{\"id\":").append(f.id);
                    sb.append(",\"name\":\"").append(escapeJson(f.fileName)).append("\"");
                    sb.append(",\"size\":").append(f.fileSize);
                    sb.append(",\"sizeFormatted\":\"").append(FileManager.formatFileSize(f.fileSize)).append("\"");
                    sb.append(",\"type\":\"file\"");
                    sb.append(",\"mimeType\":\"").append(escapeJson(f.fileType != null ? f.fileType : "")).append("\"");
                    sb.append(",\"extension\":\"").append(escapeJson(f.fileExtension != null ? f.fileExtension : "")).append("\"");
                    sb.append(",\"folder\":\"").append(escapeJson(f.folderPath)).append("\"");
                    sb.append(",\"downloads\":").append(f.downloadCount);
                    sb.append(",\"createdAt\":\"").append(f.createdAt).append("\"");
                    sb.append("}");
                }
                sb.append("]}");
                
                respond(exchange, 200, sb.toString());
                
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
}
