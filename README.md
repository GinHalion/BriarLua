# BriarLua

Android 端 Lua IDE + 独立 APK 打包器。在手机上写
Lua、搭界面、一键把项目打包成可分发的独立 APK。

基于 AndroLua+ / LuaAppX Pro 二次开发。

## 最近更新（v1.0.8）

**LuaRecyclerAdapter 新增子布局机制（setChildLayout）**

### 一、新 API

``` lua
-- 注册：条目布局里 id 为 talk_reply_box 的 ViewGroup 作为子容器
adapter.setChildLayout("talk_reply_box", "layout.item.talkreply")

-- 行数据：容器键的值为数组表，每个元素是一行子数据
adapter.add({
  talk_name = "张三",
  talk_content = "留言正文",
  talk_reply_box = {
    { reply_name = "李四", reply_time = "3分钟前",
      reply_content = "回复内容", reply_avatar = "https://..." },
  },
})

-- 清除注册（恢复旧行为）
adapter.setChildLayout("talk_reply_box", nil)
```

### 二、如何使用

-   **注册**：调用 `setChildLayout(容器id, 子布局名)`
    一次即可，之后所有条目行共用该注册。
-   **绑定规则**：子数据行与主行完全一致------字段名与子布局控件 id
    同名即自动绑定（TextView → `setText`，ImageView → Glide
    全类型）；不匹配任何控件 id 的键自动跳过（可放元数据）。
-   **多行子数据**：容器键的值为数组表，每个元素是一行子数据，按顺序渲染进容器。
-   **空态**：行数据无容器键或为空表 → 容器清空并 `GONE`；非空 →
    `VISIBLE`，杜绝复用残留。
-   **补绑回调**：`bindChildren` 在 `AdapterInterface` 回调之前执行，Lua
    回调可直接对已装载子视图挂监听。
-   **降级**：注册校验失败抛带原因的 `LuaException`，Lua 端可用 `pcall`
    拦截并回退旧方案。

### 三、实现要点（LuaRecyclerAdapter.java）

  --------------------------------------------------------------------------------------------------------------------
  项目                                说明
  ----------------------------------- --------------------------------------------------------------------------------
  注册即校验                          `setChildLayout` 当场试装载：先以 `ViewGroup.class` 装载条目布局（与主条目
                                      `onCreateViewHolder` 路径一致），取出容器并校验 id 存在且为
                                      `ViewGroup`，再以容器实际类型 `container.getClass()` 作为 `parentViewClass`
                                      试装载子布局。与绑定期 `bindChildren`
                                      使用完全相同的装载路径，因此注册期通过的子布局在绑定期必然可装载（含
                                      margin、padding 等全部布局语义）。

  装载路径                            使用 `loadlayout(childLayout, childViews, container.getClass())`
                                      三参形式，`parentViewClass` 传入容器实际类型，保证子根布局的 margin 等
                                      `LayoutParams` 语义与 XML 嵌套一致。

  子视图复用                          容器内已有子视图复用并重新绑定（使用 `WeakHashMap` 持有各子视图的 `views`
                                      表）；不足时才新建，多余项从尾部移除。滚动复用时零新建，与主列表采用相同策略。

  错误策略                            装载失败时仅调用一次 `sendError`，随后安全跳过，不崩溃、不刷屏。

  主行绑定                            将原 `onBindViewHolder` 内联绑定循环提取为私有方法
                                      `bindRow(row, views, skipKey)`，主行和子行共用。主行调用时 `skipKey`
                                      为容器键；未注册子布局时 `skipKey` 为 `null`，行为与旧版等价。

  `extractRowHeaders`                 签名由 `(row, holder)` 调整为
                                      `(row, views)`，支持子行级图片请求头判断，语义不变。

  `release()`                         同步清空子布局注册与子视图缓存。
  --------------------------------------------------------------------------------------------------------------------

### 四、兼容性

-   **纯增量**：不调用 `setChildLayout`
    时，全部代码路径与此前版本行为等价。唯一改动是绑定循环提取为私有方法，以及
    `extractRowHeaders` 调整参数，均为内部实现；既有脚本无需修改。
-   **构造器与公开方法**：构造器及全部原有公开方法签名一字未改。
-   **AdapterInterface**：仍为 2
    参数（`viewHolder, position`），旧回调写法直接可用。
-   **旧版适配器兼容**：旧版适配器不存在 `setChildLayout`，Lua 端可用
    `pcall` 探测失败后回退旧方案，框架与脚本升级顺序不受限制。
-   **改动范围**：单文件改动，无新增依赖、无新增权限、无 API level
    变化（`WeakHashMap` 支持 API 1+）。
-   **回归清单**：既有 `LuaRecyclerAdapter`
    页面中的构造、`add`、`clear`、点击、过滤、动画、图片请求头等行为保持不变。
-   **运行环境**：`com.briarlua` v1.0.8（`versionCode 13`）。

## 历史更新（v1.0.7）

**LuaRecyclerAdapter Fusion 兼容 + 图片请求头 + 清除缓存**

-   **Fusion 兼容 `viewHolder.tag`**：ViewHolder 新增公有 `tag` 字段（与
    `views` 指向同一控件表），Fusion 系脚本
    `myAdapter.setAdapterInterface(...)` 中通过 `viewHolder.tag.image`
    获取控件的写法无需修改。
