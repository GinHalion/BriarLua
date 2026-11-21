# BriarLua 构建与部署文档

> 本文档写给在本机没有构建环境的设备/电脑上工作的 AI 助手:按此文档可从零部署编译环境并成功构建 BriarLua。
> 所有"已知坑"均为实际踩过并已验证修复的问题,**不要跳过任何一条**。

## 项目概述

BriarLua = 基于 AndroLua+ / LuaAppX Pro 二改的 Lua IDE(Android)。
当前状态:**自研 luajava.so 已完成**(基于 nirenr/AndroLua_pro 官方 C 源码重建,源码在本仓库 `native/` 目录),targetSdk/compileSdk = 35 (Android 15),minSdk 23(v1.7 升级,原 21;同步解锁 Material 1.14.0)。

## 技术栈与版本(已验证组合,不要随意更换)

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **17**(Temurin 17.0.16 验证过) | AGP 8.13 要求 JDK 17;JDK 21+ 未验证 |
| Android Gradle Plugin | **8.13.0** | AGP 8.6+ 才支持 compileSdk 35;8.1.x 连 SDK 35 平台包都解析不了(abis 元素),必须 8.13+ |
| Gradle | **8.13**(发行版) | wrapper 已配置腾讯镜像,直接 `./gradlew` 即可 |
| Kotlin | **1.9.24** | 1.8.21 不兼容 AGP 8.13 |
| compileSdk / targetSdk | **35 / 35** | Android 15;36+ 有 AGP 8.13 平台命名冲突问题,暂不可用 |
| minSdk | 23 | v1.7 由 21 升至 23;Material 1.14.0 的最低要求,打包器同步以 23 为下限钳制 |
| NDK(重编 native 用) | **r27c (27.2.12479018)** | 注意:**r27 默认仍是 4KB 页对齐**(r28 才默认 16KB),v1.11.1 起三个 Application.mk 均已显式 `-Wl,-z,max-page-size=16384`;现 jniLibs 的 libluajava.so/libcjson.so 即 v1.11.1 重编产物(16KB 对齐,含 luajava.c 泄漏/悬挂异常修复) |

## 一次性环境部署

### 1. JDK 17

```bash
# 推荐 sdkman:
curl -s https://get.sdkman.io | bash
sdk install java 17.0.16-tem
export JAVA_HOME=~/.sdkman/candidates/java/17.0.16-tem
```

### 2. Android SDK 组件(缺哪个装哪个)

```
platforms;android-35        # compileSdk 35 必需
build-tools;35.0.0
platform-tools
ndk;27.0.12077973           # 重编 libluajava 等 so 必需(16KB 页对齐)
```

### 3. 镜像(国内环境必需)

- Gradle 发行版:wrapper 已指向 `https://mirrors.cloud.tencent.com/gradle/gradle-8.13-bin.zip`
- Maven:`settings.gradle` 的 pluginManagement/dependencyResolutionManagement **已配置阿里云**(google/public/gradle-plugin),无需额外 init.gradle

### 4. 发布签名(可选)

本仓库**不包含**发布签名密钥。无密钥时，release 构建自动回退 debug 签名（可直接安装体验）。

正式发布请自备 keystore（命名 `BriarLua.keystore`，alias=release）放到仓库根目录，并设置环境变量：

```bash
export BRIARLUA_KEYSTORE_PASSWORD=你的keystore密码
export BRIARLUA_KEY_ALIAS=release       # 可选，默认 release
```

## 构建命令

```bash
# Java/Kotlin 构建(产出 debug APK)
./gradlew :app:assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
# 签名: 未配置发布密钥时自动回退 debug 签名(见「4. 发布签名(可选)」)
```

### 重编原生 so(libluajava 等 13 个模块)

```bash
# cd native（仓库根下；历史布局 app/src/main/jni/ 已不存在，构建入口全在 native/）
cd native
# 全量重编 13 模块（产物 native/libs/<abi>/）：
$NDK/ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk NDK_APPLICATION_MK=Application.mk
# 单独重编 lua+luajava（宿主 so，产物 native/libs/<abi>/）：
$NDK/ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk.luajava NDK_APPLICATION_MK=Application.mk.luajava
# 单独重编 cjson（动态依赖宿主 libluajava.so，链接输入=app/src/main/jniLibs 同款 so）：
$NDK/ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk.cjson NDK_APPLICATION_MK=Application.mk.cjson \
  NDK_OUT=/tmp/cjson-obj NDK_LIBS_OUT=/tmp/cjson-libs
# 产物复制进 app/src/main/jniLibs/<abi>/（全量/单编 luajava 的产物在 native/libs/<abi>/）
```

