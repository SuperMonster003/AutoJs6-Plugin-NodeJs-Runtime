# Node 脚本可用的宿主 API 清单 (M3.1)

> 盘点日期: 2026-08-18。基于插件 `node_bridge_sources.cpp` 的 require 拦截链与宿主 `NodeBridgeProtocol.kt` 的 dispatch 路由逐条核对。
>
> 用法: Node 脚本内 `const toast = require("toast")` 即可, 无需 import 前缀; 也可以 `require("autojs6:bridge").callAutoJs({ module, method, args })` 直接发底层调用。

## 状态说明

| 状态 | 含义 |
|---|---|
| **bridged** | 经 live bridge 到宿主真实实现, 默认可用 |
| **bridged-partial** | 模块可用但部分方法被宿主 fail-closed |
| **bridged-policy** | 能力已稳定启用, 但仍受宿主权限、声明或外部插件可用性约束 |
| **local-shim** | 插件内纯 JS 实现, 不出进程 |
| **denied** | 结构性拒绝 |

## 一. 已桥接 — 默认可用

| 模块 | 方法 | 备注 |
|---|---|---|
| `toast` | showToast, toast | 真 toast |
| `app` | launchPackage, launchApp, openAppSetting, startActivity, getPackageName, getAppName, isInstalled, viewFile, editFile | 真启动 |
| `accessibility` | isEnabled, ensureEnabled, click, back, home, recentApps, findByText, clickText, findOne, findAll, longClick, setText, scrollForward, scrollBackward, **swipe, gesture** (M3.2) | 真无障碍; 有每秒限速; 服务未启用时返回 capabilityProviderMissing。swipe/gesture 需显式声明 `accessibility.gesture` 权限 (不被 `accessibility` 隐含), 时长上限 10s |
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

## 三. 稳定能力与外部依赖 (bridged-policy)

| 模块 | 门禁 |
|---|---|
| `fetch` / `axios` / `undici` | 默认启用; 仍要求项目声明 `network` 能力并满足宿主网络策略。raw Node http/https 同样默认可用; 此桥接走宿主 OkHttp |
| `websocket` | 同上 |
| `ocr` | 需 ML Kit OCR 外部插件 |
| `barcode` | 需 Barcode 外部插件 |
| `java` | 默认启用; 仅允许宿主白名单中的类、构造器、方法和字段, 反射/ClassLoader/进程与原生库加载继续拒绝 |
| `rhino` | 默认提供显式迁移入口; 执行请求使用 `explicit: true`, 不会自动安装旧 Rhino 全局对象 |
| `worker_threads` | 默认启用; Worker 不能访问 AutoJs 桥、Android 对象或越权文件/网络资源, 并受数量、内存、超时和清理预算约束 |
| `child_process` | 默认启用并遵循 Node 原生语义; 受 Android UID/SELinux/清单与调用方校验约束，但不继承独立 `shell` 桥的私有可执行文件白名单或资源预算，脚本必须自行校验命令、限制 stdio/超时并回收子进程 |

## 四. 插件内本地实现 (local-shim, 不出进程)

`files` (scoped fs 封装), `base64`, `colors`, `formatter`/`fmt`, `converter`/`cvt`, `s13n`, `mime`, `nanoid`, `opencc`, `pinyin`, `pinyin4j`, `jsox` (+.mathx/.arrayx/.numberx), `plugins` (本地 ./plugins 目录扫描)。

注意 (M2.4 语义): 工作区 node_modules 里安装了同名 npm 包时, **npm 包优先于 shim** (桌面 Node 语义); 未安装时 shim 照常回答。

## 五. 明确不可用 (denied)

| 项 | 原因 |
|---|---|
| `media_projection` (requestScreenCapture/nextImage/stop) | 宿主侧 UNAVAILABLE |
| accessibility 的 powerDialog / waitFor / rawNode | 宿主 blockedMethods (swipe/gesture 已于 M3.2 放开, 见第一节) |
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
| `accessibility.swipe` 派发链 + `accessibility.gesture` 能力门禁 | 宿主 `pluginRuntimeSwipesThroughAccessibilityGestureCapability` (服务未开时断言可读 capabilityProviderMissing; 未声明能力被插件本地拒绝且零派发) | ✅ M3.2 |
| `accessibility.*` 动作在服务开启下的完成路径 | 无障碍开启依赖手工授权 (小米 adb 不可直写 secure settings), 待手工开启后跑同一用例补证 | ⚠️ 部分验证 |
| 其余 bridged 模块 | 无真机断言 | ⚠️ 未验证 |

## 七. 与 Node 原生重叠的能力: 选择指引 (M3.3)

原则: **能用 Node 原生就用 Node 原生**。桥接调用要跨进程 (文件轮询传输, 单次 ~10ms 级), Node 原生在 V8 里直接执行; 且原生 API 与桌面 Node 教程/npm 生态零差异。不为重叠能力重复造桥。

| 需求 | 用这个 (Node 原生) | 不要用 | 备注 |
|---|---|---|---|
| 读写文件 | `fs` / `fs/promises` (作用域限定在工作目录) | `files` shim | shim 本身就是 fs 封装, 直接用 fs 少一层 |
| HTTP/HTTPS 客户端 | `http` / `https` (M2.6 后默认可用), 或工作区安装的 npm 包 | 桥接 `fetch`/`axios`/`undici` | 桥接版走宿主 OkHttp, 仅在需要宿主网络栈 (代理/证书策略跟随宿主) 时使用 |
| HTTP 服务器 | `http.createServer` | — | 桥接版无服务器能力 |
| Base64 | `Buffer.from(x, "base64")` / `buf.toString("base64")` | `base64` shim | |
| MIME 判型 | npm `mime` 包 (M2.4 已实测) | `mime` shim | 装了 npm 包时它自动优先于 shim |
| 时间/格式化/工具函数 | npm (dayjs/lodash/ms 等, M2.4 已实测) | `formatter`/`converter` shim | |
| 定时任务 (进程内) | `setInterval`/`setTimeout` + 长驻脚本 (M2.5) | — | 跨进程/重启存活的调度才用 `work_manager` 桥 |
| 键值存储 | 小数据: 工作目录 JSON 文件 | — | 需跨脚本/跨工作区共享时用 `storages` 桥 (宿主级存储) |
| DNS | `dns` / `dns/promises` | — | |

仍然只能走桥的: 屏幕/无障碍/toast/剪贴板/传感器/通知/对话框/宿主 shell 等设备能力 (第一节), 以及需要"宿主身份"的场景 (宿主级 storage、WorkManager 调度、宿主网络栈)。

## 八. 与 M3.2 目标的对照

M3.2 原定 "补齐 toast / app.launch / click / swipe / text 查找 / 剪贴板 / shell" — **已全部达成**:

- toast, app.launch, click, text 查找, 剪贴板, shell 在盘点时即已桥接, M3.1 补了真机证据。
- swipe/gesture (原唯一缺口) 于 M3.2 落地: 宿主 `dispatchGesture` 派发 (swipe 四坐标+时长; gesture 自由路径) + 独立 `accessibility.gesture` 能力 (插件/宿主双侧均要求显式声明) + 插件 JS `accessibility.swipe/gesture` 方法。
