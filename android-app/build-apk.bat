@echo off
chcp 65001 >nul
echo ================================================
echo   SYSU Chat Android APK 构建脚本
echo ================================================
echo.

:: 检查 Node.js
where node >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [错误] 未找到 Node.js，请先安装 Node.js
    echo 下载地址: https://nodejs.org/
    pause
    exit /b 1
)

:: 检查 Java
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [错误] 未找到 Java，请先安装 JDK 17+
    echo 下载地址: https://adoptium.net/
    pause
    exit /b 1
)

:: 设置 ANDROID_HOME（如果未设置）
if "%ANDROID_HOME%"=="" (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
        echo [信息] 设置 ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
    ) else (
        echo [警告] 未设置 ANDROID_HOME 环境变量
        echo 请先安装 Android Studio 和 Android SDK
    )
)

echo.
echo [步骤 1/6] 安装依赖...
call npm install

echo.
echo [步骤 2/6] 复制 Web 文件到 www 目录...
if exist www rmdir /s /q www
mkdir www
xcopy /s /e /y ..\web\* www\

echo.
echo [步骤 3/6] 修改配置为 App 模式...
:: 创建 App 专用配置
echo const CONFIG_APP = { isApp: true }; > www\js\config-app.js

echo.
echo [步骤 4/6] 初始化 Capacitor Android 项目...
if not exist android (
    call npx cap add android
) else (
    echo Android 项目已存在，跳过初始化
)

echo.
echo [步骤 5/6] 同步 Web 资源到 Android 项目...
call npx cap sync android

echo.
echo [步骤 6/6] 构建 APK...
cd android
call gradlew.bat assembleDebug

echo.
echo ================================================
if exist app\build\outputs\apk\debug\app-debug.apk (
    echo [成功] APK 构建完成！
    echo.
    echo APK 文件位置:
    echo %CD%\app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo 复制 APK 到上级目录...
    copy app\build\outputs\apk\debug\app-debug.apk ..\sysu-chat.apk
    echo.
    echo 最终 APK: %~dp0sysu-chat.apk
) else (
    echo [错误] APK 构建失败，请检查错误信息
)
echo ================================================

cd ..
pause
