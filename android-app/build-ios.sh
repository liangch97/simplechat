#!/bin/bash
echo "================================================"
echo "  SYSU Chat iOS IPA 构建脚本"
echo "  注意：必须在 macOS 上运行！"
echo "================================================"
echo ""

# 检查是否在 macOS 上运行
if [[ "$(uname)" != "Darwin" ]]; then
    echo "[错误] 此脚本必须在 macOS 上运行！"
    echo "iOS App 只能在 Mac 上使用 Xcode 构建。"
    exit 1
fi

# 检查 Xcode
if ! command -v xcodebuild &> /dev/null; then
    echo "[错误] 未找到 Xcode，请先安装 Xcode"
    echo "下载地址: https://apps.apple.com/app/xcode/id497799835"
    exit 1
fi

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo "[错误] 未找到 Node.js，请先安装 Node.js"
    exit 1
fi

echo ""
echo "[步骤 1/5] 安装依赖..."
npm install

echo ""
echo "[步骤 2/5] 复制 Web 文件到 www 目录..."
rm -rf www
mkdir -p www
cp -r ../web/* www/

echo ""
echo "[步骤 3/5] 初始化 Capacitor iOS 项目..."
if [ ! -d "ios" ]; then
    npx cap add ios
else
    echo "iOS 项目已存在，跳过初始化"
fi

echo ""
echo "[步骤 4/5] 同步 Web 资源到 iOS 项目..."
npx cap sync ios

echo ""
echo "[步骤 5/5] 打开 Xcode..."
npx cap open ios

echo ""
echo "================================================"
echo "Xcode 已打开，请在 Xcode 中："
echo "1. 选择你的开发团队 (Signing & Capabilities)"
echo "2. 选择目标设备或模拟器"
echo "3. 点击 ▶ 运行 或 Product > Archive 打包"
echo "================================================"
