# 第三方组件与许可声明

BriarLua 基于 AndroLua+ / LuaAppX Pro 二次开发，源码树中包含以下第三方组件。
各组件的原始版权与许可全文以对应上游发布为准，本文件为汇总声明。

## Lua 运行时与语言库

| 组件 | 目录 | 作者 | 许可 |
|---|---|---|---|
| Lua 5.3.3 | `native/lua` | PUC-Rio（Roberto Ierusalimschy 等） | MIT |
| luajava | `native/luajava`、`app/src/main/java/com/luajava` | Kepler Project | MIT |
| Lua CJSON | `native/cjson` | Mark Pulford | MIT |
| LuaSocket / mime | `native/socket` | Diego Nehab | MIT |
| lua-md5 | `native/md5` | Roberto Ierusalimschy 等 | MIT 风格（Kepler） |
| lrexlib（regex） | `native/regex` | Reuben Thomas | MIT |
| LuaXML | `native/xml` | Gerald Franz | MIT |
| luv（Lua 绑定） | `native/luv` | luv 项目 | MIT |

## 通用 C 库

| 组件 | 目录 | 许可 |
|---|---|---|
| libzip | `native/zip` | Dieter Baron、Thomas Klausner，BSD-3-Clause |
| libuv | `native/luv/uv` | libuv 贡献者，MIT |

## Java 组件

| 组件 | 位置 | 来源 | 许可 |
|---|---|---|---|
| dx / dex 机器 | `app/src/main/java/com/android/cglib/dx` | Android Open Source Project | Apache-2.0 |
| 部分_behavior / 前台组件 | `app/src/main/java/com/briarlua/behavior`、`lemon-material-ui` 部分文件 | Android Open Source Project / Google LLC | Apache-2.0 |
| SwipeRefreshLayout 兼容实现 | `lemon-material-ui/.../swiperefreshlayout` | Android Open Source Project | Apache-2.0 |
| HexEncoder | `app/src/main/java/com/zzzmode/apkeditor/utils/HexEncoder.java` | The Legion of the Bouncy Castle | MIT（Bouncy Castle 变体） |
| Commons 风格工具类 | `app/src/main/java/com/zzzmode/apkeditor/utils` 部分 | Apache Commons 衍生 | Apache-2.0 |

## 运行时依赖（Maven）

构建依赖（AndroidX、Material Components、OkHttp、Glide 等）的许可由其发行包携带，详见各上游仓库。
