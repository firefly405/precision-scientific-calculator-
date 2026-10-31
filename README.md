# 精确科学计算器

这是一个可安装到 Android 真机的 Kotlin 科学计算器。工程分为 `:core` 纯 JVM 精确计算模块和 `:app` Android 模块，最低支持 Android 7.0（API 24）。界面使用原生 `View` 绘制，减少首次同步依赖，适合在已安装 Android Studio 的机器上直接打开。

## 已实现

- BigInteger 有理数、根式、π 倍数和浮点回退
- 自研 Tokenizer + Pratt 风格 Parser + AST Evaluator，不使用脚本引擎或第三方表达式库
- 四则运算、括号、一元正负、隐式乘法、幂、百分号、阶乘、sqrt、ln/log
- DEG/RAD 常驻切换、2nd 反三角函数、特殊角精确查表与周期归约
- 中文错误提示：除零、定义域、负数开方、未定义正切、表达式错误、阶乘错误
- 原生按钮键盘、物理键盘 Enter/Backspace/Escape 支持、实时预览

## 构建与测试

```bash
./gradlew test
./gradlew assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。本机已验证 `:core:test` 和 `assembleDebug` 均通过；Android SDK 平台 35 和 Build Tools 34 已配置到 `local.properties`。

## 安装

将 debug APK 传到手机并点击安装。首次安装可能需要在系统设置中授予文件管理器“允许安装未知应用”。debug 包已由 Gradle 自动生成的 debug keystore 签名，体积略大且未做混淆，但功能完整。

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Release 签名

```bash
keytool -genkeypair -v -keystore calculator-release.jks -alias calculator \
  -keyalg RSA -keysize 2048 -validity 10000
```

将 keystore 放到安全位置，在 `app/build.gradle.kts` 的 `signingConfigs` 中配置 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，然后运行 `./gradlew assembleRelease`。当前工程默认关闭 release 混淆，未伪造任何签名密码。

## 精确值说明

`sin(30)`、`200*sin(30)`、`sin(π/6)`、`sqrt(8)` 等命中有理数/特殊角查表时保持精确，显示为 `1/2`、`100`、`2√2`。无法用当前代数类型表示的角度会回退到最多 10 位有效数字的十进制值。所有核心计算不使用 Double 承载有理数。

## 已知限制

当前版本优先完成精确计算主链路，尚未加入历史记录持久化、按键音开关和独立的浅色主题切换；系统主题和 Android 导航栏仍由原生主题控制。复杂非特殊角函数会按设计回退为十进制近似。
