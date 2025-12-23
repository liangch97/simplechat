# 练习 2: HTTP 服务器

## 📝 练习题目

### 练习 2.1: 理解 HTTP (理论)

回答以下问题：

1. HTTP 请求由哪几部分组成？
2. 状态码 200、404、500 分别表示什么？
3. Content-Type 头有什么作用？
4. GET 和 POST 请求的主要区别是什么？

---

### 练习 2.2: 运行示例

运行三个示例，观察输出：

```bash
cd examples

# 示例1: 手写HTTP服务器
javac RawHttpServer.java
java RawHttpServer
# 浏览器访问 http://localhost:8080

# 示例2: 使用HttpServer API  
javac SimpleHttpServer.java
java SimpleHttpServer

# 示例3: 静态文件服务器
javac StaticFileServer.java
java StaticFileServer
```

使用浏览器开发者工具（F12 → Network）观察：
- 请求头中有哪些信息？
- 响应头中有哪些信息？

---

### 练习 2.3: 添加新路由 ⭐

修改 `SimpleHttpServer.java`，添加以下功能：

1. `/time` - 返回当前日期时间
2. `/random` - 返回一个随机数 (1-100)
3. `/add?a=10&b=20` - 返回两个数的和

---

### 练习 2.4: POST 请求处理 ⭐⭐

修改 `SimpleHttpServer.java`，添加 `/api/echo` 路径：

- 接受 POST 请求
- 读取请求体内容
- 原样返回

测试方法（使用 curl 或 PowerShell）：
```powershell
# PowerShell
Invoke-RestMethod -Uri "http://localhost:8080/api/echo" -Method Post -Body "Hello Server!"

# curl
curl -X POST -d "Hello Server!" http://localhost:8080/api/echo
```

提示代码：
```java
// 读取 POST 请求体
InputStream is = exchange.getRequestBody();
String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
```

---

### 练习 2.5: 迷你文件上传 ⭐⭐⭐

创建一个简单的文件上传服务器：

1. GET `/upload` - 返回一个上传表单
2. POST `/upload` - 接收上传的内容并保存

HTML 表单示例：
```html
<form method="POST" action="/upload">
    <textarea name="content" rows="10" cols="50"></textarea>
    <button type="submit">保存</button>
</form>
```

---

## ✅ 检验标准

完成后，你应该能够：
- [ ] 解释 HTTP 请求/响应的结构
- [ ] 使用 HttpServer 创建多路由服务器
- [ ] 处理 GET 请求的查询参数
- [ ] 处理 POST 请求的请求体

---

## 🎯 下一步

完成后进入 [模块3: SSE 实时通讯](../03-sse-realtime/README.md)！
