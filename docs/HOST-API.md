# Node 脚本可用的宿主 API 清单

> 盘点日期: 2026-10-09。Node builtin 使用原生加载器; 以下清单区分插件运行时模块与宿主 `NodeBridgeProtocol.kt` 的 dispatch 路由。
>
> 用法: Node 脚本内 `const toast = require("toast")` 即可, 无需 import 前缀; 也可以 `require("autojs6:bridge").callAutoJs({ module, method, args })` 直接发底层调用。

## 状态说明

| 状态 | 含义 |
|---|---|
| **bridged** | 经 live bridge 到宿主真实实现, 默认可用 |
| **bridged-host-provider** | 模块可用但部分方法被宿主 fail-closed |
| **bridged-policy** | 能力已稳定启用, 但仍受宿主权限、声明或外部插件可用性约束 |
| **local-shim** | 插件内纯 JS 实现, 不出进程 |
| **denied** | 结构性拒绝 |

## Node.js 内建模块

M23.2 (2026-10-07) 起, Node.js 24.21 的全部内建模块按 Node 自身的 `module.builtinModules` 清单直接返回原生导出 (含 `cluster`, `domain`, `repl`, `trace_events`, `wasi`, `constants`, `node:sea`, `node:sqlite`, `node:test` 等), 普通名称与 `node:` 前缀名称共享同一模块对象, `require` / `require.resolve` / `import` / `process.getBuiltinModule` / `module.isBuiltin` 与桌面 Node 一致 (`require.resolve("node:fs")` 返回 `"node:fs"`); 不再有 allowlist / denylist。仅 `console` 一个薄包装保留 (stdout/stderr 捕获); `module` 自 M23.3 起为原生模块 (`createRequire` / `registerHooks` / `enableCompileCache` / `findPackageJSON` 均为原生)。仍可被请求级门禁关闭的只有 `worker_threads`, `child_process`, `inspector` 的开启与 Session (需显式请求, Debug/Release 均支持, 见 M23.5) 与原生网络模块 (`rawNodeNetworkModulesEnabled=false` 时 `http` / `https` / `net` / `tls` / `dns` / `dgram` / `http2` 直接返回 `ERR_AUTOJS6_BUILTIN_DISABLED`, 原来仅在该模式可达的受限 facade 已删除)。异步 crypto、完整 EventEmitter/AsyncLocalStorage、流与序列化遵循对应 Node API, 不再受原子集包装器的方法或输入大小限制。`node:test` 与 `node:test/reporters` 使用原生实现; 普通脚本中的 `test()` 会由默认 spec reporter 输出到 stdout, 失败测试使执行退出码为 1, `node-test-project` 提供可运行样例。

M23.3 (2026-10-08) 起, 模块加载由 Node 自身的 CommonJS / ESM loader 承担: `require` / `require.resolve` / `require.cache` / `require.extensions` / `Module._resolveFilename` / `module.register` / `import()` / `import.meta` / `require(esm)` 全部为 Node 原生行为; 入口按 Node 主模块的方式运行 (`require.main === module`, `require.main.id === "."`, `__filename` / `process.argv[1]` 为 realpath, 在 Android 上即 `/data/data/...` 而非 `/data/user/0/...` 拼写, 栈帧仍按请求的 sourceURL 显示)。运行时只注册同步的 `module.registerHooks({ resolve, load })`: `autojs6:*` 运行时模块与兼容 shim 以 `autojs6:` scheme 提供 (在 `require.cache` 中以该 url 为键; 同名已安装 npm 包仍优先于 `axios` / `colors` / `mime` / `nanoid` / `opencc` / `undici` 这些 shim), 请求级 builtin 门禁在 resolve 阶段判定, 请求随附的模块源码、module-source provider 物化的源码、TypeScript 快照与按需编译产物以及入口的内联源码由 load 钩子提供, 其余一律交由 Node。不再有 `vm.SourceTextModule` 自管 linker、自定义 resolver、动态 `import()` 改写与 `eval` / `Function` 守卫; `esmEnabled` / `dynamicImportEnabled` 请求键不再起作用 (始终为原生)。ESM 中对运行时模块的具名导入 (`import { esmLoaderProfile } from "autojs6:profile"`) 按模块对象的自有可枚举键提供。

文件中的密钥既可先用 `node:fs` 读取字节再交给 `crypto.createPrivateKey` 或签名 API, 也可直接传入 `file:` URL (WHATWG `URL` 对象): Node 24.21 的 OpenSSL STORE 密钥加载器自 1.6.0 (M23.1) 起在 Android 构建中与桌面版一致, 此前返回 `ERR_ACCESS_DENIED` 的禁用补丁已移除, 可达性同样只由 Android 权限决定。PEM/DER/JWK/KeyObject 等密钥输入继续使用 Node 原生实现。

`os` 自 M23.2 起为原生模块 (`require("os") === process.getBuiltinModule("os")`): 运行时在引导时把执行环境变量 `HOME` / `TMPDIR` 设为工作目录 (请求提供的 env 优先), `os.homedir()` 经 libuv 读取 `HOME`; `os.tmpdir()` 在 Node 中经 `safeGetenv` 读取, 而 Android 应用进程带 AT_SECURE 标志, Node 会拒绝读取并回答不存在的 `/tmp`, 因此运行时在检测到这种情况时把 Node 自己模块对象上的 `tmpdir` 绑定为 `TMPDIR` (不是 Proxy, 模块身份不变); `os.userInfo()` 返回真实的 Android 应用用户。`process` 仅保留 `exit` 的生命周期映射 (槽进程跨执行复用), `dlopen` / `abort` / `kill` / `umask` / `binding` / `_linkedBinding` / `report` / `features` 及 POSIX 身份调用均为 Node 原生 (Android 决定它们能做什么; `process.abort` 会结束当前槽进程并走 M12.5 崩溃报告)。`fs` / `fs/promises` 自 M23.1 (2026-10-07) 起直接返回 Node 原生模块 (`require("fs") === process.getBuiltinModule("fs")`): 没有包装层, 没有 `/proc`、`/sys`、`/dev` 边界, 异步 API 经 libuv 线程池执行而非在 `setImmediate` 中同步完成; 执行开始时以原生 `process.chdir` 进入工作目录, `process.cwd()` / `process.chdir()` 为原生行为; `url.pathToFileURL` / `fileURLToPath` 均为原生。`process`、`module`、子进程、worker、网络和 inspector 的请求级策略见对应章节。

成功结果在 Node 事件循环完成、触发最终 `exit` 时生成, 因此原生异步工作及 `beforeExit` 中追加的任务均可完成, 最终 stdout/stderr 与 `process.exitCode` 会正确回传。

文件流、gzip/deflate/Brotli 压缩流和基础 VM 脚本/上下文能力已正式化; 显式启用的 Inspector 在 Debug/Release 的 localhost 范围内均可用。可运行项目见 `sample/nodejs/streams-compression` 和 `sample/nodejs/vm-context`。这里的 stable 表示插件对文档范围内能力的支持, 不改变 Node 上游 API 稳定性; VM Modules 仍属于上游实验 API。

嵌入执行使用 `--disable-warning=ExperimentalWarning`, 因此正常 ESM 运行不会再把内部 VM Modules 提示输出到 stderr。`process.on('warning')` 事件、普通警告、弃用警告、异常和显式 stderr 输出均保留。宿主终端直接启动的 `node` 保持上游默认行为, 可由用户显式传入同一参数。宿主 API 仍需要相应 provider 和 Android 权限。

`node:sqlite` 提供 Node 原生同步 SQLite 引擎, 支持 `DatabaseSync`、预编译语句、CRUD、事务、函数与备份, 主线程和 worker 均可使用。普通名 `sqlite` 继续表示宿主异步数据库桥。数据库路径、`ATTACH`、`VACUUM INTO`、PRAGMA 和 `backup` 遵循 Node/SQLite 原生行为, 文件访问由 Android 按插件 UID 裁决。原生 API 的稳定性级别跟随随包 Node 版本, 嵌入执行的上游提示输出遵循上面的统一策略。

SQL 里的文件操作完全交给原生 SQLite (M23.1, 2026-10-07; 取代 M20.2 第九批的 authorizer 边界): `node:sqlite` 直接返回 Node 原生模块, `ATTACH` (字面量、绑定参数或表达式给出的文件名)、`VACUUM INTO`、`PRAGMA temp_store_directory` / `data_store_directory`、只读打开与相对路径解析 (按进程工作目录) 均为 SQLite 自身行为, 路径可达性由 Android 决定, 系统路径上的失败是 SQLite 自己的 `ERR_SQLITE_ERROR` (如 procfs 文件 "file is not a database", `/sys` `/dev` 下无法创建); `ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED` 退役。`db.setAuthorizer()` 为 Node 原生实现 (不再有运行时边界回调, 对未打开连接的行为随 Node 上游); `enableLoadExtension` / `allowExtension` 按 Node 原生状态机 (构造时未开启 `allowExtension` 则 `ERR_INVALID_STATE`), 扩展本身的加载随 M23.4 原生扩展放开。

M23.4 起, 主线程和 Worker 的 `process.dlopen` / `Module._extensions[".node"]` 使用 Node 原生实现, Node Environment 不再设置 kNoNativeAddons. 通过 require 或 createRequire 加载磁盘上的 Android addon; 二进制不进入 JavaScript module-source provider, 扩展名省略时也保留 Node 对 .node 的查找. JS 插件可携带并 require 原生依赖, JS 插件本身的入口仍遵循 .js/.cjs 契约.

`sample/nodejs/native-addon` 提供三 ABI 的真实 Node-API 示例, 按 process.arch 选择文件; `tools/nodejs/addon` 提供源码与离线 NDK/CMake 构建命令. addon 及依赖须匹配 Android、运行进程 ABI 和设备页大小, 示例以 16 KB 对齐构建并链接随包 libnode.so. 随包 npm 含 node-gyp 的 JavaScript 脚本, 但插件不提供 NDK/Python 编译环境; addon 通常在桌面交叉编译或使用 Android 预编译分发. 直接 ESM import .node 的处理仍由 Node 决定, ESM 项目可使用 createRequire.

错 ABI、缺依赖、损坏二进制由 Node/Android 动态链接器报告 (例如 ERR_DLOPEN_FAILED), 不再返回 ERR_AUTOJS6_NATIVE_ADDON_DISABLED 或 ERR_AUTOJS6_PROCESS_API_DISABLED. 原生代码在运行时槽进程中执行, 崩溃走既有 M12.5 slotExit / 系统 tombstone 报告. 设备端 npm registry 安装见 M23.6 下方说明; 插件不在设备内提供编译工具链.

