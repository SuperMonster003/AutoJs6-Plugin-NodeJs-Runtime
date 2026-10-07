<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>用于 AutoJs6 的 Node.js 24.21.0 原生运行时插件</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 语言 (Languages)

******

当前 README.md 支持以下语言:

- 简体中文 [zh-Hans] # 当前
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ar.md)

******

### 简介

******

AutoJs6 Node.js Runtime 插件为 AutoJs6 提供内嵌 Node.js 24.21.0 原生运行时, 用于执行 Node.js 脚本和插件化运行时任务. 在 Android 17 及以上, 在 AutoJs6 插件中心开启此插件前需允许访问附近的设备. 也可在此插件的设置页面中管理本地网络权限. 未获授权时插件保持关闭, 自动启动将静默跳过. 此权限属于插件自身, 与 AutoJs6 的授权相互独立.

******

### 功能

******

- 提供 `nodejs` 插件服务, 插件 ID 为 `nodejs`, 引擎为 `nodejs`.
- 通过 `org.autojs.plugin.nodejs.RUNTIME` 为宿主提供同步脚本执行和运行时预热.
- 支持覆盖排队与执行全过程的脚本超时, 超时返回 `ERR_AUTOJS6_SCRIPT_TIMEOUT`; 未指定超时时仍可无限运行
- 全部 Node.js 内建模块按 Node 自身的 `module.builtinModules` 清单直接返回原生导出 (含 `dgram`, `http2`, `trace_events`, `wasi`, `cluster`, `repl` 等), 仅保留 `worker_threads`, `child_process`, `inspector` 与原生网络模块的请求级门禁 (1.6.0 起).
- 支持 Debug 构建显式开启本地 `inspector` 调试, 仅监听 localhost 并通过 `adb forward` 连接
- 支持 CommonJS/ESM 源码, 模块源码, 工作目录, 沙盒根目录, 环境变量, stdout/stderr 结果回传.
- ESM 入口与 dynamic `import()` 使用 V8 native linker, 支持循环依赖、可变导出与 re-export live binding; CommonJS `require(esm)` 保留同步互操作边界.
- 文件系统使用 Node.js 原生 fs 模块, 文件可达性完全由插件自身的 Android 权限决定; 运行时不再保留 `/proc`, `/sys`, `/dev` 等拒绝边界 (1.6.0 起).
- 执行宿主提供的 TypeScript 编译产物, 并可通过 provider v3 请求按需编译运行中创建的项目 `.ts`/`.mts`/`.cts`; 运行时类型剥离 fallback 与兼容开关已删除, direct raw dispatch 始终 fail-closed.
- 提供宿主能力代理与 live bridge, 可注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等运行时模块.
- 附带 `sample/nodejs` 示例项目与 `docs/HOST-API.md` 宿主 API 能力清单.
- 插件信息, 使用说明, README 与 CHANGELOG 均支持西班牙语/法语/俄语/阿拉伯语/日语/韩语/英语/简体中文/香港繁体/台湾繁体.
- 附带多入口终端启动器 `libnodexe.so` 与 npm / corepack 资产, 通过 `NODE_CLI_*` manifest meta-data 声明, 使 AutoJs6 终端 (宿主 6.8.0+) 能在自身 uid 的 shell 中运行 `node`, `npm`, `npx`, `corepack`, `yarn` 与 `pnpm`.

******

### 使用示例

******

```js
"nodejs";

console.log(process.version);
console.log("AutoJs6 Node.js runtime");
```

将插件安装并在 AutoJs6 插件中心启用后, 以 `"nodejs";` 指令启动 Node.js 脚本. 更多示例位于 `sample/nodejs`.

******

### 快速上手

******

- **怎么装** — 从 [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases) 下载对应 ABI 的 APK (不确定就选 `universal`) 并安装; 或本地构建 `.\gradlew.bat :app:assembleDebug` 后安装 `app/build/outputs/apk/debug/` 下的产物. 然后在 AutoJs6 的插件中心启用本插件. Android 11+ 如需访问共享存储, 还要在系统设置中为本插件授予“所有文件访问”; 未授予时出现 `EACCES` 属预期行为.
- **怎么跑** — 在 AutoJs6 编辑器中新建脚本, 首行写 `"nodejs";`, 其余按桌面 Node.js 写法编写 (支持 CommonJS/ESM/npm 纯 JS 包/网络内建模块), 点击运行即可. 运行中即时输出, 可随时手动停止. raw `.ts`/`.mts`/`.cts` 当前必须先由宿主编译为 JavaScript; 插件不内置 `tsc`.
- **出错了看哪里** — 脚本报错时控制台显示 JS 栈与一行错误码 (如 `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED`); 更多细节用 `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin` 查看插件进程日志. 宿主 API 可用性以 `docs/HOST-API.md` 为准.

******

### 运行时资料

******

