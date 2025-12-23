@echo off
chcp 65001 >nul
title SYSU Chat Server

echo ╔════════════════════════════════════════════╗
echo ║         SYSU Chat 服务器启动脚本           ║
echo ╚════════════════════════════════════════════╝
echo.

REM 进入项目根目录
cd /d "%~dp0.."

REM 设置默认端口
set PORT=7070
if not "%1"=="" set PORT=%1

REM 检查 Java 是否安装
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Java，请先安装 JDK 11 或更高版本
    pause
    exit /b 1
)

REM 创建输出目录
if not exist "out" mkdir out

echo [1/3] 正在编译服务器...
javac -encoding UTF-8 -d out src\WebChatServer.java
if errorlevel 1 (
    echo [错误] 编译失败
    pause
    exit /b 1
)

echo [2/3] 编译成功！
echo [3/3] 正在启动服务器...
echo.

java -cp out WebChatServer %PORT%

pause
