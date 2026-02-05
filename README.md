# SimpleChat / SYSU Chat

一个轻量的 Java Web 聊天服务器 + 静态网页前端示例工程，支持可选的数据库持久化与 Cloudflare Tunnel 公网转发。

## 功能概览

- Java WebSocket/HTTP 聊天服务（入口：`src/WebChatServer.java`）
- 静态前端页面（目录：`web/` 与 `android-app/www/`）
- 可选数据库持久化（MySQL / SQL Server）
- 简单启动脚本（`start.bat`、`start.sh`）

## 环境要求

- JDK 11 或更高版本
- （可选）MySQL 或 SQL Server
- （可选）cloudflared（用于公网隧道）

## 快速启动

### Windows

双击 `start.bat`，或运行后指定端口：

- 默认端口：8080
- 自定义端口：`start.bat 9000`

### macOS / Linux

- 运行：`./start.sh`
- 自定义端口：`./start.sh 9000`

启动后访问：

- 本地：`http://localhost:8080`

## 数据库配置（可选）

项目根目录已有示例 `.env`，未配置时服务器仍可运行（仅内存态，不落库）。

支持的环境变量：

- `DB_URL`：JDBC 连接串
- `DB_USER`：数据库用户名
- `DB_PASSWORD`：数据库密码
- `DB_POOL_SIZE`：连接池大小（默认 10）

示例（MySQL）：

- `jdbc:mysql://localhost:3306/simplechat?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true`

示例（SQL Server）：

- `jdbc:sqlserver://localhost:1433;databaseName=simplechat;encrypt=true;trustServerCertificate=true`

数据库建表脚本位于 `scripts/`（如 `schema.sql`、`schema-sqlserver.sql`）。

## 目录说明

- `src/`：Java 服务器源码
- `web/`：静态前端
- `android-app/`：Capacitor 工程（移动端壳）
- `scripts/`：数据库脚本与启动脚本
- `storage/`：文件与聊天记录存储示例

## 常见问题

- **编译失败？** 确认已安装 JDK 11+，并已配置 `JAVA_HOME`。
- **数据库连不上？** 检查 `.env` 与 JDBC 驱动（`lib/` 目录）。
- **端口被占用？** 启动脚本传入新端口即可。

## 许可证

未附带许可证文件时，默认保留所有权利。如需开源授权，请补充 LICENSE。
