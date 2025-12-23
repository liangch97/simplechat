#!/bin/bash

# SYSU Chat 服务器启动脚本
# 域名: sysu.asia

echo "╔════════════════════════════════════════════╗"
echo "║         SYSU Chat 服务器启动脚本           ║"
echo "╚════════════════════════════════════════════╝"
echo ""

# 进入项目根目录
cd "$(dirname "$0")/.."

# 设置默认端口
PORT=${1:-7070}

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "[错误] 未找到 Java，请先安装 JDK 11 或更高版本"
    exit 1
fi

# 创建输出目录
mkdir -p out

echo "[1/3] 正在编译服务器..."
javac -encoding UTF-8 -d out src/WebChatServer.java

if [ $? -ne 0 ]; then
    echo "[错误] 编译失败"
    exit 1
fi

echo "[2/3] 编译成功！"
echo "[3/3] 正在启动服务器 (端口: $PORT)..."
echo ""

java -cp out WebChatServer $PORT
