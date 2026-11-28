# BriarLua

Android 端 Lua IDE + 独立 APK 打包器。在手机上写
Lua、搭界面、一键把项目打包成可分发的独立 APK。

## 最近更新（v1.1.2）

### 新增代码加密功能（私有版）

### luajava 代理返回值 ClassCastException 根治

## 一、现象

Lua 脚本里 `luajava.override` 给 Java
接口/抽象类生成动态代理，代理方法声明返回 `int`/`float` 等数值类型（如
`PagerAdapter.getCount()`），Lua 函数返回 Lua 整数（如
`#pageViews`、字面量 `3`）时真机抛：

``` text
java.lang.ClassCastException: java.lang.Long cannot be cast to java.lang.Integer
```

Lua 侧必须写 `#pageViews + 0.0`（Lua 浮点）才能绕过。前版本已实证根因在
Java 层拦截器，当时只并入了 Lua 层 `+0.0` 规避方案。

## 二、根因链（源码逐环节）

1.  Lua 5.3 整数是 64 位 `lua_Integer`。`LuaState.toJavaObject()` 对 Lua
    整数走 `toInteger(idx)`（返回 Java `long`）→ 自动装箱
    **`java.lang.Long`**；Lua 浮点走 `toNumber(idx)`（返回 `double`）→
    装箱 **`java.lang.Double`**。
2.  `LuaObject.call()` → `call_aux()` → `L.toJavaObject(-1)`，即 Lua
    函数的返回值到达拦截器时数值只会是 Long 或 Double。
3.  三个拦截器（`LuaMethodInterceptor` / `LuaAbstractMethodInterceptor`
    / `LuaInvocationHandler`）都只有 `ret instanceof Double` 才调
    `LuaState.convertLuaNumber`------**Long 直接跳过转换**。
4.  `LuaState.convertLuaNumber` 其实一直提供 `Long` 重载（完整覆盖
    `int`/`long`/`float`/`double`/`byte`/`short`），但从未被拦截器调用------上游
    AndroLua 的半成品功能。
5.  Long 逃逸到 cglib `Enhancer`（dex
    生成）代理方法的返回处理：基本类型目标先
    `code.cast(retPackLocal, retObjLocal)` 强转包装类再
    `xxxValue()`，引用类型目标直接
    `code.cast(retLocal, retObjLocal)`。`getCount()` 声明返回 `int` →
    `(Integer)` 强转一个 `Long` → **CCE**。
6.  `+ 0.0` 能绕过的原因：Lua 浮点装箱 Double，走拦截器已有的 Double →
    int 转换分支，产出 Integer，强转成功。

对照：方法**调用**方向（Lua → Java 传参）在 `LuaJavaAPI`
有完整转换（`isInteger → convertLuaNumber(Long, parameter)`），所以传参一直正常；只有代理**返回值**方向缺转换。

## 三、修复方案

### 3.1 三个拦截器：Double 分支后补 Long 与其余 Number 分支

`retType` 来自 `methodProxy.getOriginalMethod().getReturnType()` /
`method.getReturnType()`（`java.lang.reflect.Method`），即被代理方法的声明返回类型。Double
分支保持原样（含其无守卫的历史语义），其后新增两个带守卫分支：

``` java
if (ret != null && ret instanceof Double) {
    ret = LuaState.convertLuaNumber((Double) ret, retType);
} else if (ret instanceof Long
        && (retType.isPrimitive() || Number.class.isAssignableFrom(retType))) {
    ret = LuaState.convertLuaNumber((Long) ret, retType);
} else if (ret instanceof Number
        && (retType.isPrimitive() || Number.class.isAssignableFrom(retType))) {
    ret = LuaState.convertLuaNumber((Number) ret, retType);
}
```

守卫的必要性：目标为
`Object`/`String`/`Boolean`/自定义类等非数值类型时，Long/装箱对象**原样透传**（旧行为如此------如
Object 返回类型 + Lua 整数本来就返回 Long
对象）。若不加守卫，`convertLuaNumber` 对非数值目标返回
null，会把原本能透传的值变成 null（行为回归）。Boolean/String
等返回值本身不是 Number，不进任何新分支，行为零变化。

### 3.2 LuaState.convertLuaNumber：修复 Long 重载的 Double 目标缺陷

旧代码 `convertLuaNumber(Long, Double.class 目标)` 分支
`return lg;`------把 Long 原样返回给 Double
目标，必然类型错配（此前无调用方，缺陷未暴露；拦截器接通后必须修正）：

``` java
} else if (retType.isAssignableFrom(Double.class)) {
    return new Double(lg.doubleValue());   // 旧: return lg;
}
```

