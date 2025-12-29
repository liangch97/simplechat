#!/bin/bash
echo "================================================"
echo "  SYSU Chat Android APK 构建脚本"
echo "================================================"
echo ""

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo "[错误] 未找到 Node.js，请先安装 Node.js"
    echo "下载地址: https://nodejs.org/"
    exit 1
fi

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "[错误] 未找到 Java，请先安装 JDK 17+"
    echo "下载地址: https://adoptium.net/"
    exit 1
fi

# 设置 ANDROID_HOME（如果未设置）
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
        echo "[信息] 设置 ANDROID_HOME=$HOME/Android/Sdk"
    elif [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
        echo "[信息] 设置 ANDROID_HOME=$HOME/Library/Android/sdk"
    else
        echo "[警告] 未设置 ANDROID_HOME 环境变量"
        echo "请先安装 Android Studio 和 Android SDK"
    fi
fi

echo ""
echo "[步骤 1/6] 安装依赖..."
npm install

echo ""
echo "[步骤 2/6] 复制 Web 文件到 www 目录..."
rm -rf www
mkdir -p www
cp -r ../web/* www/

echo ""
echo "[步骤 3/6] 修改配置为 App 模式..."
echo 'const CONFIG_APP = { isApp: true };' > www/js/config-app.js

echo ""
echo "[步骤 4/6] 初始化 Capacitor Android 项目..."
if [ ! -d "android" ]; then
    npx cap add android
else
    echo "Android 项目已存在，跳过初始化"
fi

echo ""
echo "[步骤 5/6] 同步 Web 资源到 Android 项目..."
npx cap sync android

echo ""
echo "[步骤 6/6] 构建 APK..."
cd android
./gradlew assembleDebug

echo ""
echo "================================================"
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "[成功] APK 构建完成！"
    echo ""
    echo "APK 文件位置:"
    echo "$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "复制 APK 到上级目录..."
    cp app/build/outputs/apk/debug/app-debug.apk ../sysu-chat.apk
    echo ""
    echo "最终 APK: $(dirname $(pwd))/sysu-chat.apk"
else
    echo "[错误] APK 构建失败，请检查错误信息"
fi
echo "================================================"

cd ..
