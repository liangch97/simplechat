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
PORT=${1:-8080}

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "[错误] 未找到 Java，请先安装 JDK 11 或更高版本"
    exit 1
fi

# 清理并创建输出目录
rm -rf out
mkdir -p out

echo "[1/4] 正在编译服务器..."
mkdir -p lib >/dev/null 2>&1 || true
javac -encoding UTF-8 -d out -cp "lib/*" src/util/Env.java src/db/Db.java src/db/MessageDao.java src/WebChatServer.java

if [ $? -ne 0 ]; then
        echo "[错误] 编译失败"
        exit 1
fi

echo "[2/4] 编译成功！"

# 可选：启动 cloudflared（保持与 Windows 脚本一致的步骤计数）
if command -v cloudflared >/dev/null 2>&1; then
    echo "[3/4] 正在启动 Cloudflare 隧道..."
    cloudflared tunnel run &
    sleep 2
else
    echo "[3/4] 未找到 cloudflared，跳过隧道启动"
fi

echo "[4/4] 正在启动服务器 (端口: $PORT)..."
echo ""
java -cp "out:lib/*" app.WebChatServer $PORT
