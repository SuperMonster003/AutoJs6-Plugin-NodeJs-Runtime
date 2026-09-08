<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>用于 AutoJs6 的 Node.js 24.5.0 原生运行时插件</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
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

AutoJs6 Node.js Runtime 插件为 AutoJs6 提供内嵌 Node.js 24.5.0 原生运行时, 用于执行 Node.js 脚本和插件化运行时任务.

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

- 运行时槽位: `node24_5`.
- 插件 ID: `nodejs`, 引擎: `nodejs`.
- 运行时服务动作: `org.autojs.plugin.nodejs.RUNTIME`.
- 原生运行库: `libnode.so` 和 `libautojs6-node.so`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, 以及 `universal`.
- 文件系统: Android 权限允许范围内可访问设备路径; `/proc`、`/sys`、`/dev` 为硬边界.
- TypeScript: 接受宿主产物并可通过 provider v3 按需编译运行中创建的项目文件; direct raw TypeScript 返回 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, 原生 live binding 与循环依赖.
- 能力: 同步脚本执行, bundle transport, 原生内嵌运行时, 宿主能力代理, host capability live bridge.

******

### 发行历史

******

# v1.3.0

###### 尚未发布

* `新增` Node.js 桥订阅通过既有回调推送传感器, WebSocket, UI, 悬浮窗与输入事件, 提供 on/once/off 监听, 有界事件队列及 drainEvents 兼容
* `新增` Node.js 运行时支持可选 idleExitMs 空闲退出与后续脚本重新连接, 提供 idleForMs 诊断, 默认保持常驻
* `修复` 修复长驻脚本的桥会话持续累积请求与响应记录的问题, 清理已完成请求, 仅保留最近 32 条响应并限制诊断体积
* `优化` 实时桥默认使用 JNI/Binder 直通并经 Node 事件循环返回响应, 降低调用延迟, 保留可选文件回退与待处理调用上限

# v1.2.0

###### 2026/08/29

* `新增` `mediainfo` facade 新增 `capabilities()`, 且其可调用入口与 `read()` 支持显式选择插件快照 v1/v2; 省略 schema 时继续返回原有宿主 Node v1 快照
* `新增` 新增 module-source provider v3, 通过绑定精确字节数与 SHA-256 的有界 PFD 按需编译运行中创建的 `.ts/.mts/.cts`, 使用独立 30 s 编译预算, 稳定拒绝路径逃逸, 符号链接和歧义, 并保留宿主 TypeScript 诊断与 Source Map 栈映射
* `新增` 在 Android 应用权限范围内启用桌面式文件系统访问, 同时继续拒绝 `/proc`、`/sys`、`/dev`
* `新增` 支持 `accessibility.swipe` 与 `accessibility.gesture`, 并由独立能力 `accessibility.gesture` 门禁
* `新增` 支持覆盖排队与执行全过程的脚本超时, 超时返回 `ERR_AUTOJS6_SCRIPT_TIMEOUT`; 未指定超时时仍可无限运行
* `新增` 默认启用 `dgram` (UDP) 与 `http2`, 并对 `trace_events` 返回明确的禁用错误
* `新增` 支持 Debug 构建显式开启本地 `inspector` 调试, 仅监听 localhost 并通过 `adb forward` 连接
* `新增` 新增受宿主插件权限保护的无界面激活入口, 补齐插件中心说明并禁用应用数据备份
* `修复` raw TypeScript 在宿主未提供编译产物时改为 fail-closed, 补齐快照动态 import 映射并归一化生成/导入栈帧
* `修复` 以 V8 native linker 替换快照式 partial ESM adapter, 修复循环 re-export 中可变导出不会实时更新的问题
* `修复` 修复 AutoJs6 兼容 facade 的 ESM 导入与不存在的 TypeScript 后缀探测, 同时保持已安装 npm 包优先
* `优化` 删除基于 regex 的 legacy TypeScript 类型剥离 fallback 及其请求开关, raw `.ts/.mts/.cts` 现始终要求宿主编译产物
* `优化` 对齐宿主/插件 v2 合约, 能力清单与 plugin-only 运行时职责边界
* `优化` 将 Node.js 样例, TypeScript 类型声明, 项目向导, 运行时默认值及宿主对齐校验统一归属插件仓库, 移除宿主侧 Gradle 开关与重复开发资产
* `优化` 实测 npm 生态扩展到 15 包, 新增 axios、express 及 ESM-only 包 nanoid、p-limit、yocto-queue
* `优化` 以 `executionMode` 请求字段作为生命周期模式的权威来源, 将无实际作用的 `runtimeAdapter` 标记为废弃
* `优化` 移除十个仅打印固定状态的历史样例, 并在类型声明中标明截图、图像分析和录音尚未提供宿主 provider

# v1.1.0

###### 2026/08/18

* `新增` 支持 stdout/stderr 实时流式输出与基于 `node::Stop` 的协作式取消
* `新增` 以最多 3 个等待者的有界串行队列取代 BUSY 直接拒绝, 并支持常驻长运行脚本生命周期
* `新增` 默认启用 Node 原生网络内建模块、`worker_threads` 与 `child_process`, 并实测 10 个常用纯 JavaScript npm 包
* `优化` 支持无需工作区归档的 direct-run 与 v1..v2 模块源码 provider 宽容协商, 错误输出收敛为简明错误码与 JavaScript 栈

##### 更多发行历史可参阅

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.changelog/CHANGELOG-zh-Hans.md)

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
