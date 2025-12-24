import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * 简易 HTTP + SSE 聊天服务器，便于前端（浏览器）连接。
 * 端点：
 *  - GET  /events   : SSE 事件流，推送聊天消息。
 *  - POST /send     : JSON {"name":"昵称","message":"内容"}
 * 跨域已放开，适合本机调试；生产请收紧。
 */
public class HttpChatServer {
    private static final int PORT = 8080;
    private static final Set<SseClient> CLIENTS = Collections.synchronizedSet(new HashSet<>());
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) throws IOException {
        int port = PORT;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/events", new EventsHandler());
        server.createContext("/send", new SendHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("[HTTP] Chat SSE server started at http://localhost:" + port);
    }

    // SSE 连接
    static class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers h = exchange.getResponseHeaders();
            addCors(h);
            h.add("Content-Type", "text/event-stream; charset=utf-8");
            h.add("Cache-Control", "no-cache");
            h.add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            SseClient client = new SseClient(os);
            CLIENTS.add(client);
            client.send("event: info\ndata: {\"msg\":\"connected\"}\n\n");
            System.out.println("[HTTP] client connected, total=" + CLIENTS.size());

            // 保持阻塞，直到对端关闭
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ignored) {
            } finally {
                CLIENTS.remove(client);
                try { os.close(); } catch (IOException ignored) {}
                System.out.println("[HTTP] client disconnected, total=" + CLIENTS.size());
            }
        }
    }

    // 发送消息
    static class SendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "method not allowed");
                return;
            }
            addCors(exchange.getResponseHeaders());

            byte[] body = exchange.getRequestBody().readAllBytes();
            String json = new String(body, StandardCharsets.UTF_8).trim();
            // 非严格 JSON 解析，简单获取 name/message
            String name = extract(json, "name");
            String msg = extract(json, "message");
            if (name.isEmpty() || msg.isEmpty()) {
                respond(exchange, 400, "invalid payload");
                return;
            }
            String line = formatMsg(name, msg);
            broadcast(line);
            respond(exchange, 200, "ok");
        }
    }

    private static void broadcast(String data) {
        String payload = "data: " + data.replace("\n", "\\n") + "\n\n";
        synchronized (CLIENTS) {
            CLIENTS.removeIf(c -> !c.send(payload));
        }
        System.out.println("[HTTP] broadcast: " + data);
    }

    private static String extract(String json, String key) {
        // 极简提取："key":"value"
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
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void addCors(Headers h) {
        h.add("Access-Control-Allow-Origin", "*");
        h.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static class SseClient {
        private final OutputStream out;
        SseClient(OutputStream out) { this.out = out; }
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
