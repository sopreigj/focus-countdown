# Focus Countdown 专注倒计时

一个简洁的 Android 专注倒计时应用，提供全屏倒计时显示和 3D 滚轮时间选择器。

<p align="center">
  <img src="screenshots/screenshot_1.png" width="280" />
  <img src="screenshots/screenshot_2.png" width="280" />
</p>

## ✨ 特性

- 📱 **真全屏显示** - 倒计时显示延伸到整个屏幕，包括刘海/妖精区域
- 🎯 **3D 滚轮选择器** - 使用 Canvas 绘制的流畅 3D 时间选择器
- ⚡ **后台持续计时** - 即使应用在后台也能准确倒计时
- 🌚 **状态保存** - 自动保存/恢复倒计时状态
- 🎨 **纯黑主题** - OLED 友好的纯黑界面设计

## 🚀 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **底层渲染**: SurfaceView + Canvas Native 绘制
- **状态管理**: StateFlow + SharedPreferences
- **动画**: Compose Animation API

## 📖 使用方法

1. 打开应用，使用滚轮选择小时和分钟
2. 点击"开始专注"启动倒计时
3. 进入全屏倒计时界面
4. 倒计时结束后自动返回设置界面

## 🎨 主要特点

### 全屏显示
使用 SurfaceView 绕过 Compose 的安全区域限制，实现真正的全屏显示：
```kotlin
window.attributes = window.attributes.apply {
    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}
window.setFlags(FLAG_LAYOUT_NO_LIMITS, FLAG_LAYOUT_NO_LIMITS)
```

### 3D 滚轮选择器
自定义 Canvas-based 滚轮组件，支持：
- 无限循环滚动
- 惯性滑动动画
- 3D 透视效果（缩放、透明度、旋转）
- 动态渐变遮罩

### 状态持久化
倒计时状态通过 SharedPreferences 保存，应用在后台结束时重新打开会自动返回设置界面。

## 🔧 构建

### 调试版
```bash
./gradlew :app:assembleDebug
```
APK 输出: `app/build/outputs/apk/debug/app-debug.apk`

### 发布版
发布版需要签名配置：

1. **生成签名密钥**（首次）：
```bash
keytool -genkey -v -keystore app/keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias focuscountdown
```

2. **配置签名**在 `app/build.gradle.kts` 中添加：
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("keystore.jks")
        storePassword = "你的密码"
        keyAlias = "focuscountdown"
        keyPassword = "你的密码"
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        // ...
    }
}
```

> ⚠️ **安全提示**：`keystore.jks` 已在 `.gitignore` 中，请勿提交到 Git！

3. **构建发布版**：
```bash
./gradlew :app:assembleRelease
```
APK 输出: `app/build/outputs/apk/release/app-release.apk`

## 📱 系统要求

- **最低 SDK**: 31 (Android 12)
- **目标 SDK**: 34 (Android 14)
- **Gradle**: 8.13

## 📁 项目结构

```
app/src/main/java/com/focus/countdown/
├── MainActivity.kt              # 全屏倒计时显示
├── timer/
│   └── TimerStateManager.kt       # 倒计时状态管理
└── ui/
    ├── components/
    │   └── WheelPicker.kt           # 3D 滚轮选择器
    └── screen/
        └── TimerSettingScreen.kt    # 设置界面
```

## 📝 开源协议

MIT License

## 👨‍💻 开发者

[@sopreigj](https://github.com/sopreigj)

---

<p align="center">
  用 ❤️ 和 Kotlin 打造
</p>
