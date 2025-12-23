/**
 * 练习 5.2 参考答案：添加在线人数显示
 * 
 * 这个文件展示了如何添加在线人数功能
 */
package solutions;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class OnlineCountExample {
    
    // 客户端列表
    private static final List<OutputStream> clients = new CopyOnWriteArrayList<>();
    
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(7070), 0);
        
        // 原有端点
        server.createContext("/events", new EventsHandler());
        server.createContext("/send", new SendHandler());
        
        // 新增：在线人数端点
        server.createContext("/online", new OnlineHandler());
        
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.println("服务器启动: http://localhost:7070");
    }
    
    /**
     * 在线人数处理器
     * 返回当前连接的客户端数量
     */
    static class OnlineHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置 CORS 头
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Access-Control-Allow-Origin", "*");
            
            // 构造响应
            String response = String.format("{\"count\":%d}", clients.size());
            
            // 发送响应
            byte[] bytes = response.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
    
    // SSE 处理器
    static class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "text/event-stream");
            headers.set("Cache-Control", "no-cache");
            headers.set("Connection", "keep-alive");
            headers.set("Access-Control-Allow-Origin", "*");
            
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            
            // 添加到客户端列表
            clients.add(os);
            System.out.println("客户端连接，当前在线: " + clients.size());
            
            // 广播有人加入
            broadcastOnlineCount();
        }
    }
    
    // 发送处理器
    static class SendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            // 读取请求体
            String body = new String(
                exchange.getRequestBody().readAllBytes(), 
                "UTF-8"
            );
            
            // 广播消息
            broadcast(body);
            
            // 返回成功
            exchange.sendResponseHeaders(200, -1);
        }
    }
    
    // 广播消息
    private static void broadcast(String message) {
        String data = "data: " + message + "\n\n";
        
        Iterator<OutputStream> it = clients.iterator();
        while (it.hasNext()) {
            OutputStream os = it.next();
            try {
                os.write(data.getBytes("UTF-8"));
                os.flush();
            } catch (IOException e) {
                it.remove();
                System.out.println("客户端断开，当前在线: " + clients.size());
                // 广播人数变化
                try {
                    broadcastOnlineCount();
                } catch (Exception ex) {}
            }
        }
    }
    
    /**
     * 广播在线人数变化
     * 使用 SSE 的自定义事件类型
     */
    private static void broadcastOnlineCount() {
        String data = "event: online\ndata: {\"count\":" + clients.size() + "}\n\n";
        
        for (OutputStream os : clients) {
            try {
                os.write(data.getBytes("UTF-8"));
                os.flush();
            } catch (IOException e) {
                // 忽略
            }
        }
    }
}

/*
 * 前端代码修改示例：
 * 
 * HTML 添加：
 * <div id="online-count">在线: 0</div>
 * 
 * JavaScript 添加：
 * 
 * // 方式1: 使用 SSE 自定义事件（推荐）
 * eventSource.addEventListener('online', (event) => {
 *     const data = JSON.parse(event.data);
 *     document.getElementById('online-count').textContent = '在线: ' + data.count;
 * });
 * 
 * // 方式2: 定时轮询
 * setInterval(async () => {
 *     try {
 *         const res = await fetch('/online');
 *         const data = await res.json();
 *         document.getElementById('online-count').textContent = '在线: ' + data.count;
 *     } catch (e) {
 *         console.error('获取在线人数失败');
 *     }
 * }, 5000);
 */
