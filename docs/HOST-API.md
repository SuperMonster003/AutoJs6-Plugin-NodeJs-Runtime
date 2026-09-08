# Node 脚本可用的宿主 API 清单 (M3.1)

> 盘点日期: 2026-08-29。基于插件 `node_bridge_sources.cpp` 的 require 拦截链与宿主 `NodeBridgeProtocol.kt` 的 dispatch 路由逐条核对。
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
| `mediainfo` | read, get, capabilities (作用域内相对路径; 缺省保持 Node v1, 插件明确宣告后可显式请求插件 v1/v2) | — |
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

`files` (Node fs 便捷封装), `base64`, `colors`, `formatter`/`fmt`, `converter`/`cvt`, `s13n`, `mime`, `nanoid`, `opencc`, `pinyin`, `pinyin4j`, `jsox` (+.mathx/.arrayx/.numberx), `plugins` (本地 ./plugins 目录扫描)。

注意 (M2.4 语义): 工作区 node_modules 里安装了同名 npm 包时, **npm 包优先于 shim** (桌面 Node 语义); 未安装时 shim 照常回答。

## 五. fs 访问模式

当前 1.2.0 构建以 `AUTOJS6_NODE_UNRESTRICTED_FS_ACCESS=1` 编译, Node `fs` / `fs/promises` 采用**桌面式可达范围**:

- `workingDirectory` / `sandboxRoot` 继续约束入口、`require()`、`node_modules`、本地 ESM 图与 `./plugins` 扫描, 但**不再作为 fs 授权根**。绝对路径与工作区外路径可以交给 Android 正常判权。
- 插件 APK 使用独立 applicationId/UID, 不继承 AutoJs6 宿主的存储授权。Android 11+ 访问共享存储前, 用户需在系统设置为本插件授予“所有文件访问” (`MANAGE_EXTERNAL_STORAGE`); 未授权时 Node 返回 `EACCES` 属预期行为。
- Android UID、清单权限、SELinux 与文件自身权限仍是实际边界。插件不能借此读取其他应用的私有目录。
- `/proc`、`/sys`、`/dev` 是运行时额外保留的硬拒绝边界, 即使 Android 授权也不会放开。
- 项目清单中的 `filesystemRoots` 保留为兼容性诊断元数据 (`metadataOnly=true`, `grantsAuthority=false`), 不会授予、扩大或收窄 fs 权限。

因此“沙盒根”与“文件系统可达根”是两个概念。需要可移植脚本时仍建议优先使用工作目录相对路径; 需要共享存储时再显式使用设备路径并处理 `EACCES`。

## 六. TypeScript 编译要求

插件运行时负责**执行 JavaScript 编译产物**, 不内置 `tsc`、SWC 或其他完整 TypeScript 编译器:

