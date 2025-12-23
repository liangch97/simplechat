import java.io.*;
import java.net.*;

/**
 * 【学习示例】手写 HTTP 服务器
 * 
 * 这个示例用最原始的 Socket 实现 HTTP 服务器，
 * 帮助你理解 HTTP 协议的本质。
 * 
 * 运行后访问: http://localhost:8080
 * 
 * 知识点：
 *   1. HTTP 请求的文本格式
 *   2. HTTP 响应的构造方法
 *   3. 浏览器和服务器的通讯过程
 */
public class RawHttpServer {
    
    private static final int PORT = 8080;
    
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════╗");
        System.out.println("║    手写 HTTP 服务器 (学习版)       ║");
        System.out.println("╚═══════════════════════════════════╝");
        System.out.println("访问: http://localhost:" + PORT);
        System.out.println();
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            
            while (true) {
                // 等待浏览器连接
                Socket client = serverSocket.accept();
                System.out.println("--- 新请求 ---");
                
                handleRequest(client);
            }
            
        } catch (IOException e) {
            System.err.println("服务器错误: " + e.getMessage());
        }
    }
    
    private static void handleRequest(Socket client) {
        try (
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream())
            );
            OutputStream out = client.getOutputStream();
        ) {
            // ============ 第一步：读取 HTTP 请求 ============
            
            // 读取请求行（第一行）
            String requestLine = reader.readLine();
            System.out.println("请求行: " + requestLine);
            
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            
            // 解析请求行: "GET /path HTTP/1.1"
            String[] parts = requestLine.split(" ");
            String method = parts[0];      // GET
            String path = parts[1];        // /path
            
            // 读取请求头（直到遇到空行）
            String headerLine;
            System.out.println("请求头:");
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                System.out.println("  " + headerLine);
            }
            System.out.println();
            
            // ============ 第二步：构造 HTTP 响应 ============
            
            String body;
            String contentType = "text/html; charset=utf-8";
            int statusCode = 200;
            String statusText = "OK";
            
            // 根据路径返回不同内容
            if ("/".equals(path) || "/index.html".equals(path)) {
                body = buildHomePage();
            } else if ("/hello".equals(path)) {
                body = "<h1>Hello, World!</h1><p>你好，世界！</p>";
            } else if ("/time".equals(path)) {
                body = "<h1>当前时间</h1><p>" + java.time.LocalDateTime.now() + "</p>";
            } else if ("/api/data".equals(path)) {
                contentType = "application/json";
                body = "{\"message\": \"这是JSON数据\", \"code\": 200}";
            } else {
                statusCode = 404;
                statusText = "Not Found";
                body = "<h1>404 - 页面不存在</h1><p>请求的路径: " + path + "</p>";
            }
            
            // ============ 第三步：发送 HTTP 响应 ============
            
            // 构造响应
            StringBuilder response = new StringBuilder();
            
            // 状态行
            response.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusText).append("\r\n");
            
            // 响应头
            response.append("Content-Type: ").append(contentType).append("\r\n");
            response.append("Content-Length: ").append(body.getBytes("UTF-8").length).append("\r\n");
            response.append("Connection: close\r\n");
            
            // 空行（分隔头和体）
            response.append("\r\n");
            
            // 响应体
            response.append(body);
            
            // 发送响应
            out.write(response.toString().getBytes("UTF-8"));
            out.flush();
            
            System.out.println("响应: " + statusCode + " " + statusText);
            System.out.println("路径: " + path);
            System.out.println();
            
        } catch (IOException e) {
            System.err.println("请求处理错误: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }
    
    private static String buildHomePage() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>手写HTTP服务器</title>
                <style>
                    body { font-family: Arial; max-width: 600px; margin: 50px auto; padding: 20px; }
                    h1 { color: #006633; }
                    a { color: #006633; margin-right: 15px; }
                </style>
            </head>
            <body>
                <h1>🎉 恭喜！你的HTTP服务器工作了！</h1>
                <p>这个页面是由纯 Java Socket 代码生成的。</p>
                <h3>试试这些链接：</h3>
                <p>
                    <a href="/hello">/hello</a>
                    <a href="/time">/time</a>
                    <a href="/api/data">/api/data</a>
                    <a href="/notfound">/notfound (404)</a>
                </p>
                <h3>学习要点：</h3>
                <ul>
                    <li>HTTP 是纯文本协议</li>
                    <li>请求和响应都有固定格式</li>
                    <li>状态码表示处理结果</li>
                </ul>
            </body>
            </html>
            """;
    }
}

/*
 * 💡 学习要点：
 * 
 * 1. HTTP 是文本协议
 *    - 请求和响应都是纯文本
 *    - 用 \r\n 换行（不是 \n）
 *    - 空行分隔头部和正文
 * 
 * 2. 响应格式固定
 *    - 第一行：状态行
 *    - 接下来：响应头（键: 值）
 *    - 空行
 *    - 响应体
 * 
 * 3. Content-Type 很重要
 *    - text/html → 浏览器渲染HTML
 *    - application/json → 浏览器显示JSON
 *    - text/plain → 纯文本
 * 
 * 4. Content-Length
 *    - 告诉浏览器响应体有多少字节
 *    - 注意中文字符的字节数（UTF-8中文3字节）
 * 
 * ⚠️ 这个实现的缺陷：
 * - 单线程，一次只能处理一个请求
 * - 没有处理 POST 请求体
 * - 没有静态文件服务
 * 
 * 下一个示例会使用 Java 内置的 HttpServer 解决这些问题！
 */
