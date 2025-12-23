/**
 * 练习 5.5 参考答案：消息历史功能
 * 
 * 这个文件展示了如何实现消息历史功能
 * 新用户加入时，可以看到最近的聊天记录
 */
package solutions;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MessageHistoryExample {
    
    // 客户端列表
    private static final List<OutputStream> clients = new CopyOnWriteArrayList<>();
    
    // 消息历史
    private static final List<String> messageHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 10;  // 最多保存10条
    
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(7070), 0);
        
        server.createContext("/events", new EventsHandler());
        server.createContext("/send", new SendHandler());
        
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.println("消息历史示例服务器启动: http://localhost:7070");
    }
    
    /**
     * SSE 处理器 - 带历史消息发送
     */
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
            
            // 1. 先发送历史消息
            sendHistory(os);
            
            // 2. 发送分隔提示
            sendSystemMessage(os, "--- 以上是历史消息 ---");
            
            // 3. 添加到客户端列表
            clients.add(os);
            
            System.out.println("新客户端连接，发送了 " + messageHistory.size() + " 条历史消息");
        }
        
        /**
         * 发送历史消息给新连接的客户端
         */
        private void sendHistory(OutputStream os) {
            synchronized (messageHistory) {
                for (String message : messageHistory) {
                    try {
                        String data = "data: " + message + "\n\n";
                        os.write(data.getBytes("UTF-8"));
                        os.flush();
                    } catch (IOException e) {
                        // 发送失败，客户端可能已断开
                        break;
                    }
                }
            }
        }
        
        /**
         * 发送系统消息
         */
        private void sendSystemMessage(OutputStream os, String text) {
            try {
                String message = "{\"user\":\"系统\",\"message\":\"" + text + "\"}";
                String data = "data: " + message + "\n\n";
                os.write(data.getBytes("UTF-8"));
                os.flush();
            } catch (IOException e) {
                // 忽略
            }
        }
    }
    
    /**
     * 消息发送处理器
     */
    static class SendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            String body = new String(
                exchange.getRequestBody().readAllBytes(), 
                "UTF-8"
            );
            
            // 保存到历史
            saveToHistory(body);
            
            // 广播
            broadcast(body);
            
            exchange.sendResponseHeaders(200, -1);
        }
    }
    
    /**
     * 保存消息到历史
     * 使用 synchronized 保证线程安全
     */
    private static void saveToHistory(String message) {
        synchronized (messageHistory) {
            messageHistory.add(message);
            
            // 超过最大数量则移除最早的
            while (messageHistory.size() > MAX_HISTORY) {
                messageHistory.remove(0);
            }
            
            System.out.println("历史消息数: " + messageHistory.size());
        }
    }
    
    /**
     * 广播消息给所有客户端
     */
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
                System.out.println("客户端断开，剩余: " + clients.size());
            }
        }
    }
}

/*
 * 知识点总结：
 * 
 * 1. 消息历史存储
 *    - 使用 ArrayList 存储消息
 *    - 限制最大数量，防止内存溢出
 *    - 使用 synchronized 保证线程安全
 * 
 * 2. 历史发送时机
 *    - 在客户端连接时（EventsHandler.handle）
 *    - 在加入客户端列表之前发送
 *    - 这样可以保证先收到历史，再收到新消息
 * 
 * 3. 扩展思考
 *    - 如果要持久化怎么办？（答：存到文件或数据库）
 *    - 如果消息很多怎么办？（答：分页加载）
 *    - 如果多个房间怎么办？（答：Map<房间, List<消息>>）
 */