- raw `.ts` / `.mts` / `.cts` **直接派给本插件**仍会 fail-closed, 返回 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`。当前 AutoJs6 宿主源码已接通独立 TypeScript Compiler 0.6.0+ 的完整 `Program` 编译链: 宿主先编译项目快照, 再把 JavaScript 产物派给本插件; 用户从宿主启动 `.ts` 项目时无需自行生成中间文件。
- 运行时不再提供正则剥离回退或兼容请求开关。raw `.ts` / `.mts` / `.cts` 无论语法是否可擦除都返回同一个 compiler-required 错误; 只有宿主编译器产出的 JavaScript 快照进入执行链。
- 已编译项目快照可通过 `typeScriptPrecompiledSnapshot` 与 `typeScriptPrecompiledSourceNames` 标记; 运行时先把 `.ts`/`.mts`/`.cts` 静态或动态说明符映射到请求中已存在的 JavaScript 模块。若 compiler-backed Node 项目在运行期间新建精确普通文件, module-source provider v3 可使用 `compile_missing_typescript` 把 no-follow 读取的源码以 PFD + byte count + SHA-256 交回宿主编译, 成功状态为 `compiled_typescript`。v1/v2 provider 仍保持 snapshot not-found。
- v3 只接受项目私有 workspace 内非声明形式的 lowercase `.ts/.mts/.cts`; 路径逃逸、符号链接、identity 变化、大小写/extensionless 歧义、Host 项目或生成目标碰撞都会 fail closed。单个 Host 编译输入最终受 8 MiB 上限约束; ordinary provider 操作仍最多 5 s, v3 compile transport 独立最多 30 s。
- 编译器 source map 与宿主控制台中的 `.ts` 原始栈回映由宿主编译链负责。当前链路已覆盖入口、导入模块、dynamic import 与 v3 运行时源码; 插件负责归一化生成代码和导入 CommonJS 的栈位置, 但不会凭空生成 source map。动态类型错误使用 `ERR_AUTOJS6_TYPESCRIPT_COMPILATION_FAILED`, 并保留文件名与 TypeScript diagnostic。

普通 `.js` / `.cjs` / `.mjs` 与 npm 纯 JavaScript 包不受此限制。

## 七. 执行总预算与取消

`runScript` 请求中的 `timeoutMs` 采用**等待 + 执行的 wall-clock 总预算**:

- 正数预算从插件 Binder 入口开始计时, 包含契约检查、串行队列等待、工作区/模块准备和原生 Node 执行。队列阶段耗尽同样返回脚本超时, 不再伪装成 `BUSY`。
- 超时结果固定为 `timedOut=true`、`errorCode=ERR_AUTOJS6_SCRIPT_TIMEOUT`, 并回显 `timeoutMs`。诊断 payload 记录超时发生在 `queue_wait`、`pre_*` 或 `execution` 阶段。
- 执行阶段超时复用协作取消链: watchdog 先请求 `node::Stop`; 若 3 秒内仍未释放唯一执行槽, 再关闭队列并重启独立 runtime 进程。能协作停止时进程保持不变, 下一脚本可复用同一 PID。
- 请求缺少 `timeoutMs` 或传 `<=0` 时, **脚本执行本身不设时限**, 保持长驻服务语义; 无显式预算的队列等待仍有插件内部 10 分钟安全上限。宿主或项目配置若主动填入正数, 该值就是总预算; 有意长驻的调用应传 `0`。
- AutoJs6 宿主另保留 `timeoutMs + 5s` 的 Binder 响应兜底; 插件异常未返回时, 宿主会先发 `cancelScript` 再向调用方返回同一规范化超时码。

M8.1 设备证据覆盖 API 28/36/37 模拟器与 3 台真机: `while(true)` 在 3 秒预算后均得到规范化超时, 且后继脚本 6/6 复用同一 PID; 队列预算、无 timeout 长驻与人工取消也均通过。

`executionMode` 是 lifecycle config 的请求级权威来源。缺失时仅为兼容旧 contract-v2 调用方而回退宿主 `engine-info`, 两处都缺失则为 `one_shot`; 不带 `engine-info` 的显式 `interactive_long_running` 请求按 `interactive_session` 生成配置并开启 checkpoint 门。checkpoint 只用于脚本主动保存/读取 JSON 进度, `automaticRestart=false` 与 `restartPolicy=never` 不变; 真正的宿主 lifecycle bridge 仍会独立校验 execution mode 与 launch surface。

`runtimeAdapter` 是保留字面值的 deprecated no-op 键: 运行时槽位由已绑定插件服务的 runtime info 决定, 单次请求不能覆盖。当前 AutoJs6 宿主不再建模或发送该键; 旧调用方继续发送时会被宽容忽略。native payload 中的 `embedded_script.runtime_adapter.*` 是插件内部 C++ adapter 诊断, 与这个废弃请求键无关。

人工取消或执行超时若遇到同步原生调用无法响应 `node::Stop`, 插件会在 3 秒宽限后只杀死独立 runtime 进程。由于被杀的 Binder 事务不可能返回结果 Bundle, 直接 AIDL 调用方看到 transport loss; AutoJs6 宿主将已派发后的丢失规范化为 `ERR_AUTOJS6_NODE_PLUGIN_EXECUTION_LOST` 且禁止自动回退重放, 随后的新会话可拉起新 PID 继续服务。

### 长驻桥会话诊断

`embedded_script.bridge_live_responses_json` 是最近 32 条响应的诊断摘要, 不再保存整段会话的所有响应。每条诊断最多 1536 UTF-8 字节; 大响应的诊断副本替换为带 `diagnosticTruncated=true` 与 `responseCharacters` 的记录, 实际交付给脚本的响应保持完整。响应数组最多 49185 字节, 调用总计数与失败计数继续累计。

请求文件完成删除后立即释放内存中的去重记录。`embedded_script.bridge_live_retained_request_count` 与 `embedded_script.bridge_live_retained_response_count` 报告当前保留数量, 用于区分仍待处理的请求和有界的历史响应。

### Debug-only 本地 inspector

`inspectorEnabled` 是默认 false 的请求级实验键, 只有 **debug 宿主 + debug 插件 + 显式 true** 同时成立才有效。插件 Java 层与 native 编译产物各自复核构建类型; release 产物即使收到伪造请求也保留 `kNoCreateInspector` 并拒绝 `require("inspector")`。

启用后只暴露受控 `inspector` facade: `Session`、`console`、`open`、`close`、`url`; `open` 强制绑定 `127.0.0.1`, 拒绝远程地址与 `wait=true`, `NODE_OPTIONS=--inspect*` 仍被启动环境策略过滤。桌面调试需由用户取得 `inspector.url()` 的设备端口, 手动执行 `adb forward tcp:9229 tcp:<device-port>`, 再在 `chrome://inspect` 配置 `localhost:9229`。自动化证据已覆盖 WebSocket 升级、`Debugger.enable`、断点暂停/恢复、CPU Profiler 与 heap usage Session。