-   **Glide 图片请求头**：网络图片支持携带 HTTP Header。可在
    `Adapter.add` 行数据中直接设置
    `header = { Referer = ... }`；另提供适配器级 `setImageHeaders`
    和属性表级 `{ src = ..., header = ... }` 两级按键覆盖。未指定 UA
    时自动附带默认 UA；缓存键包含请求头，避免相同 URL、不同 Header
    的图片串图。
-   **设置页新增「清除缓存」**：清除图片缓存与其他临时缓存（Glide
    内存/磁盘缓存、`cacheDir`、代理
    dex），不影响工程与设置，清除后软件可正常运行。
-   新增内置扩展类
    `com.briarlua.utils.CacheCleaner`，提供缓存统计与清空功能。

## 历史更新（v1.0.6）

**更新 FusionApp 同款 LuaRecyclerAdapter**

-   新增 `com.briarlua.LuaRecyclerAdapter` 高性能列表适配器，用法与
    FusionApp / AndroLua
    系一致：`LuaRecyclerAdapter(activity, data, itemLayout)`，支持
    `clickViewId`、`onItemClick`、`add`、`clear`、`getData`
    等接口；同时进行全面加固：条目动画缓存弱引用化（修复内存泄漏）、数据操作统一在主线程执行（后台线程调用安全）、Glide
    全类型图片加载（URL / 本地路径 / URI / 资源
    id），并增强空数据与异常处理。
-   API 精简（使用方式不变，均可通过属性赋值）：`setStyle` 更名为
    `setDefaultData`，移除冗余的 `setAnimationUtil`。
-   修复编辑器选择菜单翻转至选区下方时遮挡选区手柄的问题（下方翻转时自动避让手柄下垂区域）。
-   API 浏览器新增 **BriarLua** 分类 Tab，`com.briarlua.*`
    内置类可集中查阅；类加载失败时改为页面内错误状态展示，不再出现点击后无响应的问题。
-   开发文档重构：新增「Material You
    控件」「代码编辑器与选择菜单」「基础控件速查」「内置扩展类」章节；导航按「界面开发
    / 扩展与工具 / 系统能力 / 工程发布」重新组织。

## 功能概览

### IDE

-   **主页**：项目管理，支持新建、导入 `.alp`、重命名、删除、备份。
-   **编辑器**：文件树侧边栏、Lua
    语法高亮、行号、自动缩进、输入法光标同步（含中文符号 / emoji
    边界处理）、可自定义符号栏、Material 3
    风格搜索替换栏、选区浮动菜单（自动避让选区手柄）、全项目代码搜索。
-   **API 浏览器**：全量类索引（含 BriarLua
    分类），同名类均可选择，并支持复制导入语句。
-   **开发文档**：内置文档页面，跟随全局主题。
-   **日志**：运行日志查看，重 IO 操作异步化。
-   **主题**：7 个主题色 + Material You 动态取色，支持夜间模式。

### 打包器（核心能力）

-   将项目 Lua 源码编译为 LuaAppX 双层编码字节码，并连同资源重新压入宿主
    APK，产出**可直接安装、分发的独立应用**。
-   可配置应用名、包名、版本、图标、`targetSdk` 和权限。
-   支持 v1 + v2 + v3 全签名方案（AOSP apksig）。
-   防无效包三重保险：`arsc` 条目对齐、构建门禁、产物完整性校验。
-   后台使用独立 LuaState 执行，不阻塞 UI，并提供实时进度。

### Lua 运行环境

-   Lua 5.3.3（定制版本，兼容 LuaAppX 编码字节码）。
-   luajava 桥（自研重编 `libluajava.so`）：支持
    `import`、`bindClass`、`luajava.override`（动态子类生成）等官方 18
    个重载。
-   **内置扩展类**：`com.briarlua.LuaRecyclerAdapter`（FusionApp
    同款列表适配器，支持 `setDefaultData` / 动画 / 过滤 /
    `viewHolder.tag` / 图片请求头）、PhotoView、AsyncFile /
    AsyncShell、JsonUtil / UiUtil、CacheCleaner、LuaMaterialDialog
    等。具体用法详见应用内「开发文档」。
-   21
    个原生库：luajava、cjson、socket、luv（libuv）、xml、md5、canvas、crypt、bson、regex、sensor、zip
    等。
-   壳 Lua 库：`loadlayout` 布局引擎、`json.lua`、cjson 纯 Lua 兜底层。
-   `Http` 全局类：支持 GET / POST / 下载 / cookie，异步回调进入 Lua。
-   纯 Java 调试面板：在 `init.lua` 中设置 `debugmode=true`
    即可自动挂载，拦截 `print` 与错误，并查看全局变量。
-   CrashManager + ErrorActivity：提供崩溃堆栈可视化。

## 系统要求

  项目       要求
  ---------- -----------------------------------------
  最低支持   Android 6.0（minSdk 23）
  目标平台   Android 15（targetSdk / compileSdk 35）
  CPU 架构   armeabi-v7a + arm64-v8a
  原生库     16KB 页对齐（16KB 页设备可运行）

## 构建

构建方式详见 [BUILD.md](BUILD.md)。

``` bash
./gradlew :app:assembleRelease
```

## 目录结构

``` text
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

本项目基于 [AndroLua+](https://github.com/nirenr/AndroLua) / LuaAppX Pro
二次开发。项目包含 Lua、luajava、libuv、libzip、LuaSocket、Lua CJSON
等第三方组件，完整清单与许可信息见
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。
