# BriarLua 构建文档

## 技术栈与版本（已验证组合，不要随意更换）

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **17** | AGP 8.13 要求 JDK 17+ |
| Android Gradle Plugin | **8.13.0** | AGP 8.6+ 才支持 compileSdk 35 |
| Gradle | **8.13**（发行版） | wrapper 已配置腾讯镜像 |
| Kotlin | **1.9.24** | 1.8.21 不兼容 AGP 8.13 |
| compileSdk / targetSdk | **35 / 35** | Android 15；36+ 有 AGP 8.13 平台命名冲突，暂不可用 |
| minSdk | 23 | Material 1.14.0 的最低要求 |
| NDK（重编 native 用） | **r27c** | r27 默认仍是 4KB 页对齐，三个 Application.mk 已显式 `-Wl,-z,max-page-size=16384` |

## 环境准备

JDK 17 与 Android SDK 组件：

```
platforms;android-35
build-tools;35.0.0
platform-tools
ndk;27.2.12479018        # 仅重编 native 时需要
```

Maven 依赖：`settings.gradle` 已配置阿里云镜像（google / public / gradle-plugin），国内网络可直接构建；海外网络走其后的 google() / mavenCentral()。

## 构建

```bash
./gradlew :app:assembleDebug     # 调试包
./gradlew :app:assembleRelease   # 正式包
# 产物: app/build/outputs/apk/release/app-release.apk
```

### 签名

本仓库**不包含**发布签名密钥。无密钥时，release 构建自动回退 debug 签名（可直接安装体验）。

正式发布请自备 keystore 放到仓库根目录并设置环境变量：

```bash
export BRIARLUA_KEYSTORE_PASSWORD=你的keystore密码
export BRIARLUA_KEY_ALIAS=release       # 可选，默认 release
./gradlew :app:assembleRelease
```

## 重编原生 so（可选）

日常构建**不需要**重编 native——`app/src/main/jniLibs/` 已带全部预编译 so。

如需修改 native 代码（13 个自研模块）：

```bash
cd native
$NDK/ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk NDK_APPLICATION_MK=Application.mk
# 产物在 native/libs/<abi>/，复制进 app/src/main/jniLibs/<abi>/
```

注意：`jniLibs/` 里另有 8 个 LuaAppX 时代遗留 so（base64/gl/lfs/rawio/tcc/threads/yaml/zlib）**没有源码**，保留原样，不要试图重编。

## 重要注意事项（历史踩坑实录）

### 构建系统

1. AGP 8.1.x 遇 compileSdk 35 直接失败（解析不了新 SDK 的 package.xml），必须 8.6+。
2. res 下 XML 首字符必须是 `<`，资源合并器对前言内容零容忍。

### native

3. **luajava.c 的 heap tagging 关闭代码勿删**（`JNI_OnLoad` 里的 `mallopt` 调用）：Android 12+ tagged heap 会因指针经 JNI long 中转后 tag 截断而 abort。
4. **静态链 lua 的模块必须带 `-lz`**：本仓库 lua 源码带 zlib 钩子（lstrlib.c 的 `str_dump`、lauxlib.c 的 `loadxchunk`），缺 `-lz` 链接期报 undefined `deflateInit_` / `inflate`。
5. **cjson 必须动态依赖 libluajava.so**（`LOCAL_SHARED_LIBRARIES := luajava`）：静态链 lua 会让 cjson.so 内出现第二份 lua VM 实例，与宿主双实例操作同一 lua_State，造成 GC 堆损坏。单独构建入口见 `native/Android.mk.cjson`。
6. **16KB 页对齐**：NDK r27 默认 4KB 对齐，不带 `-Wl,-z,max-page-size=16384` 产出的 so 在 16KB 页设备上加载失败。重编后用 `readelf -l` 验证 LOAD 段 `Align=0x4000`。
7. `gl`（luagl）模块在 arm64 编不过（老 OpenGL 固定管线），已从顶层 Android.mk 排除。
8. 顶层 `Android.mk` 用 `TOP_PATH := $(call my-dir)` 快照后 include 子模块，不能直接用 `$(LOCAL_PATH)`（会被子模块赋值全局污染）。

### Java / Lua 层

9. `com/luajava/` 必须用本仓库版本：官方 AndroLua_pro Java + 18 个 Object/Class 参数重载，与重编 luajava.c 的 JNI 调用约定配套。勿回退为「Java 侧对象注册表」模型。
10. `LuaActivity.initENV` 仅对项目目录生效（`luaDir.startsWith(luaExtDir)` 判定），IDE 自身页面不受项目 init.lua 污染。
11. `loadlayout.lua` 的 setAttribute 只缓存「setter/assign 模式」，**不能缓存方法引用本身**（luajava 索引未知方法名返回惰性代理，缓存引用会 NPE）。
12. Lua 兼容 shim（`table.clear`、`unpack = table.unpack`、`package.preload["cjson"]` 兜底）注入在 `import.lua` / `env.lua` 末尾，必须在 `return env_import` 之前。

### 主题 / UI

13. targetSdk 35+ 强制 edge-to-edge：values-v35 / values-night-v35 主题已加 `android:windowOptOutEdgeToEdgeEnforcement`，删掉会导致页面内容顶进状态栏。
14. 页面 Lua 脚本里 `setTheme` 必须在 `setContentView` 之前。

## 打包源码包 / 克隆仓库注意事项

`app/src/main/assets/page/build/`（打包页 Build.lua）是 IDE「打包 APK」功能的入口页面，**任何时候都不能遗漏**。

本仓库 `.gitignore` 已改为根锚定（`/build/`、`app/build/` 等），不会误伤该目录；如用其他工具打源码包（遵循 gitignore 的工具或自定义排除规则），打包后务必：

```bash
unzip -l xxx.zip | grep page/build   # 必须能看到 Build.lua
```
