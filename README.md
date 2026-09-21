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

# v1.5.6

###### 2026/09/19

* `新增` Node.js 致命错误支持生成诊断报告, 失败结果及 getRuntimeInfo 可读取崩溃原因, 堆用量, 调用栈和报告路径, maxOldGenerationSizeMb 选项支持设置单次执行的 V8 老生代堆上限
* `修复` require 加载大型 CommonJS 模块时因扫描过程占用过多堆内存而崩溃的问题
* `修复` AGP 9.1 构建时的 SDK XML v4 解析警告, 以及 JVM 单元测试误触发 APK 原生库对齐检查的问题 (共享构建插件 1.8.3)

# v1.5.5

###### 2026/09/18

* `修复` runtimeInfo 中能力目录版本及摘要过期的问题, 改为从运行时资源读取
* `修复` Worker 的 eval 模式将代码字符串误当作文件路径的问题
* `优化` 能力目录按实际运行时更新, TypeScript 宿主编译标为稳定, 移除已废弃的类型剥离及未实现的执行模式
* `优化` autojs6:profile 诊断信息按实际能力更新, 明确文件权限, process, Worker, 进程池, WASI 及原生扩展的支持范围
* `优化` 完善 ESM, 动态导入, Wasm 及桌面兼容性示例, 对照测试改为实际执行, 移除仅打印元数据的依赖安装示例 (编译缓存示例标为不适用)
* `优化` require/import 及 Worker 支持绝对路径, 上级目录及 file: URL, 文件访问遵循 Android 权限 (node_modules 从工作区查找, /proc, /sys, /dev 仍不可访问)
* `优化` Worker 消息及文件监听遵循 Node.js 原生限制, 移除额外的消息大小, 排队数量, 监听器及事件频率限制
* `优化` Worker 中的 process.exit 仅结束当前线程, process.getBuiltinModule 按 Worker 模块白名单开放
* `优化` Worker 支持通过工作区 node_modules 解析包名, 包含 exports/main/index 及包自引用
* `优化` Worker 支持动态 import, 包含相对或绝对路径, file: URL, 工作区包, 允许的内置模块及 JSON 导入属性
* `优化` 实体按键及通知访问示例完成真机验收, host-events 示例标为稳定
* `优化` scheduled-node-task 示例支持显示生命周期策略及保留定时任务, 补充 scheduled 执行模式测试
* `优化` scheduled 执行模式完成宿主 WorkManager 验证, 能力目录标为可用
* `优化` media.play 支持返回可控制的播放会话, media_store 支持受权限约束的媒体库读写, 兼容层补充 playMusic 等 Rhino 别名 (需要配套宿主及相应媒体能力授权)
* `优化` 媒体播放及媒体库示例完成宿主集成验证并标为稳定, 发布 1.5.5 能力目录快照供宿主核对
* `优化` fs 的流选项, 递归监听, 复制过滤, glob, readableWebStream 及文件类型构造器遵循 Node.js 24 原生行为, 移除额外选项拦截
* `优化` 递归 readdir/opendir 移除 4096 条目上限, readlink/chmod/chown/utimes 支持绝对路径, 普通文件错误保留 Node.js 错误码 (仍执行路径安全检查)
* `优化` 移除运行时自设的模块大小及数量限制, CommonJS 入口路径及 fs.mkdtemp 的路径和编码行为与 Node.js 保持一致
* `优化` node:sqlite 通过原生授权回调校验 ATTACH/VACUUM INTO 的路径, 支持 file: URI 及空临时库, 保留 /proc, /sys, /dev 边界限制 (仍拒绝通过绑定参数或表达式指定 ATTACH 文件名)
* `优化` 宿主桥接调用超出并发窗口时按顺序排队, 图像句柄及网络连接配额统一由宿主控制, 移除运行时重复限制及对应策略字段
* `优化` 桥接 fetch/WebSocket/axios 的超时, 大小及队列限制统一由宿主控制, 运行时最多跟随 20 次重定向, policy 改用 limitsEnforcedBy 描述限制来源
* `优化` opendir 返回原生惰性 fs.Dir, 保留调用者的路径写法, rm/rmdir 移除额外根目录限制并遵循 Node.js 与 Android 行为 (仍检查 NUL 字节及路径越界)
* `优化` 模块传输预算采用宿主公布的限制, fetch 响应体支持文件描述符传输, 超大 Binder 回复立即返回 ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED (需要配套宿主)
* `优化` fetch 请求正文及 WebSocket 消息超过 256 KiB 时支持文件描述符传输, 大小由宿主策略限制 (需要宿主声明 bridgeRequestBinaryTransport=pfd)
* `优化` 桥接 fetch 的 Response.url 返回最终响应地址, 避免始终为空字符串
* `优化` Java 互操作统一采用宿主提供的类及成员白名单, 支持对象句柄, getStatic/describe 及成员异常回传
* `优化` npm 同名包优先规则仅用于 axios/colors/mime/nanoid/opencc/undici 等替代模块, 保留 AutoJs6 专用模块, 运行时模块裸名支持 ESM 导入, Packages 代理支持 getStatic/describe
* `优化` 运行时进程异常退出时回报系统记录的低内存或原生崩溃原因, 结果附带 slotExit, getRuntimeInfo 提供 lastSlotExit (需要 Android 11+, 低版本提示原因不可用)

# v1.5.4

###### 2026/09/17

* `修复` 嵌入执行输出多余 ExperimentalWarning 提示的问题, 保留警告事件, 普通警告, 弃用警告及错误 (不改变终端行为)
* `优化` 文件流, gzip/deflate/Brotli 压缩流及基础 VM 执行转为正式支持, Debug Inspector 支持显式启用且仅监听 localhost

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
