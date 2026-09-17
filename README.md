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
* `优化` TypeScript 宿主编译路由在能力目录中转为 stable, 删除已退役的 legacy stripping 元数据; 生命周期目录只保留可运行的执行模式, 移除 packaged_long_running 启动面与 node_sandboxed / worker_computation 保留名; 传输、准入与取消元数据按实际运行时对齐
* `优化` autojs6:profile 诊断对象按实际运行时描述, 不再沿用历史 partial / reserved / deferred 文案: Android 权限约束的文件访问、process 子集、worker 通道、进程池以及 WASI / native addon 决策

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
