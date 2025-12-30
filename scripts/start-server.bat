@echo off
chcp 65001 >nul
title SYSU Chat Server

echo ============================================
echo          SYSU Chat 服务器启动脚本
echo ============================================
echo.

REM 进入项目根目录
cd /d "%~dp0.."

REM 设置默认端口
set PORT=8080
if not "%1"=="" set PORT=%1

REM 检查 Java 是否安装
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Java，请先安装 JDK 11 或更高版本
    pause
    exit /b 1
)

REM 清理并创建输出目录
if exist "out" (
    rmdir /s /q out
)
mkdir out

echo [1/4] 正在编译服务器...
REM 包含 JDBC 驱动（如 lib\mysql-connector-j-*.jar）
if not exist lib mkdir lib
javac -encoding UTF-8 -sourcepath src -d out -cp "lib\*" src\util\Env.java src\db\Db.java src\db\MessageDao.java src\db\UserDao.java src\WebChatServer.java
if errorlevel 1 (
    echo [错误] 编译失败
    pause
    exit /b 1
)

echo [2/4] 编译成功！

REM 检查 cloudflared 是否安装
cloudflared --version >nul 2>&1
if errorlevel 1 (
    echo [警告] 未找到 cloudflared，跳过隧道启动
    echo [3/4] 跳过隧道...
) else (
    echo [3/4] 正在启动 Cloudflare 隧道...
    start "Cloudflare Tunnel" cmd /c "cloudflared tunnel run"
    timeout /t 3 >nul
    echo       隧道已在后台启动
)

echo [4/4] 正在启动服务器...
echo.
echo ============================================
echo   本地访问: http://localhost:%PORT%
echo   公网访问: https://chat.sysu.asia
echo ============================================
echo.

REM 运行时加入 lib/* 以加载 MySQL 驱动
java -cp "out;lib/*" app.WebChatServer %PORT%

pause