## MediaInfo 多流查询

`require('mediainfo')` 和 compat facade 的 `mediainfo` 均提供异步查询. `get(path, streamKind, parameter, options?)` 的 `options.streamNumber` 是从 0 开始的整数, 缺省为 0; `options.infoKind` 缺省为 `TEXT`, 支持 `NAME`, `TEXT`, `MEASURE`, `OPTIONS`, `NAME_TEXT`, `MEASURE_TEXT`, `INFO`, `HOWTO`, `DOMAIN`, 忽略大小写. 例如 `await mi.get('sample.mka', 'audio', 'SamplingRate', {streamNumber: 1, infoKind: 'MEASURE'})` 查询第 2 条音轨的采样率单位.

`await mi.countGet(path, streamKind)` 返回同类流数量, 类型不存在时为 0, 原生文件打开失败时为 -1. `capabilities()` 返回的 `streamNumber`, `streamCount` 和 `infoKinds` 表示索引查询, 流计数及详细字段能力. 非缺省索引或 infoKind 查询会先协商宿主能力; 不支持时抛出包含 `MEDIAINFO_QUERY_UNSUPPORTED` 的错误, 防止旧宿主忽略选项后返回第 1 条流. 负数, 小数, 数字字符串及未知 infoKind 均拒绝. 原有不带选项的 get 调用保持兼容.

报告的 `Complete name` 与 `get(path, 'general', 'CompleteName')` 使用宿主解析后的原始路径. 插件私有缓存路径只用于内部解析; snapshot 的 `fileName` (v1) 和 `file.name` (v2) 继续为显示文件名. `Info_Parameters` 尚未开放.

配套宿主接入提交为 AutoJs6 `b3b3b29dc`; 宿主与本插件均已整合至主分支, 并在 ARM64 16 KB 三星设备验证真实 MediaInfo 调用.

## 标准输入与宿主消息

`process.stdin` 是 Node 原生 `Readable`, 支持 `readline` / `readline/promises`、`data` 事件、编码、管道及异步迭代。读取输入会保持脚本存活; 暂停、关闭 readline、EOF 或取消执行会释放相应等待。`stdin.unref()` 可取消它对事件循环的保活, `ref()` 恢复。`host-input` 样例连续询问两行, 使用支持输入的新宿主时控制台自动显示输入框。

`require('autojs6:host')` 或 `import host from 'autojs6:host'` 返回每次执行独立的 EventEmitter。`host.on('message', value => ...)` 接收 JSON 值; `once/off/removeAllListeners` 遵循 Node 事件语义。消息监听器默认保持脚本存活, `host.unref()` 取消保活, `ref()` 恢复。此模块仅在主脚本使用; worker 可通过父线程的 MessagePort 转发。

宿主在 AIDL 末尾追加的 v3 事务 `postMessage(executionId, Bundle)` 投递消息, 原有四个事务号保持不变。`getRuntimeInfo().contractVersion` 继续为 2, 以兼容严格检查版本 2 的已发布宿主; 新字段 `maxContractVersion=3` 表示支持新增事务。新宿主只在该字段至少为 3 时调用 `postMessage`; 旧插件缺字段视为 2, 旧宿主可继续正常执行无需输入的脚本。脚本请求接受版本 2..3, 缺省仍为 2。

| Bundle 字段 | `kind=stdin` | `kind=message` |
|---|---|---|
| `data` | 文本字符串, 按 UTF-8 推入流; 调用方负责追加换行 | JSON 值编码成字符串, JS 监听器收到解析后的值 |
| `eof` | 可选 boolean, 默认 false; 在当前文本后结束流 | 忽略 |

每条 data 最多 64 KiB UTF-8。JNI 队列最多 32 条且总 JSON 编码最多 384 KiB; stdin 每次只接受一个待投递块, 遵循流的读取需求。`false` 表示执行不存在/已退出、类型或大小无效、没有相应读取者或队列已满, 调用方可在下一次读取需求后重试; `true` 表示已入队, 取消执行仍可能使尚未消费的消息丢弃, 不表示脚本已处理成功。stdin 的 `stdin_state` callback 以 text=`true`/`false` 告知能否接收下一块; 新宿主用它登记和取消现有控制台输入队列中的请求, 输入提交在后台执行 Binder 调用。旧宿主忽略这个新增事件。

## 一. 已桥接 — 默认可用

| 模块 | 方法 | 备注 |
|---|---|---|
| `toast` | showToast, toast | 真 toast |
| `app` | launchPackage, launchApp, openAppSetting, startActivity, openUrl, sendEmail, uninstall, intent, startService, sendBroadcast, getInstalledApps, getPackageInfo, getPackageName, getAppName, isInstalled, viewFile, editFile | Android Intent 与包查询; 邮件打开编辑页, 卸载打开确认页; 服务/广播不使用 root 或 Shizuku |
| `accessibility` | isEnabled, ensureEnabled, click, back, home, recentApps, findByText, clickText, findOne, findAll, longClick, setText, scrollForward, scrollBackward, **swipe, gesture** (M3.2) | 真无障碍; 有每秒限速; 服务未启用时返回 capabilityProviderMissing。swipe/gesture 需显式声明 `accessibility.gesture` 权限 (不被 `accessibility` 隐含), 时长上限 10s |
| `clipboard` | getText, setText, hasText | 真剪贴板 |
| `device` | info, getWidth/Height/Density/DensityDpi, getBrightness/Mode, getBattery/isCharging, getSdkInt/Release/Model/Brand/Product/Board/Bootloader/Hardware/Fingerprint/BuildId, getTotalMem/AvailMem, isScreenOn, wakeUp, vibrate, setBrightness/Mode, keepScreenOn, cancelKeepingAwake, isIgnoringBatteryOptimizations, openBatteryOptimizationSettings | 控制需 device.power; 亮度写入另需 Android 修改系统设置权限; 硬件标识符 (imei/androidId/serial/mac) 仍 blocked |
| `shell` | exec | 真 ProcessBuilder; execRoot 需 `shell.root` 权限; execShizuku 拒绝 |
| `dialogs` | alert, confirm, input/prompt, rawInput, select, singleChoice, multiChoice, progress.show/update/dismiss | Activity 持有真实对话框; 请求超时/关闭/脚本结束释放 |
| `keys` | notifications, quickSettings, lockScreen, takeScreenshot, splitScreen, powerDialog | 声明 keys 且开启无障碍; 返回 Android 是否接受该全局动作 |
| `engines` | myEngine, all, stopAll, stopSelf, execScript, execScriptFile | 宿主脚本引擎 |
| `storage` / `storages` | get, put, remove, clear, keys, contains | |
| `database` / `sqlite` | open, exec, run, get, all, transaction, close | 单线程 executor |
| `notifications` | notify, cancel, cancelAll, getPermissionStatus, openSettings | 受 POST_NOTIFICATIONS 权限门禁 |
| `sensors` | getAvailableSensors, once, subscribe, unsubscribe, drainEvents | 真 SensorManager |
| `ui` | showLayout, update, batchUpdate, close, drainEvents | Activity-owned 声明式 UI |
| `work_manager` | scheduleOnce, schedulePeriodic, cancel, list | WorkManager 真调度 |
| `autojs6:events` | observeNotification, observeToast, observeKey, observeBroadcasts, on/once/off, close | events 根声明仅授权屏幕/电池广播; 通知/Toast/按键各需显式子能力和 Android 对应权限 |
| `autojs6:lifecycle` | checkpoint, readCheckpoint, clearCheckpoint | 注意: 宿主 getBrokerInfo 能力清单里不含它 (由 policy 层单独补), 以本清单为准 |

`app.intent()` 继续返回 JSON 描述符, action/data/type/extras/flags 保留给 Android。`startService()` 返回组件名或 null, 使用普通服务启动并遵循 Android 后台限制; `sendBroadcast()` 使用宿主身份发广播, 接收权限由 Android 检查。`getInstalledApps({includeSystem})` 和 `getPackageInfo(packageName)` 返回 Android 可见包的名称、版本、系统标记及安装/更新时间, 不存在的包返回 null。应用查询须显式声明 app.query, 服务/广播须显式声明 app.activity, 与既有 app 子能力规则一致。

`dialogs.rawInput()` 只返回文本, 不求值; 取消时 input/rawInput 返回 null、confirm 返回 false、单选返回 -1、多选返回 []。singleChoice/multiChoice 接受初始索引。`const progress = await dialogs.progress.show({title, message, max:100, value:0})` 后可 `await progress.update({value:50})` 与 `await progress.dismiss()`; 也可通过 dialogs.progress.update/dismiss 使用 id。进度窗口持续到显式关闭或脚本结束, 不需要悬浮窗权限。Android 若不允许当前应用启动 Activity, 本次调用会失败或超时。

`keys.takeScreenshot()` 触发系统截图操作, 不返回像素, 图片处理请用 images。`keys.powerDialog()` 的决策为开放 Android 电源菜单, 后续关机/重启仍由菜单中的用户选择。无障碍关闭返回 permission-denied; 服务可用但设备不支持或拒绝某个全局动作时返回 false。

## 二. 依赖宿主提供方和 Android 授权 (bridged)

