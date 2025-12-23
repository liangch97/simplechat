import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.Map;

/**
 * 【学习示例】静态文件服务器
 * 
 * 可以读取并返回本地文件，就像 Nginx/Apache 一样。
 * 
 * 运行方式：
 *   1. 在本目录创建 www 文件夹
 *   2. 在 www 中放入 index.html 等文件
 *   3. 运行本程序
 *   4. 访问 http://localhost:8080
 * 
 * 知识点：
 *   1. 文件读取 (Files.readAllBytes)
 *   2. MIME 类型映射
 *   3. 404 错误处理
 */
public class StaticFileServer {
    
    private static final int PORT = 8080;
    private static final String WEB_ROOT = "www";  // 静态文件目录
    
    // MIME 类型映射
    private static final Map<String, String> MIME_TYPES = Map.of(
        "html", "text/html",
        "css", "text/css",
        "js", "application/javascript",
        "json", "application/json",
        "png", "image/png",
        "jpg", "image/jpeg",
        "gif", "image/gif",
        "svg", "image/svg+xml",
        "txt", "text/plain"
    );
    
    public static void main(String[] args) throws IOException {
        System.out.println("╔═══════════════════════════════════╗");
        System.out.println("║      静态文件服务器                 ║");
        System.out.println("╚═══════════════════════════════════╝");
        
        // 确保 www 目录存在
        Path webRoot = Paths.get(WEB_ROOT);
        if (!Files.exists(webRoot)) {
            Files.createDirectory(webRoot);
            // 创建一个示例 index.html
            createSampleFiles(webRoot);
            System.out.println("已创建 www 目录和示例文件");
        }
        
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticFileHandler());
        server.start();
        
        System.out.println("\n服务器已启动: http://localhost:" + PORT);
        System.out.println("静态文件目录: " + webRoot.toAbsolutePath());
    }
    
    /**
     * 静态文件处理器
     */
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // 默认页面
            if ("/".equals(path)) {
                path = "/index.html";
            }
            
            // 安全检查：防止路径遍历攻击
            if (path.contains("..")) {
                sendError(exchange, 403, "禁止访问");
                return;
            }
            
            // 构建文件路径
            Path filePath = Paths.get(WEB_ROOT + path);
            
            System.out.println("请求: " + path);
            
            // 检查文件是否存在
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendError(exchange, 404, "文件不存在: " + path);
                return;
            }
            
            // 读取文件内容
            byte[] content = Files.readAllBytes(filePath);
            
            // 获取 MIME 类型
            String mimeType = getMimeType(path);
            
            // 发送响应
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.sendResponseHeaders(200, content.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
            
            System.out.println("  -> 200 OK (" + content.length + " bytes)");
        }
        
        private String getMimeType(String path) {
            int dot = path.lastIndexOf('.');
            if (dot > 0) {
                String ext = path.substring(dot + 1).toLowerCase();
                return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
            }
            return "application/octet-stream";
        }
        
        private void sendError(HttpExchange exchange, int code, String message) 
                throws IOException {
            String html = "<html><body><h1>" + code + " Error</h1><p>" + message + "</p></body></html>";
            byte[] bytes = html.getBytes("UTF-8");
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(code, bytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            
            System.out.println("  -> " + code + " " + message);
        }
    }
    
    /**
     * 创建示例文件
     */
    private static void createSampleFiles(Path webRoot) throws IOException {
        // index.html
        String indexHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>静态文件服务器</title>
                <link rel="stylesheet" href="style.css">
            </head>
            <body>
                <h1>🎉 静态文件服务器正常工作！</h1>
                <p>这个页面是从 www/index.html 文件读取的。</p>
                <p>当前时间由 JavaScript 显示: <span id="time"></span></p>
                <script src="script.js"></script>
            </body>
            </html>
            """;
        Files.writeString(webRoot.resolve("index.html"), indexHtml);
        
        // style.css
        String css = """
            body {
                font-family: Arial, sans-serif;
                max-width: 600px;
                margin: 50px auto;
                padding: 20px;
                background: #f5f5f5;
            }
            h1 { color: #006633; }
            """;
        Files.writeString(webRoot.resolve("style.css"), css);
        
        // script.js
        String js = """
            document.getElementById('time').textContent = new Date().toLocaleString();
            console.log('JavaScript 文件加载成功！');
            """;
        Files.writeString(webRoot.resolve("script.js"), js);
    }
}

/*
 * 💡 学习要点：
 * 
 * 1. 静态文件服务的核心逻辑
 *    - 根据 URL 路径找到对应文件
 *    - 读取文件内容
 *    - 设置正确的 Content-Type
 *    - 返回文件内容
 * 
 * 2. MIME 类型
 *    - 告诉浏览器如何处理响应内容
 *    - .html → text/html (渲染网页)
 *    - .css → text/css (应用样式)
 *    - .js → application/javascript (执行脚本)
 *    - .png → image/png (显示图片)
 * 
 * 3. 安全性
 *    - 防止路径遍历攻击 (../)
 *    - 用户可能尝试访问 /../../../etc/passwd
 * 
 * 4. 与项目代码的联系
 *    - WebChatServer.java 中的 StaticFileHandler
 *    - 原理完全相同！
 * 
 * 🔧 练习：
 * 1. 添加更多 MIME 类型支持
 * 2. 添加目录浏览功能
 * 3. 添加缓存控制头 (Cache-Control)
 */