- 运行时槽位: `node24_21`.
- 插件 ID: `nodejs`, 引擎: `nodejs`.
- 运行时服务动作: `org.autojs.plugin.nodejs.RUNTIME`.
- 原生运行库: `libnode.so`, `libautojs6-node.so` 和 `libnodexe.so`.
- Intl: ICU 78, 仅含英文区域数据 (`--with-intl=small-icu`); `Intl`, 正则表达式的 Unicode 属性转义以及 Node 自身写到 stderr 的错误输出均可在 AutoJs6 终端中使用, `NODE_ICU_DATA` 可指向完整的 ICU 数据文件.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 以及 `universal`.
- 文件系统: Node.js 原生 fs 模块, Android 权限允许范围内可访问任意设备路径, 运行时自身不保留拒绝边界.
- TypeScript: 接受宿主产物并可通过 provider v3 按需编译运行中创建的项目文件; direct raw TypeScript 返回 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, 原生 live binding 与循环依赖.
- 能力: 同步脚本执行, bundle transport, 原生内嵌运行时, 宿主能力代理, host capability live bridge.
- 文件流、gzip/deflate/Brotli 压缩流和基础 VM 执行正式化; 显式启用的 Debug Inspector 在 localhost 范围内正式支持.
- 嵌入执行不再输出 ExperimentalWarning 提示, 保留 warning 事件、普通警告、弃用警告和错误; Node 上游 API 稳定性与终端默认行为不变.

******

### 发行历史

******

# v1.6.0

###### 2026/10/07

* `优化` fs, fs/promises 及 node:sqlite 模块改为直接使用 Node.js 原生实现, 移除运行时自设的 /proc, /sys, /dev 拒绝边界与路径校验包装层, 文件可达性完全由 Android 权限决定, 异步文件 API 经 libuv 线程池执行而不再阻塞事件循环, 路径错误返回 Node.js 原生错误码 (ERR_AUTOJS6_FS_NUL_BYTE, ERR_AUTOJS6_FS_PATH_ESCAPE, ERR_AUTOJS6_FS_SCOPED_PATH 及 ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED 不再由文件及数据库操作产生)
* `优化` process.cwd 与 process.chdir 改为 Node.js 原生行为, 脚本执行开始时以原生 chdir 进入工作目录, 相对路径由 Node.js 自行解析
* `优化` node:sqlite 的 ATTACH (含绑定参数及表达式形式的文件名), VACUUM INTO, 目录 PRAGMA 及 setAuthorizer 均为 SQLite 原生行为, enableLoadExtension 与 allowExtension 遵循 Node.js 原生状态检查
* `优化` 全部 Node.js 内建模块 (含 cluster, domain, repl, trace_events, wasi, constants, node:sea 等) 按 Node 自身的 module.builtinModules 清单直接返回原生导出, require, require.resolve, import, process.getBuiltinModule 与 module.isBuiltin 行为与桌面版一致, 仅保留 worker_threads, child_process, inspector 及原生网络模块的请求级门禁; node:wasi 随 /proc 边界的删除一并放开 (trace_events 在当前未启用 tracing 的运行库中与 Node 一样抛出 ERR_TRACE_EVENTS_UNAVAILABLE)
* `优化` process 对象仅保留 exit 的生命周期映射与 dlopen 的暂时拒绝, abort, kill, umask, binding, report, features 及 POSIX 身份调用均为 Node.js 原生行为; os 模块原生, homedir 与 tmpdir 通过执行环境变量 HOME/TMPDIR 指向工作目录, userInfo 返回真实的 Android 应用用户
* `优化` module 内建模块改为仅覆盖 createRequire 的 Node.js 原生对象, registerHooks, enableCompileCache, findPackageJSON 等可直接使用; 删除仅在禁用原生网络时可达的 http, https, net, tls, dns 受限实现, 禁用时直接返回 ERR_AUTOJS6_BUILTIN_DISABLED

# v1.5.7

###### 2026/10/05

* `优化` 插件中心图标采用统一工作台调整后的尺寸, 位置, 亮暗图稿与圆形底色, 保留可重建原稿和参数
* `优化` Android 系统应用信息图标与图标工作台共用图稿和亮暗底色, 保留插件中心透明图稿及现有启动器选项

# v1.5.6

###### 2026/09/19

* `新增` Node.js 致命错误支持生成诊断报告, 失败结果及 getRuntimeInfo 可读取崩溃原因, 堆用量, 调用栈和报告路径, maxOldGenerationSizeMb 选项支持设置单次执行的 V8 老生代堆上限
* `修复` require 加载大型 CommonJS 模块时因扫描过程占用过多堆内存而崩溃的问题
* `修复` AGP 9.1 构建时的 SDK XML v4 解析警告, 以及 JVM 单元测试误触发 APK 原生库对齐检查的问题 (共享构建插件 1.8.3)

##### 更多发行历史可参阅

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hans.md)

******

### 构建

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Release 构建:

```powershell
.\gradlew.bat :app:assembleRelease
```

构建参数来自 `version.properties`, 当前最低 SDK 为 24, 目标 SDK 为 36.

******

### 资源结构

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
app/src/main/assets/doc/CHANGELOG-*.md
```

`strings.xml` 提供插件描述本地化; `plugin_instruction.md` 提供宿主侧展示的插件使用说明. README 与 CHANGELOG 由 `.python/generate_markdown.py` 根据 JSON 源文件生成.

******

### 相关链接

******

- AutoJs6 文档: https://docs.autojs6.com
- Node.js 官方项目: https://github.com/nodejs/node
- Node.js 运行时构建计划: tools/nodejs/runtime-build/README.md
- 16 KB page alignment: [master/docs/16kb.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