| 模块 | live 方法 | blocked 方法 |
|---|---|---|
| `image` / `images` | requestScreenCapture, stopScreenCapture, captureScreen, readImage, saveImage, toBytes, clip, resize, grayscale, threshold, findImage, matchTemplate, findColor, findMultiColors, recycle | 图像分析需宿主 OpenCV 插件 |
| `media_projection` | requestScreenCapture, nextImage, stop | 需用户确认 Android 录屏授权 |
| `recorder` | getStatus, start, stop | 需 media + media.recording 及 Android RECORD_AUDIO; 真机录音 Check 待补 |
| `media` | getAudioStreamVolume/MaxVolume/Info, setAudioStreamVolume; play, pause, resume, stop, seekTo, getPlaybackStatus (会话播放) | 音量方法需 media + media.audio, 遵循 Android 音量/DND 策略; 播放方法需 media + media.playback, 经宿主 ScriptMusicService 前台媒体通知播放, 同一应用同一时刻只有一个脚本音乐会话; 需含媒体 provider 的宿主 (2026-09-17 已合并进宿主 master) |
| `mediainfo` | read, get, countGet, capabilities (Android 可访问的文件路径; 缺省保持 Node v1, 插件明确宣告后可显式请求插件 v1/v2) | — |
| `media_store` | capabilities, query, get, insert, update, delete, scanFile, exportFile | 读需 media + media.library (Android 13+ 为 READ_MEDIA_AUDIO/IMAGES/VIDEO, 更早为 READ_EXTERNAL_STORAGE); insert/update/delete 另需 media.library.mutate, Android 10+ 只能改本应用创建的记录, Android 9- 需 WRITE_EXTERNAL_STORAGE; 需含媒体 provider 的宿主 (2026-09-17 已合并进宿主 master) |
| `ui.overlay` | show, update, drainEvents, close, closeAll, hasPermission, openPermissionSettings | 需 SYSTEM_ALERT_WINDOW; 真实窗口、属性更新、拖动、推送与退出清理 |
| `package_manager` / `npm` | 字符串/字符串数组 install/update/remove 通过随包 npm 在工作区运行, 支持 registry 下载与生命周期脚本; npm.cli/exec/runScript/pack 提供原生命令行; 对象请求及 list/verify/prune/plan* 保留宿主本地库兼容路径 | 设备未内置 node-gyp/NDK; 下载后文件的执行由 Android 裁决 |
| `input_observer` | observeKeys, drainEvents, close, getAvailableSources (fake/accessibility) | 实源需可运行的无障碍服务; 不拦截按键, touch/intercept 仍拒绝 |

截屏需声明 `screen_capture` 与 `image`。`await images.requestScreenCapture()` 打开宿主既有 Android 授权流程, 授权后才启动 mediaProjection 前台服务; `captureScreen` / `media_projection.nextImage` 返回宿主图片句柄。`saveImage(handle, path, {format, quality})` 保存 PNG/JPEG/WebP, 默认 PNG, quality 为 0..100。`images.stopScreenCapture()` / capturer.stop、系统撤销和脚本结束释放会话; 未授权捕获返回 `permission-denied`。图片延续每次执行默认 32 个活跃句柄的既有限制, recycle 或执行结束释放。M14.1 未授权回归与真实授权链均已验收: 2026-09-10 Sony XQ-DQ72 返回 1096x2560 截图, PNG 尺寸与设备分辨率一致, 裁剪模板回读匹配到 (468,1220), 偏差 0 px。

M14.2 图像操作保持输入句柄有效, clip/resize/grayscale/threshold 返回独立的新句柄。找图使用宿主 Rhino Images 同用的 TemplateMatching, 找色使用 ColorFinder, 图像变换调用同一 OpenCV 后端; clip 与编码使用 Android Bitmap。模板阈值默认 0.9, 范围 0..1; matchTemplate 的 limit/max 默认 5, 范围 1..1000。找色阈值默认 4, 范围 0..255; region 为 `[x,y,width,height]`, 返回坐标位于输入图片中, 不做屏幕比例缩放。多点找色偏移可为负, 匹配点须在原图范围内。grayscale 使用 RGBA→GRAY, threshold 对灰度执行指定二值化类型。未安装可用 OpenCV 插件时, 捕获、读取、保存、裁剪和回收仍可调用, 依赖 OpenCV 的操作返回宿主插件错误。

