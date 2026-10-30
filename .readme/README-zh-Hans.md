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
- 本地 Inspector 调试支持 Debug 和 Release, 由宿主显式请求启用, 使用 localhost 与 ADB 转发
- 支持 CommonJS/ESM 源码, 模块源码, 工作目录, 沙盒根目录, 环境变量, stdout/stderr 结果回传.
- 模块加载由 Node.js 自身的 CommonJS / ESM loader 承担 (`require`, `import()`, `import.meta`, `require(esm)`, package exports / imports 均为原生); 运行时只注册 `module.registerHooks` 提供 `autojs6:*` 运行时模块、宿主提供的模块源码与请求级 builtin 门禁 (1.6.0 起).
- 文件系统使用 Node.js 原生 fs 模块, 文件可达性完全由插件自身的 Android 权限决定; 运行时不再保留 `/proc`, `/sys`, `/dev` 等拒绝边界 (1.6.0 起).
- 执行宿主提供的 TypeScript 编译产物, 并可通过 provider v3 请求按需编译运行中创建的项目 `.ts`/`.mts`/`.cts`; 运行时类型剥离 fallback 与兼容开关已删除, direct raw dispatch 始终 fail-closed.
- 提供宿主能力代理与 live bridge, 可注入 `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, `autojs6:bridge-permissions` 等运行时模块.
- 附带 `sample/nodejs` 示例项目与 `docs/HOST-API.md` 宿主 API 能力清单.
- 插件信息, 使用说明, README 与 CHANGELOG 均支持西班牙语/法语/俄语/阿拉伯语/日语/韩语/英语/简体中文/香港繁体/台湾繁体.
- 附带多入口终端启动器 `libnodexe.so` 与 npm / corepack 资产, 通过 `NODE_CLI_*` manifest meta-data 声明, 使 AutoJs6 终端 (宿主 6.8.0+) 能在自身 uid 的 shell 中运行 `node`, `npm`, `npx`, `corepack`, `yarn` 与 `pnpm`.
- 插件设置页支持 1-8 个并行执行槽 (默认 2) 与排队上限 (默认不限), 超额脚本按 FIFO 等待, 设置调整保留已接受的任务, idleExitMs 可回收空闲运行进程
- 减少脚本启动时不必要的 Web API 初始化, 工作区与 Worker 默认启用 Node 原生可移植模块编译缓存, 缓存可跨工作区副本复用并由 Node 自动使变更源码失效
- Node.js Worker 与 Bun 的 database/sqlite API, 支持参数化查询, 事务和数据库持久化.
- Bun 与 Node.js Worker 的传感器采样及订阅 API, 支持取消和监听器自动释放.
- Bun, Node.js 及 Worker 的宿主 Shell API, 支持流式输入输出, Shizuku 授权与进程清理.

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
- Intl: ICU 78 完整区域数据 (`--with-intl=full-icu`, 1.6.0 起; 1.5.1 至 1.5.7 为仅含英文数据的 small-icu); `Intl`, 区域格式化, 正则表达式的 Unicode 属性转义以及 Node 自身写到 stderr 的错误输出均与桌面版一致, 无需 `NODE_ICU_DATA`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 以及 `universal`.
- 文件系统: Node.js 原生 fs 模块, Android 权限允许范围内可访问任意设备路径, 运行时自身不保留拒绝边界.
- TypeScript: 接受宿主编译产物, 映射预编译快照并可请求 provider v3 按需编译; 磁盘上的 raw TypeScript 模块经 Node 自身的类型剥离加载, 直接派发的 raw TypeScript 入口仍返回 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM: Node.js 原生 loader + `module.registerHooks`; live binding, 循环依赖, `import.meta`, `data:` URL 与 `require(esm)` 与桌面一致.
- 能力: 同步脚本执行, bundle transport, 原生内嵌运行时, 宿主能力代理, host capability live bridge.
- 文件流、gzip/deflate/Brotli 压缩流和基础 VM 执行正式化; 显式启用的 Inspector (Debug/Release) 在 localhost 范围内正式支持.
- 嵌入执行不再输出 ExperimentalWarning 提示, 保留 warning 事件、普通警告、弃用警告和错误; Node 上游 API 稳定性与终端默认行为不变.
- 原生 Worker 支持嵌套, MessagePort API, stdio, argv, execArgv 与预加载继承; 删除插件自设的 Worker 数量和启动时限, 请求 resourceLimits 仅作可覆盖的默认值, WorkerPool 默认不限制排队数量或任务时长; Inspector 会话遵循执行请求与当前构建支持
- 主脚本与 Worker 可通过 Node 加载 Android native addon, process.dlopen 与 .node 扩展恢复原生行为, 不设插件自有签名或哈希白名单; native-addon 示例提供三 ABI 的 16 KB 对齐 Node-API 产物和离线 NDK/CMake 构建; 错 ABI 或损坏二进制返回 ERR_DLOPEN_FAILED 等 Node 错误, 设备端不随包提供 NDK 或 Python
- Inspector 在宿主显式允许后支持 Debug 和 Release 构建; inspector 与 inspector/promises 保留 Node 原生模块对象, 主脚本和 Worker 可使用 Session, 监听仅绑定 127.0.0.1 且不支持阻塞等待; 未开启时 url() 返回 undefined, 激活和 Session 创建保持关闭
- package_manager 的 install/update/remove 支持 npm 包规格, 在工作区调用随包 npm; npm.cli/exec/runScript/pack 使用真实命令行, registry/代理/生命周期选项遵循 npm 与项目 .npmrc, 输出流式回传并保留 npm 错误码; CLI 资产仅在首次调用时准备, 对象请求保留原宿主本地库契约

******

### 发行历史

******

# v1.32.2

###### 2026/10/30

* `修复` shell.exec 执行很快结束的命令时偶尔以 ERR_STREAM_DESTROYED 错误失败的问题

# v1.32.1

###### 2026/10/30

* `修复` rhino.install() 装入的 Packages / java / android 代理在读取静态字段, 调用 describe(), 调用静态方法或用 new 创建对象时报错的问题

# v1.32.0

###### 2026/10/29

* `新增` Node.js/Bun/Worker 的 ai.agent 任务控制与事件, 注册脚本上下文和结果回传, 以及打包应用的独立授权
* `新增` Node.js 与 Worker 的 util, species, s13n, fmt/cvt 和 jsox 工具模块及全局数据函数, 与 AutoJs6 脚本一致, 以 autojs6:util 等模块名引入
* `修复` chooseFiles() 之后立即调用 web.cancelFileChooser() 时文件选择器可能没有关闭的问题
* `优化` jsox 与字节单位换算的结果和报错改为与 AutoJs6 脚本一致, jsox.extend/extendAll 直接扩展当前线程的 Math, Array 和 Number

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