这是本地开发工具而非安全隔离面: CDP 可读取源码路径、运行时值、CPU profile 与 heap 数据, 当前**不做敏感路径或值的脱敏**。因此 release、项目元数据、打包 APK 与远程监听均不可开启; 完整 heap snapshot 的有界 app-private 落盘、清理和显式导出尚未实现。

## 八. 明确不可用 (denied)

| 项 | 原因 |
|---|---|
| `media_projection` (requestScreenCapture/nextImage/stop) | 宿主侧 UNAVAILABLE |
| accessibility 的 powerDialog / waitFor / rawNode | 宿主 blockedMethods (swipe/gesture 已于 M3.2 放开, 见第一节) |
| `image.captureScreen` 及全部图像分析方法 | 同 media_projection 链路 |
| 硬件标识符 (imei 等) | 隐私 fail-closed |
| 非白名单 Node builtin | `ERR_AUTOJS6_BUILTIN_DISABLED` |

## 九. 真机验证矩阵

"已桥接" ≠ "已真机验证"。当前有真机断言证据的:

| 路径 | 用例 | 状态 |
|---|---|---|
| `device.isScreenOn` | 宿主 `pluginRuntimeUsesLiveHostBridgeForDeviceCall` | ✅ M1.1 |
| `toast` + `clipboard` + `storage` + `shell.exec` + `app.getAppName` | 宿主 `pluginRuntimeDrivesCommonAutomationApisThroughLiveBridge` | ✅ M3.1 |
| 加密模块 provider (v1) | 宿主 `pluginRuntimeDecryptsEncryptedModuleThroughHostV1Provider` | ✅ M1.3 |
| TypeScript provider (v3) | 宿主 `compilesRuntimeCreatedTypeScriptAndKeepsSnapshotErrorsStable`、`runtimeCreatedTypeScriptTypeErrorsReturnReadableDiagnostics`、`mapsRuntimeCreatedTypeScriptFailureBackToDynamicSource` | ✅ T5-1, API 35 Xiaomi 23046RP50C |
| `accessibility.swipe` 派发链 + `accessibility.gesture` 能力门禁 | 宿主 `pluginRuntimeSwipesThroughAccessibilityGestureCapability` (服务未开时断言可读 capabilityProviderMissing; 未声明能力被插件本地拒绝且零派发) | ✅ M3.2 |
| `accessibility.*` 动作在服务开启下的完成路径 | 无障碍开启依赖手工授权 (小米 adb 不可直写 secure settings), 待手工开启后跑同一用例补证 | ⚠️ 部分验证 |
| 其余 bridged 模块 | 无真机断言 | ⚠️ 未验证 |

