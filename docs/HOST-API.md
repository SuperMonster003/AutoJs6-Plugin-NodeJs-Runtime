# Node 脚本可用的宿主 API 清单 (M3.1)

> 盘点日期: 2026-08-18。基于插件 `node_bridge_sources.cpp` 的 require 拦截链与宿主 `NodeBridgeProtocol.kt` 的 dispatch 路由逐条核对。
>
> 用法: Node 脚本内 `const toast = require("toast")` 即可, 无需 import 前缀; 也可以 `require("autojs6:bridge").callAutoJs({ module, method, args })` 直接发底层调用。

## 状态说明

| 状态 | 含义 |
|---|---|
| **bridged** | 经 live bridge 到宿主真实实现, 默认可用 |
| **bridged-partial** | 模块可用但部分方法被宿主 fail-closed |
| **bridged-gated** | 需实验开关或外部插件才可用 |
| **local-shim** | 插件内纯 JS 实现, 不出进程 |
| **denied** | 结构性拒绝 |

## 一. 已桥接 — 默认可用

| 模块 | 方法 | 备注 |
|---|---|---|
| `toast` | showToast, toast | 真 toast |
| `app` | launchPackage, launchApp, openAppSetting, startActivity, getPackageName, getAppName, isInstalled, viewFile, editFile | 真启动 |
| `accessibility` | isEnabled, ensureEnabled, click, back, home, recentApps, findByText, clickText, findOne, findAll, longClick, setText, scrollForward, scrollBackward | 真无障碍; 有每秒限速; 服务未启用时返回 capabilityProviderMissing。**swipe/gesture 不在此列** (见"缺口") |
| `clipboard` | getText, setText, hasText | 真剪贴板 |
| `device` | isScreenOn, wakeUp, vibrate, isIgnoringBatteryOptimizations, openBatteryOptimizationSettings | 硬件标识符 (imei/androidId/serial/mac) 明确 blocked |
| `shell` | exec | 真 ProcessBuilder; execRoot 需 `shell.root` 权限; execShizuku 拒绝 |
| `dialogs` | alert, confirm, input, select | 宿主对话框 |
| `engines` | myEngine, all, stopAll, stopSelf, execScript, execScriptFile | 宿主脚本引擎 |
| `storage` / `storages` | get, put, remove, clear, keys, contains | |
| `database` / `sqlite` | open, exec, run, get, all, transaction, close | 单线程 executor |
| `notifications` | notify, cancel, cancelAll, getPermissionStatus, openSettings | 受 POST_NOTIFICATIONS 权限门禁 |
| `sensors` | getAvailableSensors, once, subscribe, unsubscribe, drainEvents | 真 SensorManager |
| `ui` | showLayout, update, batchUpdate, close, drainEvents | Activity-owned 声明式 UI |
| `work_manager` | scheduleOnce, schedulePeriodic, cancel, list | WorkManager 真调度 |
| `autojs6:lifecycle` | checkpoint, readCheckpoint, clearCheckpoint | 注意: 宿主 getBrokerInfo 能力清单里不含它 (由 policy 层单独补), 以本清单为准 |

## 二. 部分可用 (bridged-partial)

| 模块 | live 方法 | blocked 方法 |
|---|---|---|
| `image` / `images` | readImage, recycle | captureScreen, saveImage, clip, resize, grayscale, threshold, findImage, matchTemplate, findColor, findMultiColors |
| `recorder` | getStatus | start, stop |
| `media` | getAudioStreamVolume/MaxVolume/Info (只读) | — |
| `mediainfo` | read, get (作用域内相对路径) | — |
| `ui.overlay` | show, update, drainEvents, close, closeAll, hasPermission, openPermissionSettings | 需 SYSTEM_ALERT_WINDOW; POC 级 |
| `package_manager` (`npm` 为其别名) | list, verify, prune, planInstall/Update/Remove, install, update, remove (app 私有本地库) | npm CLI / registry 下载 / 生命周期脚本 |
| `input_observer` | observeKeys, drainEvents, close, getAvailableSources (fake 源) | accessibility 实源在 scheduled/background 启动面拒绝 |

## 三. 需开关或插件 (bridged-gated)

| 模块 | 门禁 |
|---|---|
| `fetch` / `axios` / `undici` | 网络实验开关 (M2.6 后 raw Node http/https 默认可用, 优先用 Node 原生; 此桥接走宿主 OkHttp) |
| `websocket` | 同上 |
| `ocr` | 需 ML Kit OCR 外部插件 |
| `barcode` | 需 Barcode 外部插件 |
| `java` | 需 javaInterop 实验开关, 未开启 require 即抛错 |
| `rhino` | 需请求带 `experimental: true` |
| `worker_threads` / `child_process` | 需各自实验开关 |

## 四. 插件内本地实现 (local-shim, 不出进程)

`files` (scoped fs 封装), `base64`, `colors`, `formatter`/`fmt`, `converter`/`cvt`, `s13n`, `mime`, `nanoid`, `opencc`, `pinyin`, `pinyin4j`, `jsox` (+.mathx/.arrayx/.numberx), `plugins` (本地 ./plugins 目录扫描)。

注意 (M2.4 语义): 工作区 node_modules 里安装了同名 npm 包时, **npm 包优先于 shim** (桌面 Node 语义); 未安装时 shim 照常回答。

## 五. 明确不可用 (denied)

| 项 | 原因 |
|---|---|
| `media_projection` (requestScreenCapture/nextImage/stop) | 宿主侧 UNAVAILABLE |
| accessibility 的 swipe / gesture / powerDialog / waitFor / rawNode | 宿主 blockedMethods; gesture 需独立 capability, 尚未提供 |
| `image.captureScreen` 及全部图像分析方法 | 同 media_projection 链路 |
| 硬件标识符 (imei 等) | 隐私 fail-closed |
| 非白名单 Node builtin | `ERR_AUTOJS6_BUILTIN_DISABLED` |

## 六. 真机验证矩阵

"已桥接" ≠ "已真机验证"。当前有真机断言证据的:

| 路径 | 用例 | 状态 |
|---|---|---|
| `device.isScreenOn` | 宿主 `pluginRuntimeUsesLiveHostBridgeForDeviceCall` | ✅ M1.1 |
| `toast` + `clipboard` + `storage` + `shell.exec` + `app.getAppName` | 宿主 `pluginRuntimeDrivesCommonAutomationApisThroughLiveBridge` | ✅ M3.1 |
| 加密模块 provider (v1) | 宿主 `pluginRuntimeDecryptsEncryptedModuleThroughHostV1Provider` | ✅ M1.3 |
| `accessibility.*` | 样例 `accessibility-click-text` 的 SKIPPED 分支使其不能作证据; 无障碍开启依赖手工授权, 待 M3.2 补真机证据 | ⚠️ 未验证 |
| 其余 bridged 模块 | 无真机断言 | ⚠️ 未验证 |

## 七. 与 M3.2 目标的对照

M3.2 原定 "补齐 toast / app.launch / click / swipe / text 查找 / 剪贴板 / shell":

- toast, app.launch, click (accessibility.click/clickText), text 查找 (findByText/findOne/findAll), 剪贴板, shell — **均已桥接**, 缺的是真机验证 (本清单第六节)。
- **swipe 是唯一真缺口**: 在宿主 blockedMethods 里, 且 gesture 被声明为需要独立 capability。M3.2 的实际工作 = 宿主端补 swipe/gesture 派发 + 插件端无需改动。