> **注意**:仓库内置的 `native/` 是精简版(13 个自研模块:luajava/lua/cjson/bson/canvas/crypt/luv/md5/mime/regex/sensor/socket/xml/zip)。
> `jniLibs/` 里另有 8 个 **LuaAppX 时代遗留 so(base64/gl/lfs/rawio/tcc/threads/yaml/zlib)没有源码**,保留原样即可,不要试图重编。

## 已知坑清单(全部实际踩过,逐条核对)

### 构建系统
1. **AGP 8.1.x 遇 compileSdk 35 直接失败**(找不到 platforms;android-35 或解析 package.xml 出错)——旧 AGP 解析不了新 SDK 的 package.xml(abis 元素)。必须 AGP 8.6+,推荐 8.13+。
2. **themes.xml 前导污染**:res/values/themes.xml 曾在 `<resources>` 前有个 `.`(基线 zip 自带),AGP 8.13 资源合并器严格报"前言中不允许有内容"。检查所有 res XML 首字符。
3. **新版 SDK 的 `PackageInfo.versionName` 标注 @Nullable**:Kotlin 1.9 严格检查,SplashWelcome 需 `?: ""`(已修)。
4. **本地 fileTree jar 冲突**:app/libs 里曾有 `android.jar`(含 android/* 类的库 jar)，如再加 jar,用 `implementation(files("libs/xxx.jar"))` 精确声明。

### 原生(native/)相关
5. **heap tagging 必须关闭**:`luajava.c` 里有 `JNI_OnLoad` 调 `mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE)`(经 dlsym 解析,兼容低版本)。**Android 12+ 的 tagged heap 会因指针经 JNI long 中转后 tag 截断而 abort(free 时 "Pointer tag truncated")。勿删此代码。** 诊断 native 内存问题时用 ASAN 版(见下),但 **ASAN 版绝不能用作打包项目 APK 的宿主**(wrap.sh 会给项目 APK 注入 ASAN,启动极慢形同卡死)。
6. **luaconf.h 缺失 io 宏段**:官方 lua 5.3.3 的 `l_fseek/l_ftell/l_popen/l_lockfile/l_seeknum` 段在此仓库 luaconf.h 中曾缺失(已在 `#endif` 后补回,Android 分支用 fseek/ftell——bionic 的 fseeko 需 API 24,而 minSdk 21)。
7. **regex 模块的 `luaL_typerror` 与静态 lua 库重复定义**:已改名 `regexL_typerror`(common.c/common.h/algo.h)。
8. **zip/mkstemp.c 缺 `#include <unistd.h>`**(getpid 隐式声明,新 clang 报错)。已补。
9. **各模块 Android.mk 的 `LOCAL_STATIC_LIBRARIES := luajava` 是无效依赖**(官方无静态 luajava 模块),已统一改为 `lua`。顶层 `Android.mk` 用 `TOP_PATH := $(call my-dir)` 快照后 include 子模块(不能直接用 `$(LOCAL_PATH)`,会被子模块的赋值全局污染),且 lua/luajava 模块的 mk 需存在。v1.5.1 已修复其残留的旧机器绝对路径。
9a. **lua 源码带 zlib 钩子**(lstrlib.c 的 `str_dump`、lauxlib.c 的 `loadxchunk` 调 deflate/inflate):**所有静态链 lua 的模块**必须带 `-lz`(v1.11.1 已给 socket/luv/xml/md5/canvas/crypt/bson/regex + socket 首模块统一补齐;luajava 两入口与 zip 原已带;sensor 动态链 luajava 不需要)。缺它会在链接期报 undefined `deflateInit_`/`inflate` 系列。
9b. **三个 Application.mk 均已带 `-Wl,-z,max-page-size=16384`**(v1.11.1)。NDK r27 默认 4KB 对齐,不带此标志产出的 so 在 16KB 页设备上加载失败 → 启动即崩(Pixel 8/9 16KB 模式、Play 16KB 合规均涉及)。重编后用 `readelf -l` 验证 LOAD 段 `Align=0x4000`。
10. **`gl`(luagl)模块在 arm64 编不过**(老 OpenGL 固定管线),已从顶层 Android.mk 排除;jniLibs 保留 LuaAppX 时代 libgl.so。

### Java 层
11. **`com/luajava/` 必须用本仓库版本**(官方 AndroLua_pro Java + BriarLua 适配):已补官方 18 个 Object/Class 参数重载(`callMethod`/`objectIndex`/`javaBindClass` 返回 Class 版/`javaCreate`/`javaOverride(long, Class)`/`objectCall`/`asTable`/`javaGc(Object)` 等)与重编 luajava.c 的 JNI 调用约定配套;并加 `compareTypes` 的 Object 形参兜底(`setSharedData(String, number)` 类调用)。**勿回退为 LuaAppX 的"Java 侧对象注册表"模型**,与官方 C 不兼容(会 NoSuchMethodError/参数错位)。
12. **`LuaState._toString` 返回类型是 `byte[]`**(非官方的 String);`_getObjectFromUserdata` 同理注意(已按官方 jobject 版对齐,getObjectFromUserdata 包装在 LuaState.java 内部)。
13. **`LuaState._LtoString` / `_doBuffer`**:官方 C 缺 `_LtoString`(已在 native/luajava/luajava.c 末尾补,返回 jbyteArray);`_doBuffer` 在 Java 中是注释掉的声明,无需实现。
14. **`LuaActivity.initENV` 仅对项目目录生效**:判定 `luaDir.startsWith(luaExtDir)`。IDE 自身页面(files/page/…、files/)下的 init.lua 是模板/无关文件,若误读会把 debugmode/theme 应用到 IDE 页面(历史崩溃源)。
15. **`Http` 全局类**来自 import.lua 的 globalMT 机制(`env_import(_G)` 给 _G 挂类查找器),`com.androlua.Http` 在 loaders 包列表里,项目 Lua 直接 `Http.get(url, nil, "UTF-8", nil, cb)` 即可。

### Lua 兼容 shim(已注入,勿删)
16. **`import.lua` 与 `env.lua` 末尾注入了**:
    - `table.clear`(官方 5.3.3 没有;LuaAppX 项目脚本大量使用)
    - `unpack = table.unpack`(json.lua 等老库用裸 unpack)
    - `package.preload["cjson"]` 纯 Lua 兜底(json.lua),**decode 失败会写 /sdcard/BriarLua/cjson_decode_error.log**
    - 注意 **shim 必须在 `return env_import` 之前**(Lua return 是块尾语句)
    - cjson.so 已自编译(AndroLua 官方同款源码+同构链接,见 native/Android.mk.cjson),不再用安装包提取的预编译版;链接输入直接用 `app/src/main/jniLibs` 宿主 so,v1.5.1 起 `native/prebuilt-luajava/` 冗余副本已删除
17. **`luajava.instanceof` 已改宽容**:非 JavaObject(如 table)返回 false 而非抛错(loadlayout 1.5 版依赖此语义)。
18. **`luajava.override` 已补 C 入口**(`luajava_override` → 转发 Java `javaOverride(long, Class)`,LuaEnhancer 生成子类)。LuaAppX 项目大量使用。
19. **`loadlayout.lua` 的 setAttribute 有 setter 探测缓存**(类名#属性名 → "setter"/"assign" 模式)。**不能缓存方法引用本身**(luajava 索引未知方法名不抛错,返回惰性代理,调用才抛,缓存引用会 NPE)。曾因缓存引用导致 home 页 RecyclerView NPE,已修。
20. **`LuaJavaAPI` 的 Object 重载追加块**在文件尾部(marker: "官方 luajava.c(AndroLua_pro)兼容重载"),提取工具用花括号配对(签名行 `( ` 后无 `)`,参数列表跨行时 grep 单行正则会漏)。

### 主题/UI
21. **targetSdk 35+ 强制 edge-to-edge**:values-v35 / values-night-v35 主题已加 `android:windowOptOutEdgeToEdgeEnforcement`(覆盖 Theme.Material3.Blue 系/AppTheme/AppWelcome)。删掉会导致页面内容顶进状态栏。
22. **页面 Lua 脚本里 `setTheme` 必须在 `setContentView` 之前**(decor 由脚本自己的 setContentView 创建,此前有效)。`env.lua` 里曾有无效的 setTheme(死代码)已删。
23. **`LuaActivity.initENV` 只读项目目录的 init.lua**(`luaDir.startsWith(luaExtDir)` 判定)。IDE 页面目录下的 init.lua 曾把 debugmode=true 误应用到 IDE 页面导致主页崩溃。
24. **打包项目 APK 用的宿主必须是正式版**——ASAN 诊断版(带 wrap.sh)打出的项目 APK 启动卡死。

## ASAN 诊断版构建(临时诊断用,勿日常/勿打包)

```bash
# 1) 各模块 Android.mk 的 include $(CLEAR_VARS) 后插入: LOCAL_SANITIZE := address
# 2) jniLibs/<abi>/ 放 wrap.sh(LD_PRELOAD 指向同目录 libclang_rt.asan-<arch>-android.so)
#    与 libclang_rt.asan-<arch>-android.so(NDK: toolchains/llvm/prebuilt/*/lib/clang/18/lib/linux/)
# 3) app/build.gradle.kts: packaging { jniLibs.useLegacyPackaging = true }
# 4) 构建后 AGP 会剔除 wrap.sh,需手动: zip 追加 wrap.sh → build-tools/35.0.0/apksigner 重签
#    (keystore=根目录 BriarLua.keystore, alias=release, 密码=BRIARLUA_KEYSTORE_PASSWORD 环境变量)
# 5) 装机复现 → logcat 过滤 "AddressSanitizer" → 输出含精确 C 源码行号
```

## 目录结构

```
BriarLua/
├── app/                    # 主模块(LuaActivity/Http/调试面板/页面 assets 等)
│   └── src/main/
│       ├── assets/         # IDE 页面(page/)、布局(layout/)、lua(env/mod/utils)
│       ├── jniLibs/        # 21 个 so(13 自研 + 8 LuaAppX 遗留)
│       ├── java/           # com.androlua.*(LuaActivity/Http/LuaEnhancer/…)、com.briarlua.utils.*
│       ├── res/            # 主题(values、values-night、values-v35 opt-out)
│       └── resources/lua/  # 壳 Lua 库(import/loadlayout/json/cjson 兜底/logcat/console/…)
├── lemon-material-ui/      # 子模块(自研 Material 组件库,Kotlin)
├── native/                 # 自研 luajava 等 13 模块 C 源(含已修复的 luaconf/luajava.c)
├── BUILD.md                # 本文档(构建与部署)
├── README.md               # 项目说明
└── THIRD-PARTY-NOTICES.md  # 第三方组件与许可
```

## Lua 环境扩展(BriarLua 宿主提供,项目可直接用)

- 全局类(经 import.lua globalMT 自动导入):`Http`(com.androlua.Http)、`LuaActivity`、`android.R`、`L`(LuaState)等
- `table.clear` / 全局 `unpack`
- `package.preload["cjson"]`(纯 Lua json 兜底)
- `luajava.override` / `luajava.instanceof`(宽容版)
- `bindClass`(luajava.bindClass 别名)

## 打包源码压缩包时绝对不可遗漏 `app/src/main/assets/page/build/`

> **这是 BriarLua 的「打包 APK」页面(Build.lua),遗漏后 IDE 将丢失打包功能入口。**
> 根目录 `.gitignore` 的 `build/` 规则(不带锚点)会匹配任意深度的 build 目录——用 `git archive` 或任何遵循 gitignore 的工具打源码包时,`assets/page/build/` 会被静默剔除!
> 正确做法:直接 `zip -r` 源码树并手工排除构建产物(`.gradle/`、`app/build/`、`lemon-material-ui/build/`、`native/obj/`、`native/libs/`、`local.properties`),打包后必须 `unzip -l | grep page/build` 验收。`app/.gitignore` 的 `/build` 只锚定 app/build,不影响 assets。
