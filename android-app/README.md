# SYSU Chat Android App 打包指南

## 📱 简介

本指南将帮助你将 SYSU Chat Web 应用打包成 Android APK 文件。

## 🛠️ 环境要求

### 必需软件

1. **Node.js** (v16+)
   - 下载: https://nodejs.org/
   - 验证: `node --version`

2. **Java JDK** (v17+)
   - 下载: https://adoptium.net/
   - 验证: `java --version`

3. **Android Studio** (推荐)
   - 下载: https://developer.android.com/studio
   - 安装时选择 Android SDK

4. **Android SDK**
   - 通过 Android Studio 安装
   - 或单独下载命令行工具

### 环境变量配置

#### Windows
```powershell
# 设置 ANDROID_HOME
setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"

# 添加到 PATH
setx PATH "%PATH%;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\tools"
```

#### macOS/Linux
```bash
# 添加到 ~/.bashrc 或 ~/.zshrc
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools
```

## 🚀 快速开始

### 方式一：一键构建（推荐）

**Windows:**
```cmd
cd android-app
build-apk.bat
```

**macOS/Linux:**
```bash
cd android-app
chmod +x build-apk.sh
./build-apk.sh
```

构建成功后，APK 文件将位于 `android-app/sysu-chat.apk`

### 方式二：手动构建

#### 1. 安装依赖
```bash
cd android-app
npm install
```

#### 2. 准备 Web 文件
```bash
# 创建 www 目录并复制 web 文件
mkdir www
cp -r ../web/* www/
```

#### 3. 添加 Android 平台
```bash
npx cap add android
```

#### 4. 同步资源
```bash
npx cap sync android
```

#### 5. 构建 APK

**Debug 版本:**
```bash
cd android
./gradlew assembleDebug
# APK 位于: android/app/build/outputs/apk/debug/app-debug.apk
```

**Release 版本:**
```bash
cd android
./gradlew assembleRelease
# APK 位于: android/app/build/outputs/apk/release/app-release-unsigned.apk
```

### 方式三：使用 Android Studio

1. 运行 `npx cap open android`
2. Android Studio 会自动打开项目
3. 点击 **Build > Build Bundle(s) / APK(s) > Build APK(s)**
4. APK 文件将在 `android/app/build/outputs/apk/` 目录下

## 📝 配置说明

### 服务器地址配置

如果你的服务器地址不是 `sysu.asia`，需要修改 `www/js/config.js`:

```javascript
// 生产环境
production: {
    baseUrl: 'https://your-server.com',  // 修改为你的服务器地址
    // ...
}
```

### 应用配置

修改 `capacitor.config.json`:

```json
{
  "appId": "com.your.appid",      // 应用包名
  "appName": "Your App Name",     // 应用名称
  // ...
}
```

### 连接远程服务器

如果想让 App 直接连接你的在线服务器（而不是打包本地文件），修改 `capacitor.config.json`:

```json
{
  "server": {
    "url": "https://sysu.asia",   // 你的服务器地址
    "cleartext": true              // 如果是 http 需要设置为 true
  }
}
```

## 🎨 自定义图标和启动画面

### 应用图标

将图标文件放置到:
```
android/app/src/main/res/
├── mipmap-hdpi/ic_launcher.png       (72x72)
├── mipmap-mdpi/ic_launcher.png       (48x48)
├── mipmap-xhdpi/ic_launcher.png      (96x96)
├── mipmap-xxhdpi/ic_launcher.png     (144x144)
├── mipmap-xxxhdpi/ic_launcher.png    (192x192)
```

### 启动画面

将启动画面放置到:
```
android/app/src/main/res/
├── drawable/splash.png
├── drawable-land-hdpi/splash.png
├── drawable-land-mdpi/splash.png
├── drawable-land-xhdpi/splash.png
├── drawable-land-xxhdpi/splash.png
├── drawable-land-xxxhdpi/splash.png
├── drawable-port-hdpi/splash.png
├── drawable-port-mdpi/splash.png
├── drawable-port-xhdpi/splash.png
├── drawable-port-xxhdpi/splash.png
├── drawable-port-xxxhdpi/splash.png
```

## 🔐 签名发布版 APK

### 1. 生成签名密钥

```bash
keytool -genkey -v -keystore sysu-chat.keystore -alias sysu-chat -keyalg RSA -keysize 2048 -validity 10000
```

### 2. 配置签名

在 `android/app/build.gradle` 中添加:

```gradle
android {
    signingConfigs {
        release {
            storeFile file('sysu-chat.keystore')
            storePassword 'your_store_password'
            keyAlias 'sysu-chat'
            keyPassword 'your_key_password'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
}
```

### 3. 构建签名 APK

```bash
cd android
./gradlew assembleRelease
```

## ❓ 常见问题

### Q: 构建失败提示找不到 SDK
**A:** 确保 `ANDROID_HOME` 环境变量已正确设置，并且已安装 Android SDK。

### Q: Gradle 构建很慢
**A:** 首次构建需要下载依赖，请耐心等待。可以配置国内镜像加速:

在 `android/build.gradle` 中添加:
```gradle
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/google' }
        google()
        mavenCentral()
    }
}
```

### Q: APK 无法安装
**A:** 
1. 确保手机开启了"允许安装未知来源应用"
2. 如果是 Android 8.0+，需要对安装来源单独授权
3. 确保 APK 文件完整未损坏

### Q: App 打开后白屏
**A:** 
1. 检查网络连接
2. 检查服务器地址是否正确
3. 检查服务器是否正常运行
4. 使用 Chrome 远程调试查看具体错误

### Q: 如何调试 App
**A:** 
1. 连接 Android 设备并开启 USB 调试
2. 打开 Chrome 浏览器访问 `chrome://inspect`
3. 选择你的设备和 WebView 进行调试

## 📦 项目结构

```
android-app/
├── package.json              # npm 依赖配置
├── capacitor.config.json     # Capacitor 配置
├── capacitor.config.ts       # Capacitor TypeScript 配置
├── build-apk.bat            # Windows 构建脚本
├── build-apk.sh             # Linux/macOS 构建脚本
├── www/                      # Web 资源目录（构建时复制）
└── android/                  # Android 原生项目（自动生成）
    └── app/
        └── build/
            └── outputs/
                └── apk/
                    └── debug/
                        └── app-debug.apk
```

## 🔗 相关链接

- [Capacitor 官方文档](https://capacitorjs.com/docs)
- [Android Studio 下载](https://developer.android.com/studio)
- [Node.js 下载](https://nodejs.org/)
- [Adoptium JDK 下载](https://adoptium.net/)

## 📄 许可证

MIT License
