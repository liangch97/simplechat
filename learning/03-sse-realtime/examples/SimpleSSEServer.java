import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 【学习示例】最简单的 SSE 服务器
 * 
 * 每秒向客户端推送当前时间，演示 SSE 的基本原理。
 * 
 * 运行方式：
 *   javac SimpleSSEServer.java
 *   java SimpleSSEServer
 * 
 * 测试方式：
 *   1. 浏览器打开 http://localhost:8080
 *   2. 或打开 sse-client.html
 */
public class SimpleSSEServer {
    
    private static final int PORT = 8080;
    
    public static void main(String[] args) throws IOException {
        System.out.println("╔═══════════════════════════════════╗");
        System.out.println("║      简单 SSE 服务器               ║");
        System.out.println("╚═══════════════════════════════════╝");
        
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // 首页 - 返回测试页面
        server.createContext("/", new PageHandler());
        
        // SSE 端点 - 持续推送时间
        server.createContext("/events", new SSEHandler());
        
        server.start();
        System.out.println("服务器已启动: http://localhost:" + PORT);
        System.out.println("SSE 端点: http://localhost:" + PORT + "/events");
    }
    
    /**
     * SSE 处理器 - 核心代码
     */
    static class SSEHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("新的 SSE 连接");
            
            // ========== 关键：设置 SSE 响应头 ==========
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            // 跨域支持（开发时使用）
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            // 发送响应头，0 表示响应体长度未知（流式传输）
            exchange.sendResponseHeaders(200, 0);
            
            // 获取输出流
            OutputStream os = exchange.getResponseBody();
            
            try {
                int count = 0;
                // 持续推送消息
                while (true) {
                    count++;
                    
                    // 获取当前时间
                    String time = LocalTime.now().format(
                        DateTimeFormatter.ofPattern("HH:mm:ss")
                    );
                    
                    // ========== 关键：SSE 消息格式 ==========
                    // data: 消息内容\n\n
                    String message = "data: [" + count + "] 服务器时间: " + time + "\n\n";
                    
                    // 发送消息
                    os.write(message.getBytes("UTF-8"));
                    os.flush();  // 必须刷新！否则数据会缓存
                    
                    System.out.println("推送: " + time);
                    
                    // 每秒推送一次
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                System.out.println("连接中断");
            } catch (IOException e) {
                System.out.println("客户端断开连接");
            } finally {
                try { os.close(); } catch (IOException ignored) {}
            }
        }
    }
    
    /**
     * 首页处理器 - 返回测试页面
     */
    static class PageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>SSE 测试</title>
                    <style>
                        body { font-family: Arial; padding: 20px; }
                        #messages { 
                            background: #f5f5f5; 
                            padding: 20px; 
                            height: 300px; 
                            overflow-y: auto;
                            border-radius: 8px;
                        }
                        .msg { padding: 5px 0; border-bottom: 1px solid #ddd; }
                        .status { padding: 10px; margin: 10px 0; border-radius: 4px; }
                        .connected { background: #d4edda; color: #155724; }
                        .disconnected { background: #f8d7da; color: #721c24; }
                    </style>
                </head>
                <body>
                    <h1>🔴 SSE 实时推送演示</h1>
                    <div id="status" class="status disconnected">未连接</div>
                    <div id="messages"></div>
                    
                    <script>
                        const statusDiv = document.getElementById('status');
                        const messagesDiv = document.getElementById('messages');
                        
                        // 创建 SSE 连接
                        const eventSource = new EventSource('/events');
                        
                        eventSource.onopen = function() {
                            statusDiv.textContent = '✅ 已连接';
                            statusDiv.className = 'status connected';
                        };
                        
                        eventSource.onmessage = function(event) {
                            const div = document.createElement('div');
                            div.className = 'msg';
                            div.textContent = event.data;
                            messagesDiv.appendChild(div);
                            messagesDiv.scrollTop = messagesDiv.scrollHeight;
                        };
                        
                        eventSource.onerror = function() {
                            statusDiv.textContent = '❌ 连接断开';
                            statusDiv.className = 'status disconnected';
                        };
                    </script>
                </body>
                </html>
                """;
            
            byte[] bytes = html.getBytes("UTF-8");
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
 * 1. SSE 响应头（三个必需）
 *    Content-Type: text/event-stream  ← 告诉浏览器这是SSE
 *    Cache-Control: no-cache          ← 禁止缓存
 *    Connection: keep-alive           ← 保持连接
 * 
 * 2. 消息格式
 *    data: 消息内容\n\n
 *    - 必须以 "data: " 开头
 *    - 必须以两个换行 "\n\n" 结束
 *    - 多行内容用多个 "data:" 行
 * 
 * 3. flush() 很重要
 *    - 不调用 flush()，数据会在缓冲区
 *    - 客户端收不到实时消息
 * 
 * 4. 连接保持
 *    - sendResponseHeaders(200, 0) 中的 0
 *    - 表示响应体长度未知
 *    - 连接会一直保持
 * 
 * 5. 客户端断开检测
 *    - 当客户端关闭页面
 *    - os.write() 会抛出 IOException
 *    - 我们就知道客户端断开了
 * 
 * 🔧 思考：
 * - 如何让多个客户端都收到消息？（广播）
 * - 下一个示例会解决这个问题！
 */
