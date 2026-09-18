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
- 默认启用 `dgram` (UDP) 与 `http2`, 并对 `trace_events` 返回明确的禁用错误
- 支持 Debug 构建显式开启本地 `inspector` 调试, 仅监听 localhost 并通过 `adb forward` 连接
- 支持 CommonJS/ESM 源码, 模块源码, 工作目录, 沙盒根目录, 环境变量, stdout/stderr 结果回传.
- ESM 入口与 dynamic `import()` 使用 V8 native linker, 支持循环依赖、可变导出与 re-export live binding; CommonJS `require(esm)` 保留同步互操作边界.
- 提供由插件自身 Android 权限约束的桌面式文件系统访问; `/proc`、`/sys`、`/dev` 始终由运行时拒绝.
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
- 文件系统: Android 权限允许范围内可访问设备路径; `/proc`、`/sys`、`/dev` 为硬边界.
- TypeScript: 接受宿主产物并可通过 provider v3 按需编译运行中创建的项目文件; direct raw TypeScript 返回 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, 原生 live binding 与循环依赖.
- 能力: 同步脚本执行, bundle transport, 原生内嵌运行时, 宿主能力代理, host capability live bridge.
- 文件流、gzip/deflate/Brotli 压缩流和基础 VM 执行正式化; 显式启用的 Debug Inspector 在 localhost 范围内正式支持.
- 嵌入执行不再输出 ExperimentalWarning 提示, 保留 warning 事件、普通警告、弃用警告和错误; Node 上游 API 稳定性与终端默认行为不变.

******

### 发行历史

******

# v1.5.5

###### 2026/09/17