### 3.3 LuaState 新增 convertLuaNumber(Number) 统一入口

Long/Double 走既有重载；Integer/Float/Short/Byte 等其余 Number 子类（经
userdata 中转的 Java 装箱对象，如 `luajava.new(java.lang.Float, 1.5)`
的返回值）分别经 `longValue()`/`doubleValue()`
归一后复用同一套转换。**不引入反射**，纯 `instanceof` 分派，开销可忽略。

### 3.4 LuaState 新增 defaultNumberValue：数值零值兜底

拦截器旧代码在 Lua 函数未被实现（`func.isNil()`）或返回 nil/Lua
报错时对数值返回类型统一 `return 0`（装箱
Integer）------long/double/float/short/byte
返回类型的代理方法直接被生成代码的 `(Long)`/`(Double)`
强转杀死。现按类型返回零值（int/Integer 仍返回 Integer 0，行为不变）：

``` java
return LuaState.defaultNumberValue(retType);   // 旧: return 0;
```

`LuaInvocationHandler` 原有的 `defaultReturnValue`
已按类型处理，未改动。

### 3.5 不需要改 Enhancer 生成代码

修复后拦截器产出的对象与目标类型严格一致，生成代码的强转必然成功。参数打包、`invokeSuper`、`SuperCall`
等其余生成逻辑均不受影响。

## 四、影响面

-   **`luajava.override`**（类/抽象类 → `LuaMethodInterceptor` /
    `LuaAbstractMethodInterceptor`）：`PagerAdapter.getCount()` /
    `getItemPosition()`、`getPageWidth()`（float）、`View.getAccessibilityViewId()`
    等一切数值返回方法，以及任何用户自定义接口的
    int/long/short/byte/float/double/Integer/Long/Float/Double/Number
    返回方法。
-   **接口代理**（`luajava.createProxy` / `LuaObject.createProxy` →
    `LuaInvocationHandler`）：`Runnable.run`（void，不受影响）、`Comparator.compare`（int）等接口同样受益。
-   **不变**：Double/Float/Boolean/String/对象类型返回值的行为；`+0.0`
    写法（含 `loadlayout.lua` v2.1.4 的
    `LuaPagerAdapter`）；`pushObjectValue` 拆箱逻辑；`LuaJavaAPI`
    参数方向转换；所有 Lua 脚本。

## 五、验证

1.  **纯 JVM 模拟链路测试（复刻 `toJavaObject` 装箱 → 旧/新拦截器 →
    生成代码强转语义）：55/55 通过**
    -   旧逻辑负向对照：Long → int/Integer/float 方法复现 CCE。
    -   新逻辑全矩阵：Long/Double/Integer/Float 返回 × 13
        种数值目标全部正确。
    -   `+0.0`（Double）→ int = 3（验收 1/2）。
    -   对象类型透传新旧一致（Long/Integer/Float/String/Boolean +
        Object）。
    -   nil 兜底：long → 0L、double → 0.0、int → 0（与旧一致）、boolean
        → false、Object → null。
    -   边界值：Long 溢出 int 窄化回绕（与旧 Double
        语义一致）、负数、浮点截断。
2.  **Gradle `assembleRelease` 构建通过。**
3.  **`javap -c` 反汇编产物确认**三个拦截器均含
    `convertLuaNumber(Long, Class)` / `convertLuaNumber(Number, Class)`
    分支调用。
4.  **真机回归脚本**：不加 `+0.0` 的
    `getCount = function(super) return 3 end` 输出 3 不崩；`+0.0`
    版本不变；自定义接口 long/float/double 返回值正确。

## 调整补全

### Doc.html 开发文档补全，Java Api 阅览器补全

-   **「事件与回调」**：新增 `luajava.override`（类/抽象类代理、super
    回调父类）用法，以及数值返回类型直返 Lua 数字的说明，标注 `+ 0.0`
    已退役。
-   **「核心 API」**：补 `luajava.override(class, overrides)` 条目。
-   **「列表适配器」**：补 `setChildLayout`
    子布局机制（v1.0.8）用法与示例，并补齐图片请求头三级说明（v1.0.7，与私有版文档对齐）。
-   **私有版 Java API 阅览器**：补全代码加密等相关 Java 类。

## 历史更新（v1.0.9）

**JNI 全局引用溢出崩溃修复 + ErrorActivity 显示修复**

修复用户报告的编辑器触摸崩溃（SIGABRT，"global reference table overflow
(max=51200)"，tombstone 中 24041×LuaEditor + 23953×MotionEvent
同一实例全部存活）：