## 十. 与 Node 原生重叠的能力: 选择指引 (M3.3)

原则: **能用 Node 原生就用 Node 原生**。桥接调用要跨进程 (文件轮询传输, 单次 ~10ms 级), Node 原生在 V8 里直接执行; 且原生 API 与桌面 Node 教程/npm 生态零差异。不为重叠能力重复造桥。

| 需求 | 用这个 (Node 原生) | 不要用 | 备注 |
|---|---|---|---|
| 读写文件 | `fs` / `fs/promises` (Android 权限范围; `/proc`/`/sys`/`/dev` 拒绝) | `files` shim | shim 本身就是 fs 封装, 直接用 fs 少一层; 共享存储授权见第五节 |
| HTTP/HTTPS 客户端 | `http` / `https` (M2.6 后默认可用), 或工作区安装的 npm 包 | 桥接 `fetch`/`axios`/`undici` | 桥接版走宿主 OkHttp, 仅在需要宿主网络栈 (代理/证书策略跟随宿主) 时使用 |
| HTTP 服务器 | `http.createServer` | — | 桥接版无服务器能力 |
| Base64 | `Buffer.from(x, "base64")` / `buf.toString("base64")` | `base64` shim | |
| MIME 判型 | npm `mime` 包 (M2.4 已实测) | `mime` shim | 装了 npm 包时它自动优先于 shim |
| 时间/格式化/工具函数 | npm (dayjs/lodash/ms 等, M2.4 已实测) | `formatter`/`converter` shim | |
| 定时任务 (进程内) | `setInterval`/`setTimeout` + 长驻脚本 (M2.5) | — | 跨进程/重启存活的调度才用 `work_manager` 桥 |
| 键值存储 | 小数据: 工作目录 JSON 文件 | — | 需跨脚本/跨工作区共享时用 `storages` 桥 (宿主级存储) |
| DNS | `dns` / `dns/promises` | — | |

仍然只能走桥的: 屏幕/无障碍/toast/剪贴板/传感器/通知/对话框/宿主 shell 等设备能力 (第一节), 以及需要"宿主身份"的场景 (宿主级 storage、WorkManager 调度、宿主网络栈)。

## 十一. 与 M3.2 目标的对照

M3.2 原定 "补齐 toast / app.launch / click / swipe / text 查找 / 剪贴板 / shell" — **已全部达成**:

- toast, app.launch, click, text 查找, 剪贴板, shell 在盘点时即已桥接, M3.1 补了真机证据。
- swipe/gesture (原唯一缺口) 于 M3.2 落地: 宿主 `dispatchGesture` 派发 (swipe 四坐标+时长; gesture 自由路径) + 独立 `accessibility.gesture` 能力 (插件/宿主双侧均要求显式声明) + 插件 JS `accessibility.swipe/gesture` 方法。

### 新能力登记顺序

桥能力采用双侧预检: 插件在脚本进入 Binder 前做快速拒绝, 宿主在真实派发前按自身权限和 provider 状态再次校验。两份预检服务于不同的进程边界, 不应合并为单份实现。

新增或修改能力时按以下顺序登记, 并在提交前运行宿主手动任务 `:app:verifyNodeCapabilityManifestAlignment`:

1. 插件 `node_bridge_sources.cpp`: 声明脚本侧所需能力并完成本地预检。
2. 插件 `NodeBridgePermissionManifest.java`: 加入插件已知能力清单。
3. 宿主 `NodeBridgePermissionManifest.kt`: 加入宿主能力常量、清单和方法映射。
4. 插件 capability catalog: 在新 catalog 版本的 `bridge.permissionCapabilities` 与相关 `bridge.operations` 中登记。

当前发布快照 `nodejs-capability-catalog/1.3.0` 已补齐 `accessibility.gesture`; 插件 manifest、宿主 manifest 与 catalog 均为 48 项, 守卫不再保留历史例外。
