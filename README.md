# BriarLua
Android 端 Lua IDE + 独立 APK 打包器。在手机上写 Lua、搭界面、一键把项目打包成可分发的独立 APK。
基于 AndroLua+ / LuaAppX Pro 二次开发。

## 最近更新（v1.0.6）

**更新 FusionApp 同款 LuaRecyclerAdapter**

- 新增 `com.briarlua.LuaRecyclerAdapter` 高性能列表适配器——用法与 FusionApp / AndroLua 系一致（`LuaRecyclerAdapter(activity, data, itemLayout)` + `clickViewId` / `onItemClick` / `add` / `clear` / `getData`），并全面加固：条目动画缓存弱引用化（修复内存泄漏）、数据操作主线程归一（后台线程调用安全）、Glide 全类型图片加载（URL / 本地 / URI / 资源 id）、空数据与异常防崩
- API 精简（使用方式不变，均可属性赋值）：`setStyle` 更名 `setDefaultData`，移除冗余 `setAnimationUtil`
- 修复编辑器选择菜单翻转至选区下方时遮挡选区手柄的问题（下方翻转自动避让手柄下垂区间）
- API 浏览器新增 **BriarLua** 分类 Tab，`com.briarlua.*` 内置类一处可查；类加载失败改为页面内错误态展示，不再「点了没反应」
- 开发文档重构：新增「Material You 控件」「代码编辑器与选择菜单」「基础控件速查」「内置扩展类」章节，导航按 界面开发 / 扩展与工具 / 系统能力 / 工程发布 重组

## 功能概览

### IDE
- **主页**：项目管理——新建 / 导入 `.alp` / 重命名 / 删除 / 备份
- **编辑器**：文件树侧边栏、Lua 语法高亮、行号、自动缩进、输入法光标同步（含中文符号 / emoji 边界处理）、可自定义符号栏、Material 3 风格搜索替换栏、选区浮动菜单（自动避让选区手柄）、全项目代码搜索
- **API 浏览器**：全量类索引（含 BriarLua 分类），同名类全部可选，可复制导入语句
- **开发文档**：内置文档页，跟随全局主题
- **日志**：运行日志查看（重 IO 异步化）
- **主题**：7 个主题色 + Material You 动态取色，夜间模式

### 打包器（核心能力）
- 项目 Lua 源编译为 LuaAppX 双层编码字节码，连同资源重压进宿主 APK，产出**可直接安装分发的独立应用**
- 可配置应用名 / 包名 / 版本 / 图标 / targetSdk / 权限
- v1 + v2 + v3 全签名方案（AOSP apksig）
- 防无效包三重保险：arsc 条目对齐、构建门禁、产物完整性校验
- 后台独立 LuaState 执行，不阻塞 UI，实时进度

### Lua 运行环境
- Lua 5.3.3（定制，LuaAppX 编码字节码兼容）
- luajava 桥（自研重编 libluajava.so）：`import` / `bindClass` / `luajava.override`（动态子类生成）等官方 18 个重载
- **内置扩展类**：`com.briarlua.LuaRecyclerAdapter`（FusionApp 同款列表适配器，支持 `setDefaultData` / 动画 / 过滤）、PhotoView、AsyncFile / AsyncShell、JsonUtil / UiUtil、LuaMaterialDialog 等，用法详见应用内「开发文档」
- 21 个原生库：luajava、cjson、socket、luv(libuv)、xml、md5、canvas、crypt、bson、regex、sensor、zip 等
- 壳 Lua 库：loadlayout 布局引擎、json.lua、cjson 纯 Lua 兜底层
- `Http` 全局类：GET / POST / 下载 / cookie，异步回调进 Lua
- 纯 Java 调试面板：`init.lua` 设 `debugmode=true` 自动挂载，拦截 print 与错误、查看全局变量
- CrashManager + ErrorActivity 崩溃堆栈可视化

## 系统要求
| 项 | 值 |
|---|---|
| 最低支持 | Android 6.0（minSdk 23） |
| 目标平台 | Android 15（targetSdk / compileSdk 35） |
| CPU 架构 | armeabi-v7a + arm64-v8a |
| 原生库 | 16KB 页对齐（16KB 页设备可运行） |

## 构建
见 [BUILD.md](BUILD.md)。

```bash
./gradlew :app:assembleRelease
```

## 目录结构
```
BriarLua/
├── app/                    # 主模块
│   └── src/main/
│       ├── assets/         # IDE 页面(page/)、布局(layout/)、lua(env/mod/utils)
│       ├── jniLibs/        # 21 个 so(13 自研 + 8 遗留)
│       ├── java/           # com.androlua.*、com.briarlua.*、com.zzzmode.apkeditor
│       └── resources/lua/  # 壳 Lua 库
├── lemon-material-ui/      # Material 组件库(Kotlin)
├── native/                 # 13 个自研模块 C 源码(luajava/lua/cjson/...)
├── BUILD.md                # 构建文档
└── THIRD-PARTY-NOTICES.md  # 第三方组件与许可
```

## 致谢与许可
本项目基于 [AndroLua+](https://github.com/nirenr/AndroLua) / LuaAppX Pro 二次开发，内部包含 Lua、luajava、libuv、libzip、LuaSocket、Lua CJSON 等第三方组件，完整清单与许可见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。