-   **luajava 桥三层修复**：
    1.  native 方法缓存表弱值化------旧实现的缓存闭包以 userdata 本体为
        upvalue 挂在共享元表（registry 根）上，任何被 Lua 调用过方法的
        Java 对象都被永久锚定、`__gc` 永不执行、GlobalRef 只增不减。
    2.  同一 Java 对象复用同一 userdata（Java WeakHashMap → token + Lua
        registry 弱值缓存表，onTouch 每次推送同一编辑器/事件对象不再重复
        NewGlobalRef）。
    3.  JavaObject 存活配额 120000 → 45000（原值高于 ART 51200
        硬上限，安全阀永远晚于 abort 生效，现改为先行触发可捕获异常 +
        强制 GC），另每 4096 个新建 userdata 附带一次增量 Lua GC。
-   **修复崩溃后 ErrorActivity 不显示（双根因）**：CrashManager 此前从未
    install（Java 异常走系统默认处理）；native abort 更是绕过一切 Java
    处理器。现 Application.onCreate 最先安装
    CrashManager；`libluajava.so` 新增 `crashhandler.c`------捕获
    SIGABRT、落盘崩溃报告（信号/PC/arm64 帧链）→ fork 子进程 `am start`
    **立即拉起 ErrorActivity** → 父进程恢复 debuggerd
    处理器重触信号（tombstone
    不受影响）；若立即拉起被后台启动限制拦截，下次启动时读取遗留日志兜底展示。

## 历史更新（v1.0.8）

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
    {
      reply_name = "李四",
      reply_time = "3分钟前",
      reply_content = "回复内容",
      reply_avatar = "https://..."
    },
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
-   **`AdapterInterface`**：仍为 2
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
-   **新增内置扩展类**：`com.briarlua.utils.CacheCleaner`，提供缓存统计与清空功能。

## 历史更新（v1.0.6）

**更新 FusionApp 同款 LuaRecyclerAdapter**

-   新增 `com.briarlua.LuaRecyclerAdapter` 高性能列表适配器，用法与
    FusionApp / AndroLua
    系一致：`LuaRecyclerAdapter(activity, data, itemLayout)`，支持
    `clickViewId`、`onItemClick`、`add`、`clear`、`getData`
    等接口；同时进行全面加固：条目动画缓存弱引用化（修复内存泄漏）、数据操作统一在主线程执行（后台线程调用安全）、Glide
    全类型图片加载（URL / 本地路径 / URI / 资源
    id），并增强空数据与异常处理。
-   **API 精简（使用方式不变，均可通过属性赋值）**：`setStyle` 更名为
    `setDefaultData`，移除冗余的 `setAnimationUtil`。
-   **编辑器修复**：修复编辑器选择菜单翻转至选区下方时遮挡选区手柄的问题（下方翻转时自动避让手柄下垂区域）。
-   **API 浏览器完善**：API 浏览器新增 **BriarLua** 分类
    Tab，`com.briarlua.*`
    内置类可集中查阅；类加载失败时改为页面内错误状态展示，不再出现点击后无响应的问题。
-   **开发文档重构**：新增「Material You
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
-   **防无效包三重保险**：`arsc` 条目对齐、构建门禁、产物完整性校验。
-   后台使用独立 LuaState 执行，不阻塞 UI，并提供实时进度。

### Lua 运行环境

-   **Lua 5.3.3**（定制版本，兼容 LuaAppX 编码字节码）。
-   **luajava 桥**（自研重编 `libluajava.so`）：支持
    `import`、`bindClass`、`luajava.override`（动态子类生成）等官方 18
    个重载。
-   **内置扩展类**：`com.briarlua.LuaRecyclerAdapter`（FusionApp
    同款列表适配器，支持 `setDefaultData` / 动画 / 过滤 /
    `viewHolder.tag` / 图片请求头）、PhotoView、AsyncFile /
    AsyncShell、JsonUtil / UiUtil、CacheCleaner、LuaMaterialDialog
    等。具体用法详见应用内「开发文档」。
-   **21
    个原生库**：luajava、cjson、socket、luv（libuv）、xml、md5、canvas、crypt、bson、regex、sensor、zip
    等。
-   **壳 Lua 库**：`loadlayout` 布局引擎、`json.lua`、cjson 纯 Lua
    兜底层。
-   **`Http` 全局类**：支持 GET / POST / 下载 / cookie，异步回调进入
    Lua。
-   **纯 Java 调试面板**：在 `init.lua` 中设置 `debugmode=true`
    即可自动挂载，拦截 `print` 与错误，并查看全局变量。
-   **CrashManager + ErrorActivity**：提供崩溃堆栈可视化。

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