`await image.toBytes(handle, {format: "png" | "rgba"})` 返回原生 Node Buffer, 也可把格式直接作为第二个参数。PNG 为默认值; RGBA 为紧密排列的 sRGB RGBA8, 使用直通 alpha, 长度为 width × height × 4。图片句柄回收后 Buffer 仍有效。宿主在私有临时文件中编码, 回调 Bundle 通过 `bridgeBinaryPfd` / `bridgeBinaryByteCount` 携带描述符及长度; JSON 仅含元数据。插件直接 mmap 文件并由 V8 BackingStore 持有, `Buffer.from(ArrayBuffer)` 不复制像素。文件目录项及 FD 及时关闭, 映射在 Buffer 回收或 isolate 结束时解除。JNI 与文件桥接均支持, 请求 `binary=true` 为 additive, 未声明该字段的旧客户端不会收到 PFD。`readImage(path)` 继续返回宿主图片句柄, 大图也通过随后调用 toBytes 取回像素, 无 base64 或 `/proc` / `/dev` 文件读取入口。PNG 解码已使用真实 [pngjs](https://github.com/pngjs/pngjs) 包在设备测试中验证。

`ocr.recognizeText(handle)` 与 `barcode.detect(handle, {formats: ["QR_CODE"]})` 可直接使用同一图片句柄, 分别需声明 `image` + `ocr` / `barcode`。OCR 选项支持 `engineId` / `engine` / `variant`; 未指定时宿主按 ML Kit OCR, Paddle OCR, Rapid OCR 的顺序自动选择已启用的引擎 (AutoJs6 6.8.0 起; 更早的宿主固定使用 ML Kit OCR); 找不到已启用的兼容识别插件时错误 category 为 `unavailable`, 实际识别失败仍返回 provider 错误。OCR 与截图 OCR 样例申请 Android 截屏授权后执行真实识别, 在 finally 中回收图片并停止截屏; 只有权限拒绝或插件不可用才输出可读 skip。M14.4 已在小米与 x86_64 上识别宿主显示的 ROADMAP 2026 和二维码, 测试输入由 UiAutomation 截屏落盘后 readImage 取得; 2026-09-10 用户通过 MediaProjection 真实截图识别到 AutoJs6 文件界面文字, 补齐人工授权链; 截屏/屏幕 OCR 样例及同调用链的 Pro 片段转为 stable。

`recorder.start({path:"record.m4a", maxDurationMs:3000})` 在宿主项目目录内通过 PFD 写入 AAC/MPEG-4 单声道文件, 返回录音 id/path; 默认时长 60 s, 可设 1..300 s。开始前检查 Android 麦克风权限, 并先建立带停止按钮的 microphone 前台通知; Android 后台启动限制仍适用。sampleRate 默认 44100 (8000..192000), bitRate 默认 128000 (16000..320000), 设备不支持的编码组合返回 provider-failed。stop 返回 path/byteCount/durationMs/reason 或尚未录音时的 null, 重复 stop 返回上一结果。时长到达、通知停止按钮和脚本退出均关闭 MediaRecorder/PFD 并移除通知。getStatus 包含 microphonePermissionGranted、recording、starting、active 和 lastRecording。权限拒绝返回 permission-denied; MediaInfo 插件未安装返回 unavailable。M15.4 的权限与窗口自动化回归已通过; 2026-09-10 用户完成真实 3 s 录音, 自动停止后文件 51,938 bytes, MediaInfo 解析到 AAC LC / 单声道 / 44.1 kHz 音轨, 录音样例及同调用链的 Pro 片段转为 stable。

`ui.overlay.update()` 可更新选定 id (默认根节点) 的 text/textSize/textColor/backgroundColor/padding/width/height/enabled/visible/type/children; content 替换整棵树。window 可更新位置、尺寸、touchable/focusable/draggable 与 alpha (0.2..1), disclosure 替换时需同时提供 title/text。尺寸和坐标输入为 dp, 返回 bounds 为 px; wrap_content 可在更新时恢复。JNI 上 click/move/update/close 走推送, file 传输保留 drainEvents; move 事件含实际 x/y。窗口随脚本结束自动移除, close 幂等, handle.closed 实时更新; 未获悬浮窗权限时拒绝创建。

## 三. 稳定能力与外部依赖 (bridged-policy)

| 模块 | 门禁 |
|---|---|
| `autojs6:fetch` / `autojs6:websocket` / `axios` / `undici` | 宿主 OkHttp 网络栈, 需项目声明 `network` 并满足宿主网络策略; `fetch` / `websocket` 旧模块名保留兼容 |
| `websocket` | 同上 |
| `ocr` | 需 OCR 外部插件 (ML Kit OCR, Paddle OCR 或 Rapid OCR 任一; 未指定 `engine` 时宿主按此顺序自动选择已启用的引擎) |
| `barcode` | 需 Barcode 外部插件 |
| `java` | 默认启用; 宿主以声明表 (类 → 可构造 / 静态方法 / 实例方法 / 静态字段 / 实例字段) 定义白名单并按表反射执行, 表经 `getBrokerInfo()` 的 `javaInteropPolicyJson` 公布为 `java.policy`; 反射/ClassLoader/进程与原生库加载继续拒绝 |
| `rhino` | 默认提供显式迁移入口; 执行请求使用 `explicit: true`, 不会自动安装旧 Rhino 全局对象; `rhino.install` 装入的 `Packages` / `java` / `android` 代理支持静态调用与构造, 并在类路径上提供 `getStatic(field)` / `describe()` (转发 `java.getStatic` / `java.type`, 由宿主白名单裁决) |
| `worker_threads` | 默认启用, 返回 Node 模块对象; Worker 仅增加执行配置预加载与生命周期清理, 数量不设插件上限且允许嵌套. MessageChannel / MessagePort / 结构化克隆 / stdio / argv / execArgv / environment data 为 Node 原生行为; CJS、ESM、动态 import、require(esm)、JSON attributes 与工作区包解析使用 Node loader. raw 网络开关沿执行继承, fs 交由 Android; worker 中的 autojs6:bridge.callAutoJs 经父线程 MessagePort 到根执行的宿主 broker, 权限、超时和取消继续由现有桥处理. Inspector Session 按执行请求开放 (Debug/Release 均支持), open 仍限 localhost 且 wait=false. WorkerPool 为兼容扩展, 默认并发取 CPU 并行度, 默认不设队列上限与任务超时 |
| `child_process` | 默认启用并遵循 Node 原生语义; 受 Android UID/SELinux/清单与调用方校验约束，但不继承独立 `shell` 桥的私有可执行文件白名单或资源预算，脚本必须自行校验命令、限制 stdio/超时并回收子进程 |

全局 `fetch`、`Request`、`Response`、`Headers`、`FormData`、`WebSocket` 使用 Node 自带的 Web API (网络实现为内置 Undici)。全局网络请求与 raw Node http/https 一样默认可用, 无需宿主 `network` 声明; 请求设 `rawNodeNetworkModulesEnabled=false` 会拒绝全局 fetch/WebSocket 的网络操作, 非联网的数据类仍可用。需要宿主代理、证书与网络策略时, 显式使用 `require("autojs6:fetch")` 或 `require("autojs6:websocket")`, 这两个桥模块仍独立检查宿主 `network` 能力。桥接版的超时上限、响应体大小、方法集合与 WebSocket 消息 / 队列尺寸由宿主网络提供者决定 (宿主 `ControlledFetchPolicy` / `ControlledWebSocketPolicy`: fetch 超时 ≤ 120 s、响应默认 32 MiB / 上限 64 MiB、仅 GET / POST; WebSocket 连接超时 ≤ 60 s、消息默认 64 KiB / 上限 1 MiB、队列默认 32 / 上限 128), 运行时只填 Node 风格默认值 (fetch 超时 30 s、WebSocket 连接超时 10 s、跟随重定向至多 20 次) 并把调用者选项原样传给宿主, `policy.limitsEnforcedBy` 为 `host_provider`。桥接 fetch 的响应体经 ParcelFileDescriptor 交付 (M20.2 第十三批: 请求带 `binary: true`, 支持的宿主把正文写入缓存文件并以 `bodyTransport: "pfd"` 附带描述符, 运行时 mmap 后挂为 Buffer; 空正文与旧宿主仍内联 base64), 响应体只受宿主 `maxResponseBytes` 约束; 插件到宿主方向 (M20.2 第十四批): fetch 请求正文与 WebSocket 消息超过 256 KiB 时, 运行时把字节写入会话上传目录, 插件服务以只读 ParcelFileDescriptor 随请求 Bundle (`KEY_BRIDGE_REQUEST_BINARY_PFD` / `KEY_BRIDGE_REQUEST_BINARY_BYTE_COUNT`) 交给宿主, 描述符只带 `bodyTransport` / `messageTransport: "pfd"` 与字节数, 宿主 OkHttp 直接流式读取文件 (WebSocket 消息按 maxMessageBytes 校验后读入发送), 上行正文只受宿主 `HARD_MAX_REQUEST_BYTES` (64 MiB) / `maxMessageBytes` 约束; 仅当宿主 `getBrokerInfo()` 公布 `bridgeRequestBinaryTransport=pfd` 时启用, 旧宿主仍走内联 base64 (单次 Binder 事务约 1 MB); 宿主回复超过 Binder 事务尺寸时 (如未带 binary 的旧插件收到 2 MiB 内联响应), 第十三批宿主改为立即回一条小体积 BRIDGE_PROVIDER_FAILED, 脚本不再等到超时。

宿主可通过 `runtimeModuleSources` 传入 JSON 模块 `autojs6:worker-policy`, 现仅消费 `resourceLimits`, 例如 `{"resourceLimits":{"maxOldGenerationSizeMb":256}}`. 这些值是 Worker 默认值; `new Worker(file, {resourceLimits})` 可向上或向下覆盖, 数值含义与校验由 Node 处理. 旧 maxWorkers / startupTimeoutMs / taskTimeoutMs 请求字段不再施加限制. WorkerPool 可通过自身 options 设置 maxWorkers、queueLimit、taskTimeoutMs, 单任务也可指定 timeoutMs; 未指定队列上限时 policy.workerPool.queueLimit 为 null.

运行时以 `--require` 注册 Worker 同步加载钩子, 适用于文件、ESM、data URL 与 eval 入口, 并支持嵌套 Worker. 预加载文件只包含插件代码, 位于执行会话的 sandbox 外, 不回写项目; 请求模块源码、已编译 TS 快照与桥端口通过 Node environment data 在内存中传递. 父线程仍能为 Provider 支持的 Worker 入口取源. 任意用户同步 hook 不会自动复制到子线程, 用户的 --require / --import 预加载按 execArgv 继承; 显式 execArgv 可覆盖用户预加载. process.execArgv 现在报告实际的运行时启动参数 (包括 VM Modules 与 ExperimentalWarning 输出设置), 不再伪装为空数组.

Worker 中使用 `require("autojs6:bridge").callAutoJs("device", "info", [], {timeoutMs: 5000, signal})` 调用宿主; 也可从 autojs6:bridge 具名导入 callAutoJs. 高层 device / ui 等 facade 仍在主脚本线程, 嵌套 Worker 通过各级父端口转发到同一根执行, 不为每个 isolate 注册一套 JNI broker. 参数与结果遵循 Node 结构化克隆 (例如 Buffer 跨线程成为 Uint8Array), 错误码与错误字段透传. `worker-cpu`、`wasm-worker` 与 `worker-host-bridge` 提供计算、WASM 与宿主调用示例.

## 四. 插件内本地实现 (local-shim, 不出进程)

`files` (Node fs 便捷封装), `base64`, `colors`, `formatter`/`fmt`, `converter`/`cvt`, `s13n`, `mime`, `nanoid`, `opencc`, `pinyin`, `pinyin4j`, `jsox` (+.mathx/.arrayx/.numberx), `plugins` (本地 ./plugins 目录扫描)。

宿主还提供显式桥调用 `files.list/stat/read/write/mkdir/rename/delete`, `console.tail` 与 `pinyin` / `pinyin4j` 方法, 用于宿主工作区和控制台等能力。它们分别使用 `files` (+ 写入时 `files.write`, 删除时 `files.delete`), `console`, `pinyin` / `pinyin4j` 声明; 原生 Node console/fs 及本地便捷模块维持原有行为。

`await device.info()` 返回现有 `autojs6-bridge-device-info-v1` 结构的实时快照, 追加 screen 的 density/brightness/brightnessMode/canWriteSettings, battery.isCharging, memory.totalMem/availMem 和 build.bootloader/fingerprint。异步 `get*` 方法从该快照读取对应值; 同步的 device.width/height/density/sdkInt 及 Build 字符串属性为脚本启动时的设备信息。内存单位 bytes, 电量为百分比, 不支持的亮度设置值为 -1。系统构建 fingerprint 是固件构建信息, 不包含硬件唯一标识。

`setBrightness(0..255)` / `setBrightnessMode(0|1)` 需 `device.power` 和宿主的 [Android 修改系统设置权限](https://developer.android.com/reference/android/provider/Settings.System#canWrite(android.content.Context)); 未授权时返回 permission-denied, 不自动跳转设置页。`keepScreenOn(timeoutMs)` 要求正整数, 持有当前脚本独立的定时屏幕 WakeLock, 超时、cancelKeepingAwake 或脚本退出释放。`media.setAudioStreamVolume(stream, volume)` 接受该流 minVolume..maxVolume 的整数且不播放提示音; device 的 get/setMusicVolume, NotificationVolume, AlarmVolume 便捷方法委托该桥。设置同一流音量仍受 Android DND 权限限制。

注意 (M2.4 语义, 2026-09-18 收窄): 只有代替真实 npm 包的运行时模块 (`axios`、`colors`、`mime`、`nanoid`、`opencc`、`undici`) 会被工作区 node_modules 里安装的同名包覆盖 (桌面 Node 语义, 未安装时 shim 照常回答); 其余运行时模块与宿主能力 facade (`java`、`fetch`、`websocket`、`device`、`files` 等)、Node builtin 以及 `node:` / `autojs6:` 前缀不受同名包影响, `require` / `require.resolve` / ESM `import` 三处规则一致 (ESM 裸名 import 现覆盖全部运行时模块名); 策略经 `autojs6:profile` 的 `moduleResolutionProfile` 公布 (`npmPrecedence: "npm_shims_only"`, `npmShadowableModules`)。

## 五. 文件访问与工作区传输

`fs`, `fs/promises` 与 `node:sqlite` 直接使用 Node 原生实现. 普通文件访问的权限边界由 Android 的应用 UID, SELinux, 存储授权和动态链接器决定; 插件不再叠加路径沙盒或敏感目录拒绝表. `/proc`, `/sys`, `/dev` 和其他目录一样, 能否访问以 Android 实际结果为准.

- 绝对路径, 相对路径, 父目录, 符号链接以及原生 fd 操作遵循 Node 24 语义. 进入执行时进行真实 chdir, `process.cwd` / `process.chdir` 返回或改变实际工作目录.
- 异步文件操作由 libuv 线程池执行; stream, watch, glob, Dir/FileHandle/Stats/Dirent 保持原生对象和选项. 错误保留 `ENOENT`, `EACCES`, `ERR_INVALID_ARG_VALUE` 等 Node/Android 代码.
- SQLite ATTACH, VACUUM INTO 和目录 PRAGMA 使用 SQLite 原生行为, 不设置运行时 authorizer 路径规则. `enableLoadExtension` 与 `allowExtension` 仍遵循 Node 的 API 配置.
- Node 文件操作使用插件 UID; MediaInfo, 图片, 录音, 媒体播放等宿主桥使用宿主 UID. 一方有权限的路径不保证另一方可访问, `filesystemRoots` 等项目字段是兼容元数据, 不授予 Android 权限.
- 宿主私有项目通过工作区归档或 module-source provider 交付. 归档条目, 描述符, 字节数, tombstone 与输出提交校验属于跨进程传输契约, 不定义普通 `fs` 的访问范围. 输出归档当前不传递符号链接; npm 项目需要回传 bin 入口时使用 `bin-links=false` 或 `--no-bin-links`.
- 大正文桥接使用 PFD: 宿主大响应可通过响应体描述符交付, 支持对应协议的宿主接收超过内联阈值的 fetch/WebSocket 上传描述符. 请求上限由实际宿主 provider 决定, 运行时不把这些桥协议上限套到 Node 原生网络或文件 API.

退役路径策略码见 [错误目录](nodejs/ERROR_CATALOG.md). 旧实现的演进记录保存在 [历史说明](nodejs/history/FILESYSTEM_M20_NOTES.md).

## 六. TypeScript 编译要求

插件运行时负责**执行 JavaScript 编译产物**, 不内置 `tsc`、SWC 或其他完整 TypeScript 编译器:

- raw `.ts` / `.mts` / `.cts` **直接派给本插件**仍会 fail-closed, 返回 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`。当前 AutoJs6 宿主源码已接通独立 TypeScript Compiler 0.6.0+ 的完整 `Program` 编译链: 宿主先编译项目快照, 再把 JavaScript 产物派给本插件; 用户从宿主启动 `.ts` 项目时无需自行生成中间文件。
- 运行时不再提供正则剥离回退或兼容请求开关。raw `.ts` / `.mts` / `.cts` 无论语法是否可擦除都返回同一个 compiler-required 错误; 只有宿主编译器产出的 JavaScript 快照进入执行链。
- 已编译项目快照可通过 `typeScriptPrecompiledSnapshot` 与 `typeScriptPrecompiledSourceNames` 标记; 运行时先把 `.ts`/`.mts`/`.cts` 静态或动态说明符映射到请求中已存在的 JavaScript 模块。若 compiler-backed Node 项目在运行期间新建精确普通文件, module-source provider v3 可使用 `compile_missing_typescript` 把 no-follow 读取的源码以 PFD + byte count + SHA-256 交回宿主编译, 成功状态为 `compiled_typescript`。v1/v2 provider 仍保持 snapshot not-found。
- v3 只接受项目私有 workspace 内非声明形式的 lowercase `.ts/.mts/.cts`; 路径逃逸、符号链接、identity 变化、大小写/extensionless 歧义、Host 项目或生成目标碰撞都会 fail closed。单个 Host 编译输入最终受 8 MiB 上限约束; ordinary provider 操作仍最多 5 s, v3 compile transport 独立最多 30 s。
- 编译器 source map 与宿主控制台中的 `.ts` 原始栈回映由宿主编译链负责。当前链路已覆盖入口、导入模块、dynamic import 与 v3 运行时源码; 插件负责归一化生成代码和导入 CommonJS 的栈位置, 但不会凭空生成 source map。动态类型错误使用 `ERR_AUTOJS6_TYPESCRIPT_COMPILATION_FAILED`, 并保留文件名与 TypeScript diagnostic。

- 能力目录 (catalog 1.5.5 起) 把 `typescript` 标为 **stable**: 宿主编译路由 (预编译快照 + module-source provider v3 的 `compile_missing_typescript` 按需编译) 是正式能力, raw TypeScript 直派 fail-closed 属于设计而非未完成项; 目录中已退役的 `legacyStrippingRequestKey` / `typeScriptLegacyStrippingRequestKey` 字段一并删除, 运行时没有对应的请求键。

普通 `.js` / `.cjs` / `.mjs` 与 npm 纯 JavaScript 包不受此限制。

## 七. 执行总预算与取消

`runScript` / `startScript` 请求中的 `timeoutMs` 采用**等待 + 执行的 wall-clock 总预算**:

- 正数预算从插件 Binder 入口开始计时, 包含契约检查、串行队列等待、工作区/模块准备和原生 Node 执行。队列阶段耗尽同样返回脚本超时, 不再伪装成 `BUSY`。
- 超时结果固定为 `timedOut=true`、`errorCode=ERR_AUTOJS6_SCRIPT_TIMEOUT`, 并回显 `timeoutMs`。诊断 payload 记录超时发生在 `queue_wait`、`pre_*` 或 `execution` 阶段。
- 执行阶段超时复用协作取消链: watchdog 先请求 `node::Stop`; 若 3 秒内仍未释放目标工作槽, 则只重启该工作进程。另一个工作槽及全局等待队列保持可用; 能协作停止时下一脚本可复用同一 PID。
- 请求缺少 `timeoutMs` 或传 `<=0` 时, **脚本执行本身不设时限**, 保持长驻服务语义; 无显式预算的队列等待仍有插件内部 10 分钟安全上限。宿主或项目配置若主动填入正数, 该值就是总预算; 有意长驻的调用应传 `0`。
- AutoJs6 宿主另保留 `timeoutMs + 5s` 的 Binder 响应兜底; 插件异常未返回时, 宿主会先发 `cancelScript` 再向调用方返回同一规范化超时码。

M8.1 设备证据覆盖 API 28/36/37 模拟器与 3 台真机: `while(true)` 在 3 秒预算后均得到规范化超时, 且后继脚本 6/6 复用同一 PID; 队列预算、无 timeout 长驻与人工取消也均通过。

`runScript` / `startScript` / `prewarmRuntime` 可以携带 `idleExitMs` (Long)。缺失或非正数为 0, 保持常驻; 正数表示最后一个执行完成且队列为空后, 空闲这么多毫秒便退出专用运行时进程。只有实际获准执行的请求会更新策略, 后续未带此字段的请求会恢复默认常驻; 队列等待、执行与预热期间不计为空闲。`getRuntimeInfo` 返回当前 `idleExitMs` 和 `idleForMs` (忙碌时为 0), 查询本身不重置计时。AutoJs6 的 `NodePluginScriptRequest.idleExitMs` 会透传正数, 旧宿主无需变更。

例如 `idleExitMs=3000` 会在目标工作槽执行完成后空闲约 3 秒时退出。退出前在同一准入锁内再次确认无执行并关闭准入, 防止定时器误杀新任务。Android 在客户端仍绑定时不会仅因 `stopSelf()` 就销毁服务, 因此这里先停止服务再退出工作 PID; 调度器负责重连, 宿主继续使用原公开 Binder。该策略不限制长驻脚本的执行时长, Android 调度或设备休眠也可能使实际退出晚于设定时间。参见 [Android 服务生命周期](https://developer.android.com/develop/background-work/services)。

`executionMode` 是 lifecycle config 的请求级权威来源。缺失时仅为兼容旧 contract-v2 调用方而回退宿主 `engine-info`, 两处都缺失则为 `one_shot`; 不带 `engine-info` 的显式 `interactive_long_running` 请求按 `interactive_session` 生成配置并开启 checkpoint 门。checkpoint 只用于脚本主动保存/读取 JSON 进度, `automaticRestart=false` 与 `restartPolicy=never` 不变; 真正的宿主 lifecycle bridge 仍会独立校验 execution mode 与 launch surface。能力目录 1.5.5 起只列出可运行的执行模式: `one_shot` (script)、`interactive_long_running` (interactive_session, available) 与 `scheduled` (scheduled_runner, 2026-09-17 起 available: 宿主 WorkManager 定时运行器经真实 Binder 拉起插件的实跑通过); `packaged_long_running` 启动面与 `node_sandboxed` / `worker_computation` 保留名已移除, 插件不再把 `packaged_long_running` 视为开启 checkpoint 的启动面 (导出 APK 无法打包本运行时, 宿主也从未发送该启动面)。目录的 `lifecycle.admission` / `cancellation` / `transport.output` 现与 `getRuntimeInfo` 一致: 每进程单活 + 可配置 1-8 槽 (默认 2) + 默认不限的全局 FIFO、协作取消 + 进程重启兜底、流式输出。

`runtimeAdapter` 是保留字面值的 deprecated no-op 键: 运行时槽位由已绑定插件服务的 runtime info 决定, 单次请求不能覆盖。当前 AutoJs6 宿主不再建模或发送该键; 旧调用方继续发送时会被宽容忽略。native payload 中的 `embedded_script.runtime_adapter.*` 是插件内部 C++ adapter 诊断, 与这个废弃请求键无关。

公开 RUNTIME 服务在 `:nodejs_runtime` 中调度。M23.7 起, 从 AutoJs6 插件中心 > 此插件 > 设置 > 跳转到启动页进入运行设置, 可选择 **并行执行槽数 1-8 (默认 2)** 与 **排队上限 (默认不限)**。上限留空表示不限, 输入 0 则不接受等待任务, 正整数表示最多等待任务数; 只有显式上限已满或 executionId 重复才返回 BUSY。槽全忙时新任务进入全局 FIFO, 等待任务不占执行线程, 队列深处仍可立即取消或按请求的 timeoutMs 超时; 未指定 timeoutMs 时也没有隐含的 10 分钟排队期限。

设置保存在插件私有空间, 由调度进程统一读写, 保存后立即影响后续调度。扩容会调度已有队列; 缩容不停止运行中的脚本, 等活跃任务数降到新容量以下后再派发; 降低排队上限不丢弃已接受的任务。最多八个不导出的工作服务运行于 `:nodejs_runtime0` 至 `:nodejs_runtime7`, 只有分配任务或显式 prewarmRuntime 才绑定启动。每进程仍保持一个 Node Environment; 脚本内 worker_threads 不占进程槽, 数量与资源由 Node / Android 管理。prewarmRuntime 预热配置范围内的空闲槽, 不打断脚本。idleExitMs 继续按各槽最近的执行请求回收空闲进程; 调度器解除已退出空闲槽的绑定, 后续工作按需重连。内存压力由 Android lmkd 处理, 不新增内存门禁。

getRuntimeInfo 回报 processModel=process_pool、实际配置的 processPoolMaxConcurrentExecutions、dispatcherPid 与 slots 数组。queueUnlimited=true 表示不限, 此时 queueCapacity=2147483647, 保持旧宿主要求非负整数的校验兼容; 有限队列的 queueUnlimited=false, queueCapacity 为设置值。每槽含 slotId/pid/active (executionId 或空串)/queued/rss (bytes)/ready/enabled。未启动或已回收的槽 pid=-1、rss=0; 查询状态本身不启动槽。缩容期间仍在运行的额外槽保留在数组中, enabled=false, 完成后移除。等待任务属于全局队列, 因此每槽 queued 为 0, 总数位于 queuedExecutions。顶层 pid/processName 兼容表示第 0 槽, activeExecutionId 表示当前第一个活跃槽; 精确状态使用 slots。maxConcurrentExecutions 保持 1, 表示每进程的单活容量; 池总容量使用 processPoolMaxConcurrentExecutions。结果和回调包含 slotId (尚未分配时为 -1), 输出回调经调度器转发, 工作区和宿主 broker Binder 继续直接在执行进程调用。AIDL 事务号、v2 握手及 v3 异步启动协议不变。

M23.8 起, `prewarmRuntime` 的可选 `prewarmSlots` 限定只预热前 N 个配置槽 (最小 1, 最大当前池容量; 缺失仍检查整个池). 宿主启动预热和用户启动准备请求使用 1, 与 TypeScript 编译, 宿主引擎初始化和工作区归档并行. 执行请求可接手正在预热的同一个槽, 不为这次执行再启动一个备用进程; 预热失败仍由实际执行按原有路径报告. 默认 idleExitMs=0 保持常驻, 用户显式设置空闲回收仍有效.

启动诊断继续放在结果的 `nativeValues` 中, 不改变 AIDL 事务号. 插件侧 `timing.pool_queue.ms` / `timing.pool_connect.ms` 分别记录排队与工作服务连接; runtimeInfo 的预热 payload 中 `timing.dlopen_cpp.ms` / `timing.dlopen_node.ms` / `timing.dlopen_bridge.ms` 记录库加载, `timing.process_runtime_initialize.ms` 包含 Node/V8 平台初始化. 每次新 isolate 的 `timing.isolate_create.ms` / `timing.environment_create.ms` / `timing.node_bootstrap.ms` / `timing.bootstrap.ms` 分别记录 isolate, 环境及 Node 基础初始化, LoadEnvironment 到 embedder preload 回调, 内嵌运行时 JS 初始化. `timing.load_environment.ms` 是包含后几段的总阶段, 不可与子阶段相加. `embedded_script.execution_source.bytes` / `.lines` 记录生成脚本体积.

宿主结果增加 `timing.host.typescript_prepare.ms`, `engine_prepare.ms`, `request_prepare.ms`, `workspace_archive.ms`, `bridge_prepare.ms`, `session_and_dispatch.ms`, `launch_to_dispatch.ms`, `dispatch_to_first_stdout.ms`, `launch_to_first_stdout.ms` (后续键共用 `timing.host.` 前缀). 起点为编辑器/文件运行入口或定时 Intent 处理, WorkManager 为 worker 开始准备执行; 不包含操作系统决定何时调度任务的等待. 无 stdout 时对应值为 -1. 编译器冷启动计入 TypeScript 准备; WorkManager 的 `lastRun.nativeValues` 保留这些耗时. 预热与准备可重叠, 总耗时以首输出时间为准.

Node 原生 `module.enableCompileCache` 默认在加载工作区模块前启用, Worker 继承. 缓存位于插件私有 cache 目录, 通过工作区外的临时别名与 Node 的 portable 模式跨执行副本复用; 别名不会进入回传归档. 源码/Node 版本失效与写入原子性由 Node 处理, 初始化失败不阻止脚本. 可用 `NODE_DISABLE_COMPILE_CACHE=1` 关闭, 或 `NODE_COMPILE_CACHE` 指定 Android 允许访问的目录; `module.enableCompileCache` / `getCompileCacheDir` / `flushCompileCache` 保持 Node API. profile 的 compileCache 报告 enabled / directory / customLoader=false, 不声称 Node 未提供的命中率. 参阅 [Node 24 模块缓存文档](https://nodejs.org/docs/latest-v24.x/api/module.html#module-compile-cache) 与 sample/nodejs/compile-cache.

cancelScript 和 postMessage 按 executionId 路由, 取消排队脚本不会执行其源码。同步原生调用无法响应 node::Stop 时, 3 s 兜底只重启目标槽, 调度器将已请求的取消/超时返回对应终态; 其他进程死亡返回 unavailable, 不重放脚本。槽进程在执行中意外死亡 (非取消兜底) 时, 调度器读取系统为该 pid 保留的 `ApplicationExitInfo` (Android 11+; 最多等待 2 s; 系统从进程死亡通知、zygote 退出状态和 lmkd 三处按任意顺序补齐记录, 先落下的可能是 reason=UNKNOWN、CRASH_NATIVE 但 signal 0 或纯 SIGKILL, 因此记录连续 400 ms 未再变化才采信), 失败结果的错误码仍为 `ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE`, 消息改为 `Node runtime slot <槽> (pid <pid>) exited during the execution: <summary>.`, summary 形如 `LOW_MEMORY (killed by the system low-memory killer; rss 2.6 GB)`、`CRASH_NATIVE (SIGABRT; see the logcat tombstone)`、`SIGNALED (SIGKILL)`、`EXIT_SELF (exit code 1)`, 记录未落盘时为 `exit reason not recorded yet`, Android 10 及以下为 `exit reason unavailable below Android 11`; 结果附 `slotExit` Bundle (available / source / pid / slotId / executionId / processName / reason / reasonName / status / signal / nativeCrash / rssBytes / pssBytes / timestamp / description / summary / waitedMs), getRuntimeInfo 附最近一次的 `lastSlotExit`。插件不装信号处理器, tombstone 与退出原因都由系统记录。调度器自身死亡时仍由宿主既有 execution-lost 逻辑处理。M16.2 已验证第二槽 stdin、FIFO/队列满载/取消、超时、空闲重连与强制重启后另一槽持续运行。

### 异步执行

异步执行使用第六个 AIDL 事务 `startScript(request, callback)`。调用前必须同时确认 `maxContractVersion >= 3` 和 capabilities 中的 `asyncScriptExecution`; 早期仅支持 postMessage 的 v3 插件没有该标记。基础握手仍为 contractVersion=2, 前五个事务号与旧 runScript 返回值保持兼容。

- 非空 callback 为必需参数。`accepted=true` 和 executionId 表示已经登记并进入执行或等待队列, 不是执行成功; 返回后可立即取消。拒绝时直接返回 `accepted=false` 的错误结果, 不发送终态回调。
- 接受后 stdout/stderr/stdin_state 按原格式推送, 唯一的 `finished` 事件增加 `result` Bundle, 内容与同步 runScript 的完整结果相同, 并回显 executionId/slotId。该事件在工作进程及调度器关闭工作区 FD、释放执行后发送, 宿主此时才验证并回传输出归档。
- 调度器与工作进程均在普通执行线程等待长驻脚本; Binder 只处理短启动事务和既有事件/取消/输入调用。callback 保持原同步 AIDL, 以保留已发布客户端的回复协议与输出背压, 不改为 oneway。
- 新宿主协商后使用 startScript, 插件或调度器死亡则结束等待并报告响应丢失, 不重放。未广告异步能力的插件继续使用同步 runScript。正数总预算与宿主额外 5 s 响应兜底适用于两种入口。

### 实时桥传输

实时桥默认通过 JNI 直达 Java/Binder 宿主 broker, 响应经 `uv_async_t` 回到 Node 事件循环后交付给原调用的 Promise。JNI 路径不创建请求/响应文件, 也不启动文件轮询线程。JSON 以 UTF-8 字节数组跨 JNI 传输, 支持补充平面字符及 JSON 转义后的 NUL。

`autojs6:bridge-live-config` 的可选 `transport` 字段接受 `"jni"` (默认) 或 `"file"`。宿主可在运行时模块中传入 `{"transport":"file"}` 强制保留原文件协议; 缓存目录由插件生成。JNI 初始化或接收请求前不可用时自动回退文件传输; 已接受的调用发生 broker 错误会直接拒绝 Promise, 避免重新派发产生重复副作用。`embedded_script.bridge_live_transport` 报告实际通道, JNI 模式下 `bridge_live_dispatch_poll_count=0`。

两条通道共享 `autojs6:bridge-limits.maxPendingBridgeCalls` (默认 32, 硬上限 128) 作为在途调用窗口: 超出窗口的调用在运行时内按先进先出排队等待空位 (超时从发起时计, 排队中同样可超时或取消), 不再以 `ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT` 拒绝; Java 与 JNI 通道保留同一上限作为传输护栏。`autojs6:bridge-limits` 的其余字段 (maxImageHandles / maxShellCommands / maxNetworkRequests / maxWebSocketConnections / accessibilityQueriesPerSecond) 由宿主 broker 读取同一模块自行执行, 运行时不再复制这些配额; `autojs.profile.bridgeLimits` 以 `runtimeEnforced` / `hostEnforced` 标出各方执行的字段。JNI 响应队列仅接受当前通道的待处理请求, 重复响应与超时/取消后迟到的响应不再交付。超时会解除原生事件循环引用; 脚本结束时清理 V8 回调、请求队列和 JNI 通道, 下一次执行使用独立通道编号。

### 订阅事件推送

`require("autojs6:events")` 提供 Android 观察者, `require("events")` / `require("node:events")` 继续返回原生 Node EventEmitter。声明 `events` 后, `on/once("screen_on" | "screen_off" | "battery_changed", listener)` 自动启动系统广播订阅, 也可显式 `await observeBroadcasts()`。初始电池状态与后续变化采用同一事件结构。

`await observeNotification()` 另需 `events.notification` 和 [Android NotificationListenerService 通知读取授权](https://developer.android.com/reference/android/service/notification/NotificationListenerService), 回调包含系统通知 id/key/packageName/title/text/postTime; 不通过自行调用脚本监听器模拟通知。`observeToast()` 另需 `events.toast` 与无障碍, 沿用宿主对外部应用 Toast 的观察范围。`observeKey()` 另需 `events.key` 与无障碍, 复用 input_observer 的真实按键来源, 事件只观察、不消费。三个子能力均须显式声明, 不由 events 根能力隐含授予; 缺少 Android 授权返回 permission-denied, 已授权但系统服务未连接返回 unavailable。

每种来源每个脚本保留一个订阅, 重复 observe 返回相同订阅; close 后可重新订阅。返回对象和模块支持 on/once/off, 返回对象可 close/drainEvents。JNI 使用 M12 的现有推送通道, file 兼容模式以 drainEvents 工作。脚本结束释放监听器和广播接收器, 通知/无障碍服务断开会关闭对应订阅。`await events.close()` 关闭全部来源并清除模块监听器。真机物理按键仍按 Roadmap 的人工验收要求记录。

`host-events` 人工样例等待 45 秒, 项目配置 `node.timeoutMs: 65000`。宿主提交 `36d1e8c52` 修正项目启动忽略该字段的问题, 超时优先级为启动参数显式覆盖值、项目的正数配置、既有 5000 ms 默认值; 原有 Node 1.3.0 可使用此修复。测试音量加键前须暂时关闭宿主 "使用音量加键控制脚本运行", 否则全局停止快捷键会取消脚本。测试后恢复设置; 通知读取授权与 Toast 无障碍权限分别检查。完整步骤见 [人工验收指南](nodejs/MANUAL-ACCEPTANCE.md)。

```javascript
const events = require("autojs6:events");
try {
  const battery = await new Promise(resolve => events.once("battery_changed", resolve));
  console.log(battery.percent, battery.isCharging);
} finally {
  await events.close();
}
```

`sensors.subscribe`, `websocket.connect`, `ui.showLayout`, `ui.overlay.show` 与 `input_observer.observeKeys` 在 JNI 通道上协商事件推送。建立订阅的请求带可选 `events=true`; 支持推送的宿主在原成功结果中增加 `subscriptionId`。此后同一个 `INodeJsHostCapabilityCallback.onResponse` 可多次收到 `event=true` 的 Bundle, `KEY_BRIDGE_RESPONSE_JSON` 对应如下信封:

```json
{"id":"原订阅请求 ID","ok":true,"event":true,"subscriptionId":"订阅 ID","result":{"type":"event","event":{"type":"accelerometer","values":[0,0,9.8]}}}
```

宿主先交付订阅确认, 再发送注册期间积累的事件。旧宿主未返回 `subscriptionId`、或显式使用文件通道时, facade 继续采用 `drainEvents`。现有能力声明与 AIDL 事务不变; 缺省 `events=false` 的旧请求继续使用原协议。

订阅句柄提供 `on/once/off`, 传感器可使用 `sensors.subscribe("accelerometer", undefined, {samplingIntervalMs:20}).on("event", listener)`。传感器最小可请求间隔为 20 ms, 默认仍为 250 ms; Android 将采样间隔视为调度提示, 实际首事件还取决于传感器产生数据的时间 ([SensorManager 文档](https://developer.android.com/reference/android/hardware/SensorManager))。WebSocket 的属性回调与 `addEventListener/removeEventListener` 继续可用。UI 的 `on/once` 保留返回取消函数的约定, `off` 显式移除监听。UI、悬浮窗和输入观察者只在存在监听时保持事件循环存活; 传感器订阅及 WebSocket 在关闭前保持活动。

`drainEvents` 保留原有有界队列。显式同时使用推送和拉取时可在两处读到同一事件, facade 不会再自动重复拉取已经协商推送的订阅。关闭句柄或脚本结束会撤销事件回调和原生循环引用; 宿主资源继续由既有资源注册表清理。

原生等待交付的事件队列最多 128 条、4 MiB, 超限丢弃最旧事件; 普通 RPC 响应保留各自的待处理上限并不被事件流挤出。`embedded_script.bridge_live_event_count` 统计已接收推送, `embedded_script.bridge_live_event_dropped_count` 报告原生队列丢弃数。宿主在订阅确认前也只保留最近 128 条事件。

### 长驻桥会话诊断

`embedded_script.bridge_live_responses_json` 是最近 32 条响应的诊断摘要, 不再保存整段会话的所有响应。每条诊断最多 1536 UTF-8 字节; 大响应的诊断副本替换为带 `diagnosticTruncated=true` 与 `responseCharacters` 的记录, 实际交付给脚本的响应保持完整。响应数组最多 49185 字节, 调用总计数与失败计数继续累计。

请求文件完成删除后立即释放内存中的去重记录。`embedded_script.bridge_live_retained_request_count` 与 `embedded_script.bridge_live_retained_response_count` 报告当前保留数量, 用于区分仍待处理的请求和有界的历史响应。

### 本地 Inspector (Debug / Release)

M23.5 起, `inspectorEnabled` 是默认 false 的请求级开关, Debug 与签名 Release 宿主/插件均支持. 插件 Java 层和 C++ 不再复核构建类型. 宿主在 "关于应用与开发者" 页面长按应用图标进入开发者选项, 开启 "允许 Node.js 脚本使用 Inspector" 后, 普通脚本、项目及后台执行都会在派发时读取该设置; 默认关闭, 修改影响后续执行. 宿主内部调用方可显式传 true/false 覆盖设置, 直接 Binder 请求缺少字段时仍为 false. 开关只允许使用调试能力, 不自动开启监听.

`inspector` 与 `inspector/promises` 返回 Node 自身模块, CJS/ESM、`process.getBuiltinModule` 以及 Worker 使用同一套入口规则. 未开启时 `inspector.url()` 返回 undefined, `open` 和 Session 构造报 `ERR_AUTOJS6_BUILTIN_DISABLED`; 环境不创建 Inspector agent. 显式启用后可以使用原生 Callback/Promise Session、CPU Profiler 和 HeapProfiler. `open` 强制绑定 `127.0.0.1`, 接受 localhost 别名, 拒绝其他地址与 `wait=true`; `waitForDebugger` 的阻塞等待也不可用. `NODE_OPTIONS=--inspect*` 继续遵循既有启动环境处理.

桌面联调: 脚本执行 `inspector.open(0, "127.0.0.1", false)` 并打印 `inspector.url()`, 取得设备端口后执行 `adb -s <serial> forward tcp:9229 tcp:<device-port>`, 在 `chrome://inspect` 配置 localhost:9229 后连接. 调试完成关闭连接、调用 `inspector.close()`, 并移除该转发. 开关关闭后新的执行不接受 Inspector 激活.

CDP 按 Node 原生协议提供源码路径、运行时值、CPU profile 与 heap 数据. 导出和清理分析文件由脚本负责; 项目元数据不能替代宿主设置, 打包 APK 支持仍属 M24. 可运行示例: `inspector-debug`、`cpu-profile`、`heap-snapshot`.

## 八. 明确不可用 (denied)

| 项 | 原因 |
|---|---|
| accessibility 的 powerDialog / waitFor / rawNode | 此旧入口仍 blocked; 电源菜单通过 keys.powerDialog, swipe/gesture 见第一节 |
| 硬件标识符 (imei 等) | 隐私 fail-closed |
| RootAutomator / Shizuku 输入通道 | M15.5 决定不新增; gesture/swipe 用无障碍, 设备有 root 时可显式使用 shell.execRoot |
| 请求级门禁关闭的 builtin (`worker_threads` / `child_process` / 原生网络模块), 以及未获许可的 Inspector 激活/Session | `ERR_AUTOJS6_BUILTIN_DISABLED`; 其余 Node 内建模块 (含 `wasi`, M23.2) 全部可用 |

`media_projection` 已接入 Android 授权和前台服务流程, 不属于 denied; 授权条件及当前验收范围见第二节。

2026-09-08 特权输入决策: 保留 `shell.execRoot(command)` 和显式 `shell.root` 声明, 由已有 shell provider 执行用户提供的 input/sendevent 命令并遵循设备 root 授权。当前没有需要另建 RootAutomator 的已复现输入缺口, 因此不增加设备事件节点映射、注入会话或额外取消机制。Shizuku 需要另一套授权与服务依赖, 本阶段不接入, `shell.execShizuku()` 继续拒绝。常规触摸自动化使用已实现的无障碍 gesture/swipe。

`node:wasi` 使用 Node 原生实现, 可在 Android 权限范围内访问资源. WASI 的 preopens 等参数不是 Android 权限替代品; `wasm-basic` 与 `wasm-worker` 提供可运行示例.

## 九. 真机验证矩阵

"已桥接" ≠ "已真机验证"。当前有真机断言证据的:

| 路径 | 用例 | 状态 |
|---|---|---|
| `device.isScreenOn` | 宿主 `pluginRuntimeUsesLiveHostBridgeForDeviceCall` | ✅ M1.1 |
| device 信息 / 亮屏 / 音量 | 宿主 NodeDeviceControlsInstrumentationTest, 三 ABI 各 2/2; 逐项类型、正值/非空、权限拒绝和 5 s 亮屏窗口 | ✅ M15.1 |
| `toast` + `clipboard` + `storage` + `shell.exec` + `app.getAppName` | 宿主 `pluginRuntimeDrivesCommonAutomationApisThroughLiveBridge` | ✅ M3.1 |
| 加密模块 provider (v1) | 宿主 `pluginRuntimeDecryptsEncryptedModuleThroughHostV1Provider` | ✅ M1.3 |
| TypeScript provider (v3) | 宿主 `compilesRuntimeCreatedTypeScriptAndKeepsSnapshotErrorsStable`、`runtimeCreatedTypeScriptTypeErrorsReturnReadableDiagnostics`、`mapsRuntimeCreatedTypeScriptFailureBackToDynamicSource` | ✅ T5-1, API 35 Xiaomi 23046RP50C |
| `accessibility.swipe` 派发链 + `accessibility.gesture` 能力门禁 | 宿主 `pluginRuntimeSwipesThroughAccessibilityGestureCapability` (服务未开时断言可读 capabilityProviderMissing; 未声明能力被插件本地拒绝且零派发) | ✅ M3.2 |
| `accessibility.*` 动作在服务开启下的完成路径 | 无障碍开启依赖手工授权 (小米 adb 不可直写 secure settings), 待手工开启后跑同一用例补证 | ⚠️ 部分验证 |
| `image.toBytes` PNG/RGBA + pngjs | 1080p 实际截图夹具, 三 ABI / JNI 与 file, 像素及 Buffer/FD 生命周期 | ✅ M14.3 |
| `ocr.recognizeText` + `barcode.detect` | 宿主画面 ROADMAP 2026 与二维码; 小米 arm64 与 x86_64, OCR/条码回归各 11/11 | ✅ M14.4 识别链及 2026-09-10 MediaProjection 人工授权链 |
| 其余 bridged 模块 | 无真机断言 | ⚠️ 未验证 |

## 十. 与 Node 原生重叠的能力: 选择指引 (M3.3)

原则: **能用 Node 原生就用 Node 原生**。桥接调用要跨进程 (文件轮询传输, 单次 ~10ms 级), Node 原生在 V8 里直接执行; 且原生 API 与桌面 Node 教程/npm 生态零差异。不为重叠能力重复造桥。

| 需求 | 用这个 (Node 原生) | 不要用 | 备注 |
|---|---|---|---|
| 读写文件 | `fs` / `fs/promises` (Node 原生模块, Android 权限范围) | `files` shim | shim 本身就是 fs 封装, 直接用 fs 少一层; 共享存储授权见第五节 |
| HTTP/HTTPS 客户端 | 全局 `fetch`、`http` / `https`, 或工作区安装的 npm 包 | `autojs6:fetch` / `axios` / `undici` | 全局 fetch 使用 Node 内置 Undici; 显式桥接版走宿主 OkHttp, 代理/证书策略跟随宿主 |
| WebSocket 客户端 | 全局 `WebSocket`, 或工作区安装的 npm 包 | `autojs6:websocket` | 原生 API 遵循 Web 标准事件接口; 桥接 API 提供异步 connect/send/close 与宿主策略 |
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

新增或修改能力时按以下顺序登记, 并在提交前运行本插件的手动任务 `verifyNodeHostIntegration` (含 verifyNodeHostCapabilityManifestAlignment 和 verifyNodeHostApiMirror):

1. 插件 `node_bridge_sources.cpp`: 声明脚本侧所需能力并完成本地预检。
2. 插件 `NodeBridgePermissionManifest.java`: 加入插件已知能力清单。
3. 宿主 `NodeBridgePermissionManifest.kt`: 加入宿主能力常量、清单和方法映射。
4. 插件 capability catalog: 在新 catalog 版本的 `bridge.permissionCapabilities` 与相关 `bridge.operations` 中登记。

发布快照 `nodejs-capability-catalog/1.3.0` 保持原有 48 个能力。M14/M15 的本地 catalog 1.4.0 增加图像、device/media、事件观察者和 app 方法, 同步宿主已有的 console / files / files.write / files.delete / pinyin / pinyin4j 能力, 尚未对外发布。M14/M15 时 verifyNodeHostCapabilityManifestAlignment 验证 host=59 / plugin=59 / catalog=59; verifyNodeHostApiMirror 的 10 个公共 API 文件通过。M18.2 第七批 (media.playback / media.library / media.library.mutate) 后插件清单为 62, 宿主 master 于 2026-09-17 合并媒体 provider 后同为 62, 发布快照 `nodejs-capability-catalog/1.5.5` 同步发布 (1.4.0 及更早快照不变), 该任务现验证 host=62 / plugin=62 / catalog=62。契约支持上限同步为 3, 旧同步执行契约仍为 2。

### 普通媒体和图片路径 (v1.4.0 开发版)

`mediainfo.read/get`、`image.readImage/saveImage`、`recorder.start({path})`、`media.play(path)`、`media_store.insert({source})`、`media_store.scanFile(path)` 与 `media_store.exportFile(..., destination)` 接受绝对文件路径, 或以宿主执行工作目录为基准的相对路径。父目录与符号链接由 Android 文件 API 解析; 文件名中的 `..`、冒号和空格保留, 只拒绝空路径和 NUL。访问身份为执行该能力的宿主应用, Android 的权限、应用私有目录和存储访问规则仍然生效。文件 URI / content URI 不是这些方法的输入类型。

此行为需要同时升级插件和包含 Android 文件解析器修复的宿主; v1.3.0 发布包与旧宿主仍有原来的媒体路径限制。工作区归档、模块传输和 MCP files 授权是其他调用接口, 不由这次文件 API 调整改变。

### 媒体播放会话与 MediaStore (M18.2 第七批, 宿主 master 已于 2026-09-17 合并)

`media.play(path, { volume, looping })` 经宿主 `ScriptMusicPlayer` 租约在 `ScriptMusicService` (Media3, 前台媒体通知) 中播放, 返回冻结的会话对象 `{ id, path, looping, volume, status(), pause(), resume(), seekTo(ms), stop() }`; 模块级 `media.pause/resume/stop/seekTo/getPlaybackStatus(options)` 默认作用于当前执行的会话, `options.session` 可传会话对象、状态对象或 id, 过期 id 得到 `active=false` 的快照而不是错误。每次调用都返回 `autojs6-node-media-playback-v1` 快照 `{ id, path, looping, volume, active, playing, ended, durationMs, positionMs }` (未激活时 `durationMs=0`, `positionMs=-1`)。`volume` 为 0..1, 超出范围在插件侧抛 TypeError; 同一应用同一时刻只有一个脚本音乐会话, 新的 play 会替换旧会话; 执行结束时宿主释放租约并撤下通知。Android 前台服务资格 (宿主需在前台或持有可见悬浮窗) 不满足时 play 以 provider 错误结束。

`media_store` 只经 JSON 行交换数据: `capabilities()` 报告 `sdkInt`、`scopedStorage`、集合 (`audio`/`images`/`video`, Android 10+ 另有 `downloads`)、各集合可用列、读权限状态与 `queryPolicy`/`mutatePolicy`; `query(collection, { filter, sort, limit, offset, columns })` 的 filter 是结构化 JSON (标量 eq、null 为 IS NULL、数组为 IN, 运算符 eq/ne/gt/gte/lt/lte/like/contains/startsWith/endsWith/in/isNull, `ownedOnly` 在 Android 10+ 限定本应用创建的记录), 由宿主编译为参数化 selection, 脚本不能提交 SQL; `sort` 接受 `"-dateAdded"` / `"dateAdded desc"` 形式, `limit` 1..1000 (默认 100)。`get` 返回 `autojs6-node-media-store-item-v1` 记录或 `null`; `insert(collection, { displayName, mimeType, source, relativePath })` 把宿主可读文件复制为新记录 (Android 10+ 走 IS_PENDING, 更早版本写公共目录并需 WRITE_EXTERNAL_STORAGE); `update` 只接受 `displayName` 与 `relativePath` (TITLE 等元数据列由 MediaProvider 推导, 桥拒绝而不是假装写入); `delete`、`scanFile(path, { mimeType })` 与 `exportFile(collection, id, destination)` 同样只返回 JSON。游标、Uri、描述符、Context 等原生对象不跨桥 (rawObjectPolicy)。

`autojs6:compat.media` 在 Node 媒体模块之上补充 Rhino 风格别名 `playMusic(path, volume, looping)`、`pauseMusic`、`resumeMusic`、`stopMusic`、`musicSeekTo`、`isMusicPlaying`、`getMusicDuration` (无会话时 0)、`getMusicCurrentPosition` (无会话时 -1) 与 `scanFile` (返回布尔)。这些方法在插件私有工作区副本中运行时, 相对路径按宿主的项目目录解析, 因此样例把 `tone.wav` 作为项目资产随目录一起复制; 宿主侧的隐私披露 `media_library` 面已登记, 插件侧的 `__autojs6_privacy_disclosure_policy_report` 未同步扩展。

## npm registry 与命令行

M23.6 起, `await require("package_manager").install("is-number@7.0.0")` 在当前工作目录运行随包 npm, 安装后可以直接 `require("is-number")`. 字符串、字符串数组或不传参数的 install/update/remove 分别调用 npm install/update/uninstall; update 遵循 package.json 中的版本范围, 改到指定范围可使用 install. 若已加载模块再更新文件, require.cache 仍遵循 Node 行为, 不会自动清空.

```javascript
const pm = require("package_manager");
await pm.install("is-number@7.0.0");
await pm.update("is-number");
const npm = require("npm");
await npm.cli(["ls", "--json"]);
await npm.runScript("build", ["--verbose"]);
```

第二个参数可传 cwd、registry、ignoreScripts、offline、env、npmArgs、signal、timeoutMs. npmArgs 是额外的 npm 参数, 放在命令前; npm.runScript 的第二个参数是脚本参数数组, 选项放在第三个参数. 不传时遵循 npm 默认值和工作区 .npmrc, 包括代理、registry、ignore-scripts 与 bin-links. 选项中的 registry/ignoreScripts/offline 显式覆盖相应 npm 配置. CLI 路径仍遵循 child_process 请求开关, 不需要宿主 package_manager.mutate 权限或 allowMutation 确认字段.

CLI 首次调用时才在插件 filesDir 解压既有 npm/corepack 资产, 按现有资产 lock 的摘要、条目数及字节数校验并原子提交; 槽进程共享该目录. 通过安装目录内的 libnodexe.so (argv0=npm) 启动 npm, PATH 中提供指向该启动器的 node/npm 等命令链接, 供生命周期脚本使用. 普通脚本不承担解压开销. 既有 npm 11.19.0 资产内含 node-gyp 12.4.0 的 JavaScript 脚本, 不额外安装 NDK、Python、git 或编译器; 依赖设备编译工具链的包由 npm/系统给出实际错误.

返回结果含 command/args/cwd、exitCode=0、stdout/stderr 与 npmVersion; 输出同时通过既有执行回调流式回传. 失败的 Promise 保留 npm 输出、exitCode、signal, 并从 npm 错误行提供 E404、ENOTCACHED 等原生 code; 无该错误行时使用退出码或信号. spawn/AbortSignal 错误保持 Node 自身行为, CLI 资产准备失败报告 ERR_AUTOJS6_NPM_CLI_SETUP_FAILED. 不设额外默认超时; 可用 timeoutMs/signal 控制此次子进程.

带 sourceDir/name/version 等字段的对象请求保留旧宿主本地库路径, 继续使用原来的权限、allowMutation 与 expectedIntegrity 契约. list/verify/prune 和本地 plan* 同样属于该路径. planRegistryInstall/planTarballInstall 仅解释传入元数据, 不联网、不修改文件, status 为 metadata_only; 实际解析、缓存、下载和解包全部由 npm 执行.

npm 在执行中创建的 .bin 链接保持原样. 现有工作区归档协议不支持符号链接回传; 需要回写宿主项目且依赖带 bin 入口时, 在 .npmrc 设置 bin-links=false 或显式传 npmArgs: ["--no-bin-links"]. 这不是 npm 调用的默认覆盖. Android 也可能拒绝执行 app data 中的脚本或下载二进制; JS 工具可显式通过 node 读取入口文件. 可运行项目见 sample/nodejs/npm-registry. 真正外网验收单独运行 NpmRegistryDeviceTest 并传 nodeNpmOnline=true; 常规 NpmCliSmokeTest 只访问回环测试 registry.

### 本地 JS 插件与能力状态 (1.6.0)

`pluginSystemProfile` 描述当前本地 JS 插件管理器及生命周期; Android provider 插件, 远程市场, 签名归档分发与更新渠道属于未规划产品方向, 不作为运行时能力字段输出. `packageIntegrityProfile` 描述现有宿主本地存储的校验与恢复流程. `rhino` 安装状态的 shadowing 为 not_installed / installed, child_process 被请求关闭时 stdin 为 denied_with_child_process. 这些标记说明实际行为, 不扩大宿主授权.

样例状态仅使用 stable 与 host_provider_required. 后者说明需要真实宿主 provider 和相应 Android 权限, 不表示样例 API 未完成. 旧 Phase 14/15 声明矩阵保留在类型目录 history 下, 仅供旧代码类型兼容, 不代表当前能力. Node 自身的 ExperimentalWarning 和上游 VM Modules 状态不属于插件的暂定标记.


### 打包应用调用外部 Node 运行时 (1.7.0 开发版, M24)

Node 项目和带有 `"nodejs";` 入口的脚本通过配套宿主与 APK Builder Template 打包. 生成的应用不嵌入 libnode, 设备需要安装 Node.js Runtime 插件. 项目使用 Node 时, 打包界面的 Node.js Runtime 依赖会自动选中且不可取消; 旧 Embedded Node.js 配置继续提示迁移.

独立签名应用声明普通权限 `org.autojs.plugin.nodejs.permission.RUNTIME`, 使用 `org.autojs.plugin.nodejs.PACKAGED_RUNTIME` 服务动作. `PACKAGED_WAKE` 无界面激活入口供用户启动的应用激活尚未运行的插件. 这些入口共享既有运行时进程池, AIDL 事务保持不变. 原 INFO / RUNTIME / WAKE 入口保留原有 `org.autojs.permission.PLUGIN` 契约.

任何声明专用普通权限的应用均可使用新入口. Node 代码以运行时插件的 UID 执行, Android 能力桥在生成应用自己的 UID 下运行, 仍需要该应用的能力声明和 Android 授权. 独立应用按运行时协议版本协商, 不把应用自身的 versionCode 与 AutoJs6 宿主版本号比较. 插件不可用时, 交互式启动会显示下载引导.

需要包含本项实现的宿主和 Runtime Kit, 以及配套 APK Builder Template. 当前公共发行版仍为 1.6.0; 1.7.0, nodejs-api 1.8.0 和相关宿主改动尚未对外发布. TypeScript 仍需 Compiler 产出的 JavaScript, 不在 Node 插件内直接编译.
