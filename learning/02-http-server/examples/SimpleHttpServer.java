import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * 【学习示例】使用 Java 内置 HttpServer
 * 
 * Java 自带 com.sun.net.httpserver 包，
 * 可以更方便地创建 HTTP 服务器。
 * 
 * 运行后访问: http://localhost:8080
 * 
 * 知识点：
 *   1. HttpServer 类的使用
 *   2. HttpHandler 处理器模式
 *   3. HttpExchange 请求/响应对象
 */
public class SimpleHttpServer {
    
    private static final int PORT = 8080;
    
    public static void main(String[] args) throws IOException {
        System.out.println("╔═══════════════════════════════════╗");
        System.out.println("║    Java HttpServer 示例            ║");
        System.out.println("╚═══════════════════════════════════╝");
        
        // 创建 HTTP 服务器，绑定端口
        HttpServer server = HttpServer.create(
            new InetSocketAddress(PORT), 
            0  // backlog，0表示使用默认值
        );
        
        // 注册路由处理器
        // 每个 context 对应一个 URL 路径
        server.createContext("/", new HomeHandler());
        server.createContext("/hello", new HelloHandler());
        server.createContext("/api/greet", new GreetApiHandler());
        
        // 启动服务器
        server.start();
        
        System.out.println("服务器已启动: http://localhost:" + PORT);
        System.out.println("\n可用路径:");
        System.out.println("  /        - 首页");
        System.out.println("  /hello   - Hello页面");
        System.out.println("  /api/greet?name=xxx - API示例");
    }
    
    /**
     * 首页处理器
     */
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 获取请求信息
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            
            System.out.println(method + " " + path);
            
            // 构造响应
            String html = """
                <!DOCTYPE html>
                <html>
                <head><title>HttpServer 示例</title></head>
                <body>
                    <h1>欢迎使用 Java HttpServer!</h1>
                    <ul>
                        <li><a href="/hello">Hello 页面</a></li>
                        <li><a href="/api/greet?name=学生">API 示例</a></li>
                    </ul>
                </body>
                </html>
                """;
            
            // 发送响应
            sendResponse(exchange, 200, "text/html", html);
        }
    }
    
    /**
     * Hello 页面处理器
     */
    static class HelloHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = "<h1>Hello, World! 你好，世界！</h1>";
            sendResponse(exchange, 200, "text/html", html);
        }
    }
    
    /**
     * API 处理器 - 演示如何处理查询参数
     */
    static class GreetApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 获取查询字符串 ?name=xxx
            String query = exchange.getRequestURI().getQuery();
            String name = "访客";
            
            // 解析参数
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2 && "name".equals(kv[0])) {
                        name = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    }
                }
            }
            
            // 返回 JSON
            String json = String.format(
                "{\"greeting\": \"你好, %s!\", \"time\": \"%s\"}",
                name,
                java.time.LocalDateTime.now()
            );
            
            sendResponse(exchange, 200, "application/json", json);
        }
    }
    
    /**
     * 辅助方法：发送 HTTP 响应
     */
    private static void sendResponse(HttpExchange exchange, int status, 
                                     String contentType, String body) throws IOException {
        // 设置响应头
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        
        // 转为字节（注意编码）
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        
        // 发送响应头和状态码
        exchange.sendResponseHeaders(status, bytes.length);
        
        // 发送响应体
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

/*
 * 💡 学习要点：
 * 
 * 1. HttpServer.create(address, backlog)
 *    - address: 监听地址和端口
 *    - backlog: 等待连接队列长度
 * 
 * 2. createContext(path, handler)
 *    - 路由注册，不同路径用不同处理器
 *    - 类似于 Web 框架的路由功能
 * 
 * 3. HttpHandler 接口
 *    - 只有一个方法：handle(HttpExchange exchange)
 *    - 所有请求处理逻辑都在这里
 * 
 * 4. HttpExchange 对象
 *    - getRequestMethod(): 获取 GET/POST 等
 *    - getRequestURI(): 获取请求路径和参数
 *    - getRequestBody(): 获取请求体（POST数据）
 *    - getResponseHeaders(): 设置响应头
 *    - sendResponseHeaders(): 发送状态码
 *    - getResponseBody(): 获取输出流
 * 
 * 5. 与 RawHttpServer 的对比
 *    - 不用手动解析 HTTP 格式
 *    - 自动处理多线程
 *    - 代码更简洁
 * 
 * 🔧 练习：
 * 1. 添加一个 /time 路径返回当前时间
 * 2. 添加一个 /echo 路径，返回用户发送的内容
 */