* `修复` runtimeInfo 报告的能力目录版本与摘要改由 runtime kit 派生, 修正 1.5.4 中仍为 1.5.0 的旧值
* `修复` worker_threads 按 Node 语义支持 new Worker(code, { eval: true }), 不再把代码字符串当作脚本路径
* `优化` TypeScript 宿主编译路由在能力目录中转为 stable, 删除已退役的 legacy stripping 元数据; 生命周期目录只保留可运行的执行模式, 移除 packaged_long_running 启动面与 node_sandboxed / worker_computation 保留名; 传输、准入与取消元数据按实际运行时对齐
* `优化` autojs6:profile 诊断对象按实际运行时描述, 不再沿用历史 partial / reserved / deferred 文案: Android 权限约束的文件访问、process 子集、worker 通道、进程池以及 WASI / native addon 决策
* `优化` 样例清点: packaged-esm、packaged-dynamic-import、require-esm、wasm-basic、wasm-plugin 与 desktop-parity-suite 转为 stable; 两个 parity 套件改为真正逐条执行片段而非打印目录文案; compile-cache 标为不适用, 删除只打印元数据的 package-install 样例
* `优化` 模块加载与 Node 一致地交给 Android 文件访问: require、import 与 Worker 接受绝对路径、上级目录目标与 file: URL (/proc、/sys、/dev 仍拒绝); node_modules 查找仍锚定工作区
* `优化` Worker 消息与 fs 监视器按 Node 原生限制运行: 移除 64 KB 消息与 32 条排队上限, 以及 16 个监视器 / 每秒 64 事件配额; 仅受 Android 内存约束
* `优化` worker 内 process.exit() 按 Node 语义只结束该 worker 线程, process.getBuiltinModule() 走 worker builtin 名单, 不再整体禁用
* `优化` worker 内 bare 包名按 Node 语义经工作区 node_modules 解析 (exports 条件、main、index、包自引用), 不再一律拒绝
* `优化` worker 内动态 import() 经 worker 的 partial ESM 装载器执行 (相对/绝对路径、file: URL、工作区包、builtin 名单、with { type: "json" }), 不再拒绝
* `优化` host-events 样例在实体按键与通知访问人工验收通过后转为 stable
* `优化` scheduled-node-task 样例打印生命周期策略并可保留定时任务供 WorkManager 实跑; scheduled 执行模式补齐插件侧回归
* `优化` scheduled 执行模式在宿主 WorkManager 定时运行器经插件实跑通过后, 能力目录状态转为 available
* `优化` media.play() 返回经宿主脚本音乐服务播放的会话对象 (pause/resume/seekTo/stop/status), 新增 media_store 模块提供受限的 MediaStore capabilities/query/get/insert/update/delete/scanFile/exportFile, 分别由 media.playback / media.library / media.library.mutate 能力把关; autojs6:compat.media 补上 Rhino 风格的 playMusic 等别名; 均需配套宿主构建
* `优化` media-playback / media-library 样例经宿主媒体 provider 合并后的人工验收转为 stable; 能力目录快照 1.5.5 发布到 releases/nodejs-capability-catalog, 宿主对齐任务改为对照该快照
* `优化` fs 包装层移除自设的选项拦截: 流的 fs / 继承的 fd / flags 选项、watch({ recursive: true })、异步 cp filter (cpSync 保持 Node 的 ERR_INVALID_RETURN_VALUE)、绝对路径 / 父目录 / 字面 '!' glob 模式与 exclude 数组、readableWebStream 的 type / encoding 以及 Stats / Dirent / Dir 构造器均按 Node 24 原生语义处理; filesystemProfile.advancedApis.recursiveWatch 报告 native
* `优化` 递归 readdir / opendir 交给原生 (移除 4096 条目上限与逐条 realpath 校验; readdir('/') 与 Node 一样列出 proc/sys/dev 名称), readlink / chmod / chown / utimes 接受绝对路径且 chmod 跟随符号链接, fs 策略错误码收敛为 ERR_AUTOJS6_FS_NUL_BYTE / ERR_AUTOJS6_FS_PATH_ESCAPE (硬边界, 与 loader 一致) / ERR_AUTOJS6_FS_SCOPED_PATH, 普通 fs 失败只保留 Node 码
* `优化` 运行时移除自设的模块源预算 (单模块 16 MiB、总量 64 MiB、8192 个模块、provider 请求计数), CommonJS 入口的 __filename / require.main.filename / process.argv[1] 与 Node 一样为绝对路径且 require.main.id 为 '.', fs.mkdtemp* 交给原生: 返回调用者的前缀写法加后缀并支持所请求的编码, 前缀父目录为符号链接时不再拒绝
* `优化` node:sqlite 把 SQL 文件操作交给原生 SQLite: 字面量文件名的 ATTACH (含 file: URI) 改经 SQLite authorizer 按 /proc、/sys、/dev 边界校验而不再扫描 SQL 文本, VACUUM INTO 的目标经 SQLite 内部 ATTACH 接受同一边界校验, 目录 PRAGMA 不再拦截, file: URI 字符串与空临时库可以打开, setAuthorizer() 与边界检查复合 (仅绑定参数或表达式给出文件名的 ATTACH 仍被拒绝)
* `优化` 桥配额交给宿主: 超出 autojs6:bridge-limits 中 maxPendingBridgeCalls 窗口的调用改为按先进先出排队等待而不再以 ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT 失败, 运行时不再自行限制图像句柄数、受控 fetch 并发数与受控 WebSocket 连接数 (宿主 broker 继续执行其策略), require('fetch').policy.maxConcurrentRequests 与 require('websocket').policy.maxConnections 字段退役

# v1.5.4

###### 2026/09/17

* `修复` 嵌入执行不再输出 ExperimentalWarning 提示, 保留 warning 事件、普通警告、弃用警告和错误; Node 上游 API 稳定性与终端默认行为不变
* `优化` 文件流、gzip/deflate/Brotli 压缩流和基础 VM 执行正式化; 显式启用的 Debug Inspector 在 localhost 范围内正式支持

# v1.5.3

###### 2026/09/16

* `优化` Android 17 本地网络授权统一移至插件中心启用流程和插件设置, 不再提供启动器授权页面; 未获授权时保持关闭并静默跳过自动启动
* `优化` 适配 Android 17 (SDK 37), 提供插件独立的本地网络权限控制及恢复引导

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
