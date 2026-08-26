# AutoJs6 Node.js Runtime 插件 — 开发路线图 (Roadmap)

> 修订日期: 2026-08-26
>
> 本路线图取代此前所有里程碑编号 (X3d/X3e/X3f/X3g/X3i/X3j 等)。旧编号只保留在 `tools/nodejs/ownership/evidence/` 的历史证据文件中, 不再继续演进。
>
> 2026-08-26 所有权更新: M5 中关于宿主保留 `sample/nodejs` 与 `docs/nodejs/types` 副本的决策已被取代。样例, 类型声明, 项目向导及其 Gradle 校验现在仅由本插件仓库维护; 宿主只保留路由, Binder 客户端, 工作区传输, Android 能力桥接与最小集成测试。详见 `docs/DEVELOPMENT-ASSETS.md`。

## 一. 项目定位 (不变的初衷)

为 AutoJs6 提供接近桌面体验的 Node.js 运行能力:

1. **像桌面 Node.js 一样流畅运行 JavaScript** — 完整 ES6+ 语法, CJS/ESM, npm 生态, 真实 V8 性能。
2. **可以运行 AutoJs6 API 脚本** — Node 脚本内可调用宿主自动化能力 (无障碍, app, device, http 等)。

## 二. 开发原则

- **先让脚本跑起来, 出了异常再针对异常修复。** 不做 "一次性完美方案", 不用测试和安全边界堆砌代码, 不把 "无穷尽验证正确性" 当作产品本体。
- 每个任务的完成标准是 **一条用户可见的能力**, 而不是一份报告 / 一个校验门禁 / 一条哈希锁。
- 保留最小烟雾测试 (能跑通 hello-world 级别脚本即可), 其余重型验证一律移出默认构建链, 按需手动触发。
- 诊断信息保留 (定位问题需要), 但不再新增 probe 类基础设施。

## 三. 现状盘点 (2026-08-17 勘察结论)

### 已具备且真实可用的能力

- ✅ Node.js 24.5.0 真实内嵌: 每 ABI 一个 `libnode.so` (~100 MB, arm64-v8a / armeabi-v7a / x86_64), dlopen + dlsym 驱动 `node::NewIsolate → CreateEnvironment → LoadEnvironment → SpinEventLoop` 完整生命周期, 常驻进程复用 V8 平台。
- ✅ 构建链可用: `:app:assembleDebug` 产出各 ABI + universal APK (已本机验证, v1.1.3 build 41)。
- ✅ 插件服务完备: `org.autojs.plugin.INFO` 发现 + `org.autojs.plugin.nodejs.RUNTIME` 执行, 独立 `:nodejs_runtime` 进程, 同步执行 + 预热 + 重启式取消。
- ✅ CJS/ESM/TS(擦除级) 源码执行, stdout/stderr 回传, 环境变量, 模块源注入, 运行时模块注入 (`autojs6:host-app-info` 等)。
- ✅ 历史真机证据: API 34 x86_64 模拟器上 CJS/ESM 端到端执行通过 (见 evidence 存档)。

### 诊断出的 "跑不起来" 断点

1. **[高度疑似主因] 模块源 Provider 契约硬拒绝**: 插件端把 module-source-provider 契约锁死为 `v2 only` (`MIN=2, MAX=2`), 并且对请求里缺失/非 Integer 的版本字段一律按 `-1` 处理。宿主 AutoJs6 (当前 master, build 5276) 的 nodejs-api 还是 **v1**, 且 runScript 请求里 **根本不放** 版本字段 → 只要宿主附带 provider binder (加密模块场景), 插件必然返回 `ERR_AUTOJS6_NODE_PLUGIN_CONTRACT_MISMATCH`。而两边的 AIDL 内容 **完全一致**, 这纯粹是元数据门禁自伤。
2. **工作区归档传输是硬前置**: `runScript` 无条件要求 workspace-archive-v2 双 FD, 缺失即抛错。任何不实现完整归档协议的调用方 (旧宿主 / adb 调试 / 第三方) 连 `console.log("hi")` 都跑不了。
3. **验证性基础设施反噬开发循环**: `check` 挂接 capability-truth / runtime-ownership / resolver 等门禁 (全仓库哈希扫描 + node 子进程); 主源码 9.2k 行 Java 对应 ~83k 行 C++ 里 93% 是 probe; 3.3k 行 conformance gradle 脚本。迭代速度被这些结构性拖慢, 而它们对 "脚本能不能跑" 没有贡献。
4. 单活准入 + 重启式取消: 第二个脚本直接 `BUSY`, 取消 = 杀进程。可用性差 (M2 处理, 不是当前主因)。

## 四. 里程碑

### M0 — 最简脚本先跑起来 (首个可交付任务组) ✅

目标: **一个只带 `source` 的最小请求就能执行 Node 脚本并拿到 stdout**; 宿主旧契约不再被硬拒; 开发循环恢复轻快。

- [x] M0.1 直跑模式 (direct-run): `runScript` 在缺少 workspace FD 时不再报错, 改为在插件私有目录直接执行 (自动建立工作目录, 路径映射恒等, 提交/回写自动跳过)。带 FD 时行为与原来完全一致。— `PluginWorkspaceArchiveSession.openDirect` + `hasWorkspaceDescriptors` 分流。
- [x] M0.2 契约宽容: 请求中缺失 `contractVersion` 视为当前版本 (显式错误值仍拒绝); module-source-provider 接受 v1..v2, 缺失版本按 v1 处理, 并在与宿主 provider 往返时回显协商版本 (v1 宿主发 v1 请求, v1 响应归一化为 v2 语义)。— 单测 75 个全绿。
- [x] M0.3 门禁下链: `check` / 常规构建不再依赖 capability-truth、runtime-ownership、resolver、nodejs-api publication 等验证门禁; 全部保留为手动任务 (`verifyNode*` 系列), 需要时单独执行。— `:app:check --dry-run` 已确认 0 引用。
- [x] M0.4 构建验证: `:app:assembleDebug` 全 ABI 通过 (v1.1.3 build 41)。
- [x] M0.5 真机烟雾验证: x86_64 模拟器 (API 36 与 API 37 各一台) 安装插件 APK, 最小请求 (仅 `source`, 无 FD/无契约字段) 跑通 `console.log`, 断言 stdout 含 `m0.smoke=42`, 约 0.9s 完成; 同机回归原 FD 路径 (CJS+ESM conformance x3d_02) 通过。承载测试: `SimpleRunSmokeTest`。
- [x] M0.6 本 Roadmap.md 落库, 旧里程碑体系归档说明 (见文档头部)。

交付物: 可安装 APK + 通过的烟雾验证 + 本文档。

### M1 — 与 AutoJs6 宿主真实打通 ✅ (2026-08-17 完成)

目标: 在真实 AutoJs6 App 里, 编辑器输入 `"nodejs";` 脚本 → 点击运行 → 看到输出。

- [x] M1.1 宿主侧联调: 用 master 分支 AutoJs6 (nodeFull flavor) + 本插件, 真机/模拟器跑通 `sample/nodejs/hello-node`。记录并修复联调中出现的每一个具体异常 (逐异常修复, 不做预防性改造)。— 2026-08-17 真机 (小米 Android 15 arm64) 插件 v1.1.3 + 宿主 v6.8.0: 宿主 `NodeExampleProjectInstrumentationTest` 7/7 (含 hello-node), 插件 conformance 24/24。修复 3 个异常: ① bootstrap 硬锁 config v2 → 宽容 v1..v2 并回显协商版本; ② conformance mock provider 不认 metadata preflight → not_found 回应 + 计数断言更新; ③ TS 剥离器字符串掩码用空格导致 `('cjs' as string)` 被剥成 `()` → 掩码改用非空白占位符 U+0001, 附回归单测。附加事实: 小米真机跑宿主 instrumentation 需先亮屏解锁并把宿主拉到前台一次, 否则 FGS 被拒、settle 超时。
- [x] M1.2 契约对齐决策: 与宿主仓库同步 `nodejs-api` (以哪边为准做一次性对齐, 建议宿主升级到插件的 v2 AIDL 包, 或插件回落 v1); 移除 `releases/` 下 AAR 哈希锁对迭代的阻塞。— 2026-08-17 决策: **以插件 v2 为准, 宿主择期升级; 过渡期由插件 v1..v2 宽容层承接** (已真机验证)。依据: 两边 AIDL 内容一致; 宿主 v1 provider 对 v2 请求的拒绝响应 (version=1 + INVALID_REQUEST) 恰好触发插件的一次性降级重试, 桥是闭合的; 宿主升 v2 需要实现 v2 操作语义 (materialize_missing_plaintext 等), 不是换常量, 按"逐异常修复"原则留到宿主仓库联调时做。哈希锁阻塞已随 `:nodejs-api` 源码模块化移除: app 改用 project 依赖, `assemble`/`check` 零锁引用 (dry-run 验证), 锁只留在手动发布任务 (`verifyNodeJsApiPublication` 等)。
- [x] M1.3 加密模块脚本联调 (宿主 provider v1 路径) 跑通。— 2026-08-17 真机: 宿主新增 `pluginRuntimeDecryptsEncryptedModuleThroughHostV1Provider` (AES 加密 `secret.cjs` + 宿主 v1 `NodeJsEncryptedModuleSourceProvider` → 插件 runtime 解密执行, 断言 decrypted_count=1, 一次性 v2→v1 降级协商生效), 与 live bridge 用例同类 2/2 通过。逐异常修复 2 个: ① 插件 engine-info 注入诊断把 workspace 映射预填误报为 "existing" → 新增 `withReplacedRuntimeModule` 保留真实来源 (host_broker); ② 宿主 bridge 测试仍断言旧 "working_directory" 标签 → 更新为 M0.2 后的 "exact_metadata_snapshot"。插件 conformance 24/24、宿主样例 7/7 回归通过。
- [x] M1.4 项目级脚本 (project.json + 多文件 + node_modules) 通过工作区传输跑通。— 2026-08-17 真机: 宿主新增 `pluginRuntimeRunsProjectWithNodeModulesThroughWorkspaceTransport` (project.json + lib/helper.cjs + node_modules/demo-pkg → 插件 runtime `require` 相对路径与裸包名均成功)。逐异常修复 1 个: 宿主 `NodeJsPluginWorkspaceArchiveTransport` 只打包请求显式命名的文件 (R4 免扫描原则), 项目里未列名的 lib/node_modules 到不了插件工作区且 v1 provider 无法按需物化明文 → 对带 project.json 标记的声明式项目目录做受限树遍历 (拒符号链接/特殊文件, 沿用 4096 文件 / 64MB 预算), 裸脚本路径保持零扫描。回归: bridge 3/3, 宿主样例 7/7。
- [x] M1.5 失败路径可读: 联调中出现的错误信息能让用户看懂 (错误码 + 一句人话), 不再输出诊断键值对墙。— 2026-08-17 真机: 宿主新增 `pluginRuntimeFailuresCarryReadableCodeAndMessage` (throw / MODULE_NOT_FOUND / TSX 三类失败断言"码+一句话", 禁止键值墙与冗余前缀)。逐异常修复 3 个: ① 宿主引擎把整面 failure envelope 打进用户控制台 → envelope 只留 logcat, 控制台留 JS 栈 + 一行错误; ② 插件失败消息叠加 "plugin execution failed:" 样板前缀 → 直接用原始异常消息 (错误码走 KEY_ERROR_CODE); ③ TSX 等预派发失败被宿主 bridge 误判为 EXECUTION_LOST (零扫描 commit 校验缺席) → 宿主按 `commit_allowed=false` + `native_dispatch_started=false` 识别声明式预派发失败, 插件保持 TSX 拒绝先于工作区物化 (x3e 负例语义不变)。回归: 插件 conformance 24/24, bridge 4/4, 宿主样例 7/7, 冒烟通过。

### M2 — 桌面级 Node 开发体验 ✅ (2026-08-18 完成)

目标: 常驻交互与长脚本可用, 接近本地 `node foo.js` 的手感。

- [x] M2.1 stdout/stderr 流式回传 (脚本运行中即时输出, 不等结束)。— 2026-08-17 完成: AIDL 零改动 (复用 `INodeJsRuntimeCallback.onEvent` + 既有 EVENT_STDOUT/EVENT_STDERR 常量, v1 事务号锁不受影响)。C++ `CapturingPipeReader` 在 fd/pipe 读取线程把原始 chunk tee 给进程级 `JavaOutputSink` 槽位 (新 JNI `nativeSetOutputStreamSink`, 单活门禁保证 set→run→clear 无竞态); 流式 chunk 不受终态 4KB 截断上限约束, 长输出首次完整可达。服务端 `StreamingOutputSink` 增量 UTF-8 解码 (跨 chunk 多字节字符重组) 后即时 `onEvent`; 流式生效时终态不再重复回放, fd 捕获不可用时自动回退旧行为; 无 callback 调用方行为完全不变。宿主 `NodeJsRuntimePluginBridge.runScript` 新增 `outputListener`, 引擎把事件即时写 GlobalConsole, 终态按 `streamed_output_events` 标记跳过二次打印。验证: 新增 `StreamingOutputSmokeTest` (脚本首行打印后 setTimeout 2s 再打印, 断言首个 stdout 事件早于 FINISHED ≥1s) 于 x86_64 API 37 模拟器 + 小米 arm64 Android 15 真机双通过; 插件 conformance 26/26; 宿主 bridge instrumentation 4/4 (真机 + 新插件 APK); 新增 `Utf8StreamDecoderTest` 4 用例; 全 ABI assembleDebug 通过。
- [x] M2.2 协作式取消: 不重启进程即可停止脚本, 重启仅作兜底。— 2026-08-17 完成: 用 libnode 已导出的 `node::Stop(Environment*, StopFlags)` (dlsym, 与既有 embedder 符号同模式) 从 binder 线程停掉活跃事件循环。C++ 侧执行 id 标记的 stop scope (`beginActiveScriptStopScope`/`registerActiveScriptEnvironment`/`requestActiveScriptStop`): 服务端准入后开 scope, lifecycle 在 CreateEnvironment 成功后发布 Environment 指针, SpinEventLoop 返回或 FreeEnvironment 前清除; 环境未就绪时 stop 记为 pending 并在注册时立即派发; id 不匹配 (脚本已结束) 的取消直接拒绝, 杜绝停错下一个脚本。服务端 `cancelScript` 改为: `requestCooperativeCancellation` (标记 lease, 不关门) → native stop → 3s 宽限窗口内执行未释放才走原 `process_restart` 兜底 (同步卡死的脚本 node::Stop 停不了); 取消成功的结果统一改写为 `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED` + 一句人话, 不再把 drain 后的 exit 0 误报成功。runtimeInfo `cancellationMode` → `cooperative_stop_with_process_restart_fallback`。验证: 新增 `CooperativeCancelSmokeTest` (setInterval 永驻脚本 → 首个流式输出到达后 cancel → 断言取消错误码 + 同 PID 复用跑下一脚本成功) 模拟器 + arm64 真机双通过; 全量 androidTest 27/27; gate 新增协作取消单测。
- [x] M2.3 串行队列 (替代直接 `BUSY` 拒绝)。— 2026-08-18 完成: 选串行队列而非真并发 — native 层 `embeddedProcessRuntimeExecutionMutex` 使执行天然串行 (每执行一个 fresh isolate, 但进程级互斥), 真并发需 native 大改, 与"先跑起来"原则冲突; 队列已消除用户可见的 BUSY 掉脚本问题。`NodeRuntimeExecutionGate` 重写为 monitor + FIFO 等待队列 (容量 3): `acquire(id, maxWaitMs)` 忙时在 binder 线程阻塞等待 (binder 线程池默认 15+, 占 4 无压力), release 直接把槽位交给队首; 满员/超时/排队中被取消各返回独立 outcome 与可读消息 (queue_full/wait_timeout/cancelled_while_queued); 等待预算 = 请求 KEY_TIMEOUT_MS, 未指定则 10min 上限。`cancelScript` 先撤排队项 (无 native 交互) 再走 M2.2 协作停止; prewarm 保持非阻塞 tryAcquire 不排队。runtimeInfo: queueCapacity=3, queuedExecutions 快照。验证: 新增 `SerialQueueSmokeTest` (3 线程同时提交 → 全部成功各含唯一标记, 无 BUSY) 模拟器 + arm64 真机通过; gate 单测 9 个 (FIFO 顺序/容量拒绝/等待超时/排队取消/协作取消); 全量 androidTest 28/28; 真机 M2 冒烟组 4/4。
- [x] M2.4 npm 生态实测: 10 个高频纯 JS 包实跑。— 2026-08-18 完成: lodash / dayjs / ms / semver / uuid / debug / mime@3 / qs / js-yaml / ajv (含它们的 23 个传递依赖, 真实 `npm install` 产物 1.3k 文件) 通过 workspace-archive 传输在插件 runtime 实跑, 每包一个核心行为断言全绿 (`NpmEcosystemSmokeTest`, 模拟器 + arm64 真机)。逐异常修复 3 个解析器缺口 (全部是 Node LOAD_AS_FILE 语义偏差): ① 相对 require 带"伪扩展名"被硬拒 (`require('./util.inspect')` 应探 util.inspect.js, object-inspect 依赖) → 本地解析按 exact→+.js→…候选链走, 仅 TS/native/ESM 拒绝保持硬性; ② 包子路径同问题 (`require('get-proto/Object.getPrototypeOf')`, qs→side-channel 依赖链) → node_modules 子路径解析同改; ③ AutoJs6 compat shim 与 npm 包重名时 shim 胜出 (`require('mime')` 返回 shim 而非安装的包) → 已安装 node_modules 包优先于 compat shim (桌面 Node 语义), Node builtin 与 `node:`/`autojs6:` 前缀不受影响, 无包安装时 shim 照常回答。axios/express 按计划延后 (需更多网络/流栈验证)。回归: 全量 androidTest 30/30。
- [x] M2.5 长驻脚本生命周期管理与手动停止。— 2026-08-18 完成: 能力由 M2.1/M2.2 组合天然构成 (流式输出 = 长驻期间持续可见; 协作取消 = 手动停止; FIFO 队列 = 长驻期间后续脚本排队不丢), 本项收口为端到端生命周期验证: 新增 `LongRunningLifecycleSmokeTest` — 不带 timeout 字段的 setInterval 常驻脚本连续流出 ≥12 个 tick (~3s, 证明无隐式超时截断), 运行中 `getRuntimeInfo` 可观测 activeExecutionId/activeForMs, `cancelScript` 停止后拿到 ERR_AUTOJS6_NODE_SCRIPT_CANCELLED, 同 PID 复用跑下一脚本。模拟器 + arm64 真机通过。注: 插件侧无超时即无限运行 (仅宿主 runBlocking 侧有 5min 默认 deadline, 属宿主策略, 宿主长驻模式 timeoutPolicy 独立管理); 断电/进程死亡后的自动重启明确不做 (checkpoint restartPolicy=never 保持)。
- [x] M2.6 网络模块默认放开 (http/https/net/dns/tls)。— 2026-08-20 正式化: 插件端与宿主端均默认启用受支持的原生网络模块，不再接受构建期开关或旧请求键；白名单边界保持为 dns/dns/promises/http/https/net/tls，dgram 等未支持面仍明确拒绝。插件 manifest 包含 `android.permission.INTERNET`；`NetworkDefaultOnSmokeTest` 覆盖无功能开关请求下的 http server + loopback 自请求。此前的临时 Gradle 属性已删除，不参与构建或宿主 AAR。

### M3 — AutoJs6 API 能力面扩展 ✅ (2026-08-18 完成)

目标: Node 脚本里可用的宿主 API 覆盖日常自动化场景。

- [x] M3.1 盘点 live bridge 现有可调用能力, 输出一页 "已可用 API 清单"。— 2026-08-18 完成: 清单落库 `docs/HOST-API.md` (五档分类: bridged 15 模块 / bridged-partial 7 / bridged-gated 8 / local-shim 15 / denied)。关键盘点结论: ① 可用面远大于预期 — M3.2 目标里的 toast/app.launch/click/text 查找/剪贴板/shell **全部已桥接**, 唯一真缺口是 swipe/gesture (宿主 blockedMethods, 需独立 capability); ② 原定基线 `require("accessibility")` 样例的 SKIPPED 分支使其不能作真机证据, 真机验证矩阵单列一栏; ③ 宿主 getBrokerInfo 能力清单漏 lifecycle 模块 (policy 层单独补), 以文档清单为准。真机扩面验证: 宿主新增 `pluginRuntimeDrivesCommonAutomationApisThroughLiveBridge` (一个脚本串调 toast.showToast + clipboard.setText/getText 回读 + storages.put/get 回读 + shell.exec echo + app.getAppName, 断言 7 次 live dispatch 全成功零失败), 真机 (小米 arm64 Android 15) bridge instrumentation 5/5。
- [x] M3.2 补齐高频 API: toast / app.launch / click / swipe / text 查找 / 剪贴板 / shell。— 2026-08-18 完成: 除 swipe 外六项经 M3.1 盘点确认早已桥接并在 M3.1 用例中真机验证。swipe/gesture 缺口本项落地: 宿主 `NodeBridgeProtocol` accessibility 新增 swipe(x1,y1,x2,y2,durationMs) 与 gesture(durationMs, [[x,y]...]) 派发 (复用 `GlobalActionAutomator`→`dispatchGesture` 同步等待完成, 时长上限 10s 防 binder 线程被长手势钉死), 从 registry blockedMethods 移出; 新增独立能力 `accessibility.gesture` (registry gesturePolicy 要求), 插件 JS 预检与宿主 manifest 校验双侧都要求显式声明, 不被 `accessibility` 前缀隐含; 插件 accessibility shim 增加 swipe/gesture 方法。真机 (无障碍未开态): `pluginRuntimeSwipesThroughAccessibilityGestureCapability` 断言可读 capabilityProviderMissing 失败 + 未声明能力本地拒绝零派发, 宿主 bridge 6/6; 服务开启下的完成路径待手工授权后补证 (小米 adb 不可写 secure settings)。插件 conformance 31/31 回归。HOST-API.md 已同步。
- [x] M3.3 files / http 等与 Node 原生能力重叠的 API: 文档引导用 Node 原生实现, 不重复造桥。— 2026-08-18 完成: HOST-API.md 新增 "重叠能力选择指引" 一节 (fs/http/Buffer/npm 包 vs 对应 shim 与桥接版的取舍表, 含"仍需走桥"的边界: 设备能力与宿主身份场景)。无代码改动。
- [x] M3.4 `sample/nodejs` 收敛: 57 个登记样例按 "能跑/不能跑" 重新标注。— 2026-08-18 完成: 扫描全部样例的 require 依赖对照 M2/M3 现状, `examples.json` 重标 11 条 — 升 stable 8 条 (http-client-compat/notifications/sensor-monitor/execution-queue: M2.6 网络默认开或桥已全 live 且样例自含; long-running-service: M2.5; scheduled-node-task: work_manager 全 live; database: M3.1 真机验证; fs-promises: M2.4), 降 partial 3 条 (screenshot-find-image: media_projection denied 只能走 skip 路径; disabled-features-demo: 依赖非默认实验开关; typescript-smoke: 触及 media_projection/rhino/java 门禁面)。未采用 `_pending/` 目录迁移: `verifyNodeExamples` 门禁强制目录↔manifest 一一对应, 移目录会破坏它, 三态 status 标注已达成 "能跑/不能跑" 的表达目的。校验: `verifyNodeExamples` 通过, 真机样例 instrumentation 7/7。

### M4 — 减脂与常态维护 ✅ (2026-08-18 完成)

目标: 代码量与真实功能匹配, 新人可读。

M4 收尾盘点: C++ (不含 Node 头) ~97.7k→~51k 行 (扣除 43k 行内嵌运行时 JS 构建器后桥接核心 ~7.6k); gradle+js 验证体系 -13.3k 行; conformance androidTest 24→5 用例; 服务类 2667→693 行 + 四个职责单一的协作类; README 一页答清装/跑/查错。此后进入常态维护: 逐异常修复 + 冒烟组守护 (单测 + 12 个 androidTest)。2026-08-21 增补 M5 (宿主↔插件职责归位)。

- [x] M4.1 删除 C++ probe 体系 (embedded_probe_* 全家, 191 值枚举, ~200 个 probe JNI 导出), 预期 C++ 从 ~83k 行降到 <15k 行。— 2026-08-18 完成: 先做双侧可达性勘察 (宿主仓 219 个 external fun 中生产路径仅 9 个, probe 分发器 runProbe 零调用方; 插件 Java 侧对 probe 零引用), 确认 probe JNI 全部是死代码后删除 ~217 个导出 (nativeEmbeddedProbe* 全家 + 5 个 legacy lifecycle 变体 + AdapterV1Diagnostics + 信号安全 crash marker)。lifecycle.cpp 20941→2075 行 (巨函数 30 参签名瘦身为 9 参 runEmbeddedScriptNodeLifecycle, 22 个 probe 选择器参数全是编译期常量); payload.cpp 5409→733; jni.cpp 4020→1366; validation.cpp (1568 行) 与 embedded_probe_* 四对文件整体删除; probe JS 源码 (sources.cpp 前 5.2k 行) 删除, 内嵌运行时 JS 构建器 (~43k 行, 真资产) 未动。C++ 总量 (不含 Node 头) ~97.7k→~51k 行, 扣除 JS 构建器后桥接核心 ~7.6k 行。验证: 全 ABI assembleDebug + JVM 单测 + 模拟器冒烟 (SimpleRun 3/3, StreamingOutput 1/1, CooperativeCancel 1/1)。
- [x] M4.2 删除/归档 capability-truth、runtime-ownership、runtime-kit、conformance gradle (共 ~7k 行 gradle+js), 保留 runtime-build (libnode 构建脚本是真资产)。— 2026-08-18 完成: 删除 4 个 app 级验证 gradle (conformance 3271 行 / capability-truth 141 / runtime-ownership 52) 与配套 js 验证器 (capabilities/ownership/runtime-kit/resolver/project 五目录 ~5.6k 行)、根级 node-plugin-tools.gradle.kts; runtime-kit gradle 瘦身为仅生成 4 个 BuildConfig 字段 (getBrokerInfo/runtimeInfo 回显在用, 187→33 行), 其发布/验证任务链整体删除。共 -13.3k 行。保留: runtime-build (libnode 构建真资产)、ownership/evidence (历史证据存档, Roadmap 头部声明)、node-plugin-examples.gradle.kts (verifyNodePluginExamples 是 M3.4 样例门禁)、nodejs-api publication 手动任务 (M1.2 决策)。验证: assembleDebug + verifyNodePluginExamples + 单测全绿; :app:check 的 lintAnalyzeDebug 崩溃经基线比对确认是 AGP 9.0.1 既有 bug, 与本项无关。
- [x] M4.3 conformance androidTest 收敛为 <10 个冒烟用例; 删除已无对应功能的用例。— 2026-08-18 完成: NodeRuntimePluginAndroidConformanceTest 24 用例删 19 留 5 (保留: x3d_01 runtimeInfo/预热进程、x3d_02 CJS+ESM 端到端、x3d_04 TSX 可读失败、x3e_05 corpus v2 七项兼容、x3f_13 provider v2 加密物化 — 每个守一条独立用户可见能力; 删除的 19 个是 TS 变体细分/工作区边界攻防/corpus v1/zlib/crypto 专项等已被冒烟组或单测覆盖的细粒度用例)。文件 2893→1696 行 (死 helper 与 corpus v1 常量迭代清理), corpus v1 资产目录删除。M2 起新增的 7 个冒烟类不动。验证: 模拟器全量 androidTest 12/12 (conformance 5 + 冒烟 7)。
- [x] M4.4 `NodeJsRuntimePluginService` 拆分瘦身 (2.3k 行 → 目标 <800 行)。— 2026-08-18 完成: 2667→693 行, 纯搬家零行为变化。四个同包协作类: NodePluginPayloads (820, 无状态 payload/bundle/binder helpers + procfs 诊断 + bridge 派发, 静态导入使调用点零改动)、NodeRuntimeModuleInjector (516, 运行时模块注入 + RuntimeModuleInjection/BridgeLimitPolicy, 持 Context)、NodePluginScriptExecution (419, runScriptActive 方法对象化)、NodePluginBundles (392, runtimeInfo/prewarm/result/failure 组装)。测试引用的静态成员 (prepareTypeScript* 系 / 契约版本 helpers / shouldCommitWorkspaceAfterFailure / Utf8StreamDecoder / procStatusLongValue 代理) 留在服务上; 死方法 normalizeWorkingDirectory 删除。验证: 单测 + 模拟器全量 androidTest 12/12 + arm64 真机冒烟 (裸跑+流式) 全绿。
- [x] M4.5 文档一页化: README 里写清 "怎么装 / 怎么跑 / 出错了看哪里"。— 2026-08-18 完成: 走既有生成体系 (template_readme.md + 10 语言 lang_*.json + generate_markdown.py) 新增 "快速上手" 一节, 三条各答一问: 装 (Releases 按 ABI 下载或本地 assembleDebug + 插件中心启用)、跑 (`"nodejs";` 首行 + 桌面 Node 写法 + 流式输出/可停止)、错 (控制台 JS 栈 + 一行错误码, 深挖用 `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin`, API 面查 docs/HOST-API.md)。features 里 M4.2 已删的 "Node 解析诊断工具和运行时构建计划校验工具" 描述同步改为 HOST-API.md 指引。全部 10 语言 README 重新生成。

### M5 — 宿主↔插件职责归位 ✅ (2026-08-21 增补, 同日完成)

目标: 宿主与插件对 Node.js 的职责边界清晰可述, 双份/错位代码归位删除, 新人按仓库就能读懂"谁管什么"。

**职责共识 (归位基准, 2026-08-21 双仓实测勘察)**:

- **插件 = Node.js 运行时唯一归属**: libnode + C++ 桥 + 内嵌 JS 运行时 (require 链 / 模块解析 / TS 剥离 / fs 策略 / 能力预检 JS 侧) + 运行时服务与执行队列 + nodejs-api 契约权威源 + sample 真值 + HOST-API.md (面向脚本作者的派生视图)。
- **宿主 = 能力提供方与桥客户端**: 引擎路由与插件发现绑定 (~2.3k 行) + 能力派发与宿主侧预检 (~13.2k 行, 直接操作宿主进程内 Activity/无障碍/剪贴板, 实现必须留宿主) + 工作区打包传输与模块源 provider (~2.3k 行) + 面向用户文档 (docs/nodejs 顶层 .md 与 types/) + sample/nodejs 副本。
- **插件→宿主方向为空集**: 插件 Java 14 文件 9,882 行零宿主进程内组件引用; `org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge` 的宿主包名是 JNI 符号约束 (改包名断 native 绑定), 非职责错位。宿主→插件方向以 **删除宿主重复实现** 为主 (插件已持权威实现), 仅个别工具/测试逐项判定回流。
- 宿主现存 node 生产 Kotlin 63 文件 58,619 行, 其中内嵌运行时层 **33,954 行"有头无身"** (宿主无 cpp/ 无 libnode.so, 仅 JNI 声明层; probe natives 已在 M4.1 从插件 .so 删除); androidTest 134 文件 74,866 行; node gradle 28 脚本 25,686 行 (几乎全手动); 阶段报告 520 份 ≈5.8M。

**红线 (每项执行前重读)**:
1. 宿主 `.changelog/lang_zh-Hans.json` 有用户未提交改动, 全程不碰、不混入提交。
2. `sample/nodejs` (双仓 358 文件字节级一致) 与宿主 `docs/nodejs` 顶层 .md + `types/` 是既有"保留"决策 — 用户可见资源, 不按重复文件删; 漂移靠 `verifyNodePluginExampleMirror` (宿主 `app/node-examples.gradle.kts:387`) 守。
3. 涉插件路径的宿主 gradle 任务必须显式传 `-Pautojs.nodejs.plugin.root=D:\idea-projects\AutoJs6-Plugin-NodeJs-Runtime` (两仓非兄弟目录, 默认探测必失败)。
4. 插件仓当前有 fs 放开未提交改动 (`node_bridge_sources.cpp` + `AndroidManifest.xml`), M5 宿主侧工作与其互不混合。
5. 新增守卫一律手动任务, 不挂默认构建链 (延续 M0.3 原则); 不新增哈希锁。

**计划执行顺序**: M5.1 (防继续漂移) → M5.2/M5.3/M5.4 (零风险与低风险清理, 可并行/穿插) → M5.5 (结构解耦) → M5.6 (最大删除面) → M5.7 (打包链退役) → M5.8 (测试收敛, 依赖 M5.6) → M5.9 (收尾)。每项独立提交, 完成标准 = 该项 Check 全绿。实际执行时 M5.8 提前到 M5.6 之前, 先移除依赖内嵌类的测试再由编译器驱动删除生产层; 其余顺序不变。

- [x] **M5.1 契约单一权威源 (nodejs-api 漂移收口)**。— 2026-08-21 完成并提交 (`d2b9cb156`): 宿主 3 文件按插件版逐字节同步 (LF→CRLF, 含新增 src/test 契约单测 4/4); 宿主 provider 钉 v1 = 新增 `NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_PROVIDER_CONTRACT_VERSION = 1` (带"为何是 1"注释), `NodeJsEncryptedModuleSourceProvider` 5 处 + androidTest 3 处改引它; 被 v2 契约移除的 `KEY_MODULE_SOURCE_PROVIDER_PROBE_MAX_COUNT` 降为宿主本地诊断键; `NodeJsPluginActions.INFO` 改字面量后宿主模块顺带解除对 common-plugin-api 的依赖; 新增手动守卫 `:plugin-api:nodejs-api:verifyNodeJsApiMirror` (行尾归一化逐文件比对, 10 文件验证通过)。Check 实绩: 双仓 diff 空 ✅; 加密模块用例 `pluginRuntimeDecryptsEncryptedModuleThroughHostV1Provider` 真机绿 ✅ (一次性 v2→v1 降级协商闭环保持); 桥类 6 用例 4 绿 2 失败, **该 2 失败经 stash 基线对照在未含改动的 HEAD 上逐字相同** (automation-API 的剪贴板回读与 swipe 的 PERMISSION_DENIED, 属设备环境既有问题, 非本项引入)。执行中修复的发现: 此前 6 用例"全绿"实为全部 Assume 跳过 — 设备装的插件 v1.1.3 (契约 v1) 被要求 v2 的宿主静默拒绝, probe 异常被 runCatching 吞掉; 换装当前源码构建的插件 v1.2.0 后用例才真实执行。教训: 跑桥用例前先核对插件契约版本, 且 `OK (N tests)` 亚秒完成 = 全跳过。事实: 宿主 `plugin-api/nodejs-api` (settings.gradle.kts:102 挂载, app/build.gradle.kts:627 project 依赖) 与插件副本已实质漂移 — `MODULE_SOURCE_PROVIDER_CONTRACT_VERSION` 宿主=1/插件=2; 插件多 MIN/MAX 常量 + `supports*` helpers + operation/status 键; 宿主多 `KEY_MODULE_SOURCE_PROVIDER_PROBE_MAX_COUNT`; `NodeJsPluginActions.INFO` 实现不同 (宿主引 common-plugin-api, 插件硬编码字面量)。动作: 以插件仓为权威, 宿主副本逐文件同步 (CRLF 归一化比对)。**陷阱**: 宿主 provider 的广告版本不得随共享常量升到 2 — 在宿主 provider 代码里显式钉 v1 (插件 v1..v2 宽容层承接, M1.2 决策), 除非同步实现 v2 操作语义 (materialize_missing_plaintext 等)。加一个双仓 nodejs-api src 内容比对的手动任务。Check: 双仓 `diff -r --strip-trailing-cr` 为空; 宿主 `NodeRuntimePluginBridgeInstrumentationTest` 全用例绿; 真机加密模块用例 `pluginRuntimeDecryptsEncryptedModuleThroughHostV1Provider` 绿。
- [x] **M5.2 能力声明收口与三方清单守卫** (原阶段1)。事实: 能力名三处登记且计数已互不相等 (= 漂移既成事实) — 宿主 `NodeBridgePermissionManifest.kt` 48 条 (:76-124, 常量 :14-74) / 插件 `NodeBridgePermissionManifest.java` 57 常量 / 插件 `releases/nodejs-capability-catalog/1.2.0` 116 条; 宿主 `NodeBridgeProtocol.kt` 另散落 21 处点分能力字面量; 宿主对未知能力名只 warning 不阻断 (声明层有松耦合空间)。动作: ① 宿主 protocol 层 21 处字面量收敛到 manifest 常量; ② 建三方比对手动任务 (宿主 manifest ↔ 插件 manifest ↔ catalog, 差异出报告, 每条差异要么修复要么白名单注明理由); ③ 双侧预检是刻意设计 (HOST-API.md 第八节), **不合并**; ④ 新增能力的登记顺序 (插件 cpp 声明 → 插件 manifest → 宿主 manifest → catalog) 写进 HOST-API.md。Check: 比对任务报告三方一致或全差异有注明; grep 宿主 node 生产代码点分能力字面量仅剩 manifest 定义处; 真机 accessibility.gesture 用例回归。
- [x] **M5.3 阶段产物清理** (原阶段2, 零风险先行)。事实: 宿主 `docs/nodejs` 760 文件 8.9M, 其中 `reports/` 377 份 + `phase{13,14,15,16}/` 143 份 = 520 份阶段报告 (≈5.8M); `tools/nodejs` 268 文件 5.1M, 其中 phase*/r6/differential 等阶段脚本 232 份; 宿主 `tools/nodejs/runtime-build` (7 文件, 含 libnode.exports.map) 与插件仓 runtime-build 双份。动作: 删 520 份报告与阶段工具脚本; runtime-build 先 diff — 插件侧为权威, 宿主侧若有较新内容先回流插件再删宿主份; `design/` 48、`releases/` 24、`schemas/` 8、`dual-engine-compat/` 18 等逐目录判定 (默认删, 被保留的顶层 .md 引用者留); 保留顶层 31 个 .md + `types/` 61 文件。Check: 被删路径在宿主 gradle 与保留文档中零引用 (grep); `:app:assembleAppDebug` 通过。
- [x] **M5.4 门禁 gradle 收敛** (原阶段3)。事实: 28 个 node gradle 脚本 25,686 行; 挂载点仅 app/build.gradle.kts :411/:1305/:1307 三处 apply, `node-gradle-scripts.gradle.kts` 聚合其余 24 个; 全部 verifyNode* 均为手动任务; **唯一默认构建链任务** = `prepareNodeJsRuntimeSlotJniLibs` → preBuild (`node-runtime-config.gradle.kts:467, :489-497`)。动作: 删 phase10~16 gates、v1-1 alpha/beta/rc gates、r6-gate、`embedded-node-packaging-preflight` (4,545 行)、node-doctor-report、native-node-deps-report 等与内嵌体系绑定的 ~20 个脚本; 保留 `node-runtime-config` (M5.7 再改造)、`node-examples` (镜像守卫)、`node-build-tasks` (逐任务判定); `prepareNodeJsRuntimeSlotJniLibs` 本项**不动**。Check: apply 链无死引用; `:app:assembleAppDebug` + `verifyNodePluginExampleMirror` (显式传 plugin.root) 通过。
- [x] **M5.5 引擎路由解耦** (结构前置)。事实: `NodeJavaScriptEngineFactory.kt:20-22` 里 PLUGIN / PLUGIN_OPTIONAL backend 复用 `NativeNodeEmbeddedJavaScriptEngine` (3,368 行) — 插件路径包在内嵌引擎壳里, 是内嵌层不可删的结构性耦合结; `NodeExampleProjectInstrumentationTest` 经 `NodeEmbeddedTestHarness` (136 行) 驱动而非直接走桥。动作: 新建独立 `NodePluginJavaScriptEngine`, 只依赖桥客户端组 (NodeJsRuntimePluginHost/Bridge/Loader) 与传输组; PLUGIN 系 backend 切换到它; NodeEmbeddedTestHarness 改为桥驱动。Check: 宿主编辑器 `"nodejs";` 脚本真机经插件运行 (裸跑/流式/取消/项目脚本各抽 1); (i) 组 6 个桥测试类 + `NodeExampleProjectInstrumentationTest` 7/7 绿。
- [x] **M5.6 内嵌运行时 Kotlin 删除** (原阶段5, 最大删除面)。事实: 14 文件 33,954 行 — `NativeNodeEmbeddedRuntimeBridge.kt` 19,041 / `NativeNodeEmbeddedScriptService.kt` 5,805 / `NativeNodeEmbeddedJavaScriptEngine.kt` 3,368 / `NodeEmbeddedProcessSessionManager.kt` 1,774 / `NativeNodeEmbeddedProjectRunner.kt` 1,426 / `NativeNodeEmbeddedProbeService.kt` 1,201 / 其余 8 个 ≈1.3k, 外加 `INodeEmbeddedProcessControl.aidl`; 宿主无 native 实现可链接, 属"有头无身"死层。附带逐项判定: `NodeTypeScriptStripper.kt` 386 行 (插件 742 行版为权威; 查宿主 provider 明文物化是否引用 → 用则留, 不用则删); `NodeDoctorReportGenerator.kt` 527 行 (诊断对象是内嵌运行时, 同删或改造为插件诊断); `NodeRuntimeBackend` 枚举 EMBEDDED/NATIVE 态退役, PLUGIN 为 Node 默认 (rhinoOnly / NodeDisabled 路径保持); `nodeFull`/`nodeMini` releaseFlavor 档位 (是 gradle property 非 flavor, 不控制任何 sourceSet) 映射到 plugin 行为保留档位名、或直接退役 — 执行时二选一并记录。动作: 沿用 M4.1 手法 (先全仓 grep 引用面 → 内容锚定脚本删除 → 编译器驱动清残留)。Check: 全仓 `NativeNodeEmbedded`/`NodeEmbedded` 生产代码零引用; `:app:testDebugUnitTest` 绿 (node 相关 6 个 JVM 单测 233 行按存续调整); `:app:assembleAppDebug` 全 ABI; 真机 (i) 组回归。
- [x] **M5.7 native 打包链退役**。事实: `prepareNodeJsRuntimeSlotJniLibs` (`node-runtime-config.gradle.kts:467`) 是唯一挂 preBuild 的 node 任务; `isNodeJsRuntimePackagedInHost` (:422) 为真时从插件仓拷 libnode.so 进宿主 APK。动作: M5.6 后宿主永不打包 libnode → 该任务与 runtimeSlot / pluginProjectDir 探测链退役; `node-runtime-config` 瘦身为 backend BuildConfig 字段 (plugin/rhino 二态); jniLibs 只剩 4 个 libc++_shared.so 走常规打包。Check: `:app:assembleAppDebug` 产物 APK 无 libnode.so, 前后体积对比记录在案; 安装后 `"nodejs";` 脚本仍经插件运行。
- [x] **M5.8 androidTest 收敛** (原阶段4, 依赖 M5.6)。事实: 134 文件 74,866 行 — (i) 桥驱动真插件 6 文件 3,785 行 (**保留**, 宿主守护链); (ii) 内嵌内部行为 31 文件 67,932 行 (**删**, 含单文件 17,530 行的 `EmbeddedNodePackagedApkSmokeTest.kt`); (iii) 旧 conformance/probe/corpus 35 文件 16,391 行 (**删**); 其余 72 文件 25,749 行逐类判定 — 测宿主派发/能力层的留 (必要时改桥驱动), 测内嵌行为的删; 插件缺失的高价值覆盖可逐用例回流插件仓, 默认不迁 (延续 M4.3 收敛哲学); fixture assets 252 个随用例清理; JVM 单测 6 文件 233 行按 M5.6 存续调整。Check: 宿主 androidTest 全量编译通过; (i) 组 + 保留的能力桥用例真机绿; 插件仓 12 个 androidTest 不受影响。
- [x] **M5.9 收尾: 盘点与文档对齐**。动作: 前后行数盘点落库 (预期宿主 node 生产 Kotlin ~58.6k → ~25k, gradle ~25.7k → <2k, androidTest 只留有效面); 保留的 docs/nodejs 顶层 .md 中指向已删体系的章节更新或标注过时 (TESTING.md 235K / CI_GATES.md 108K / SECURITY_MODEL.md 109K 大概率大面积失效); HOST-API.md 增补 M5.2 的能力登记顺序; 本 Roadmap 勾选并记录执行偏差。Check: 保留文档无死链死引用 (抽查); 宿主 README / 设置页涉 Node 描述与 plugin-only 现实一致。

### M5 执行实绩 (2026-08-21)

| 项 | 宿主提交 | 插件提交 | 结果 |
|---|---|---|---|
| M5.1 | `d2b9cb156` | — | 契约镜像 10 文件一致, 宿主 provider 显式钉 v1 |
| M5.2 | `c3988836b` | `6c9920a`, `7730822` | 宿主/插件 manifest 各 48 条; catalog 47 条, 仅 `accessibility.gesture` 一条带原因历史例外 |
| M5.3 | `07f947efe` | — | 520 份阶段报告及 232 份阶段工具脚本退役 |
| M5.4 | `2f54cbd67` | — | Node Gradle 脚本收敛为 4 文件, 保留任务均为手动任务 |
| M5.5 | `5ef2c6a56` | — | 独立 `NodePluginJavaScriptEngine` 与通用 request/result 落地 |
| M5.8 | `b98f1b84c` | — | 先删内嵌/probe 测试面, 保留桥与宿主派发测试 |
| M5.6 | `5240ce2d8` | — | 宿主内嵌 Kotlin/JNI 声明、服务、进程池、probe 与 AIDL 清零 |
| M5.7 | `23b1b7fd6` | — | runtime slot/native loader/preBuild 拷贝链退役; nodeFull/nodeMini 明确拒绝 |
| M5.9 | `3e4f2c548` | 本文件收尾提交 | 文档、诊断、测试命名、指标与最终验证收口 |

统一 `wc -l` 口径的前后盘点:

| 面 | M5 前 | M5 后 | 变化 |
|---|---:|---:|---:|
| 宿主 Node 命名生产源码 | 77 文件 / 60,041 行 | 61 文件 / 25,959 行 | -34,082 行 (-56.8%) |
| Node Gradle/约定插件 | 27 文件 / 20,309 行 | 4 文件 / 1,624 行 | -18,685 行 (-92.0%) |
| Node androidTest | 134 文件 / 74,866 行 | 37 文件 / 16,364 行 | -58,502 行 (-78.1%) |
| Node JVM test | 6 文件 / 233 行 | 4 文件 / 157 行 | -76 行 |

终态证据:

- 宿主无 `app/src/main/cpp`, 无 `NativeNodeEmbedded*`/`NodeEmbedded*` 生产类引用; `app/src/main/jniLibs` 仅 4 ABI 的 `libc++_shared.so`。最终 universal APK 含 12 个常规 `.so`, Node `.so` = 0。
- M5.7 同一构建条件的 APK 前后均为 43,663,670 bytes, delta = 0 (插件模式本已不打包 libnode); M5.9 全量重组产物为 46,804,197 bytes, SHA-256 `9DC6DDCC756FBC3C4948F28556D6431843C25FDB687F810BEA4B5FFAFB6D9A5F`, 仍为 Node `.so` = 0。
- `docs/nodejs` 终态为顶层 32 文件 / 298,464 bytes + `types/` 61 文件 / 283,299 bytes; 21 份当前指南本地链接审计零断链, 11 份 R2–R6/旧路线图类材料均标记为历史快照。
- `sample/nodejs` 双仓各 358 文件, `verifyNodePluginExampleMirror` 字节比对通过; `verifyNodeExamples`、11 个 project-wizard 模板及类型声明守卫通过。
- 离线链: nodejs-api 镜像、能力三方对齐、样例镜像、主源码、androidTest、JVM 单测、universal APK 全绿。
- 真机 `968e9f18`, 插件 v1.2.0: `NodeRuntimePluginBridgeInstrumentationTest` 6/6、`NodeR6EngineSwitchInstrumentationTest` 2/2、`NodeExampleProjectInstrumentationTest` 7/7、`NodeR6RequiredPluginSecurityInstrumentationTest` 1/1, 合计 16/16 真实执行通过。
- 有意保留的兼容名仅限插件已发布的 `work_manager` wire token `backend="embedded"` 与 `embedded_script.*` 诊断键; 它们不表示宿主持有运行时。

**终态 (已达成)**: 宿主 node 面 = 桥客户端 + 派发能力层 + 传输 provider + 文档样例, 25,959 行; 内嵌运行时 0 行; 契约/能力/样例三类跨仓一致性各有手动守卫可查。验证链恒为: 宿主 JVM 单测 + `:app:assembleAppDebug` + 真插件桥冒烟; 插件侧未改动时无需重跑插件链。

### 2026-08-25 二次勘察 (M6~M10 制定依据)

M0~M5 完成后对全仓四面 (C++ 桥 / Java 服务层 / 样例与发布链 / 提交前沿) 的勘察结论:

**核心能力已站稳**: 真实 Node 24.5.0 内嵌、CJS/ESM/npm 纯 JS 包、流式输出、协作取消、串行队列、长驻脚本、网络默认开、live bridge 21+ 模块、fs 桌面级放开 (`d5703d1`)。日常自动化脚本场景基本闭环。

**二次勘察时的三条战线** (历史基线; M6/M7 执行后已修正):

1. **TypeScript 的“生产端缺位”判断已被跨仓核对纠正**: 单看 Node Runtime 仓时, `8e08b3b` 的 fail-closed 与错误文案确实像一条断链; 但宿主仓已在 `0938deed2`→`ebbf1b8d2` 系列提交中实现完整消费链, 相邻仓 `AutoJs6-Plugin-TypeScript-Engine` 也已形成 v0.6.0 / TypeScript 6.0.3 正式候选。M7.1~M7.3 与 M7.5 已据此验收, 仅 legacy stripping 的发布观察期仍开放。
2. **fs 放开收尾已由 M6.2/M6.4 完成**: API 37 真服务进程覆盖 workspace 外写读删与 `/proc` `/sys` `/dev` 负例, README/HOST-API/catalog 已同步; `filesystemRoots` 明确裁定为兼容诊断元数据。
3. **M5 收官欠账已由 M6.1/M6.5 完成**: catalog/runtime-kit 1.3.0、48=48=48 零例外、十语言 1.1.0/1.2.0 CHANGELOG、构建噪音与悬空文档均已收账。

**服务层三个实缺口** (2026-08-25 勘察时, 已由 M8.1~M8.3 全部收账):

1. `KEY_TIMEOUT_MS` 名不副实 — 只作队列等待预算, 脚本执行本身无 wall-clock 超时; 死循环脚本永久占据唯一执行槽 → 3 个排队者依次 `WAIT_TIMEOUT` → 后续请求全部 `QUEUE_FULL`, 只能人工 `cancelScript`。宿主 5min deadline 到期也不会停掉插件侧脚本。
2. 取消兜底升级路径 (3s 宽限 → gate 永久关门 → `Process.killProcess`) 是最危险的代码路径却零自动化证据。
3. `KEY_EXECUTION_MODE` / `KEY_RUNTIME_ADAPTER` 是契约死键 (main 源码零读取), checkpoint 门禁被 engine-info 旁路架空, 裸请求路径恒 false。

**能力面缺口 (按用户价值排序)**: `dgram`/UDP 完全缺失 (无 facade, 设备发现/组播类脚本不可行); npm 生态第二批未收账 (axios/express 自 M2.4 延后至今; ESM 自实现层 `realEsmCorpus: partial_required`); `package_manager` registry 设备端下载 `deferred`; inspector 调试面三重封锁 (`kNoCreateInspector`, devtools `deferred`); V8 启动快照禁用 (`disabled-until-snapshot-batch`); `trace_events` 隐式缺失 (不在任何名单, 报错体验不一致); WASI `deferred` (2 个样例 design-gated)。

**发布链**: Node 24.17.0 (lts-security, 2026-06 发布) 晋级被上游 Android 产物阻塞 (`node2417Promotion: deferred`, 5 项前置全未动); runtime-kit 1.2.0 自报 `releaseReady: false` (blockedReasons 全指向 24.17 与容器 digest 未钉); `runtime-build.lock.json` 含本机绝对路径且其 sha256 已入 kit 信任链。

**已知但不立项 (逐异常修复原则, 未造成用户可见问题)**: `processHandler` 挂主线程 Looper; `commitWorkspaceQuietly` 异常路径双调 (靠 AtomicBoolean 兜住); broker/provider 关闭阶段 RemoteException 静默; prewarm 失败无退避; 未知能力名只告警不阻断; `node_bridge_sources.cpp` 单文件 43.7k 行 (JS 以 raw string 维护); desktop-parity-suite 二级清单不受门禁保护; `unsupported` 样例状态零使用; resource 指标只采不控。异常真实发生时再逐项修复。

### M6~M10 通用约定 (2026-08-25 增补, 每项执行前重读)

1. **验证一律本地/离线优先** (网络环境易触发 Cloudflare 502/524/529, 尤其 524): gradle 一律带 `--offline` (依赖已在本地缓存); C++ 改动先用 NDK clang `-fsyntax-only` 离线语法校验 (手法见 fs 放开时的实践: `clang++.exe -fsyntax-only -std=c++20 --target=aarch64-linux-android24 -I. -Inode-v24.5.0/include/node ...`, 宏列表按现状调整) 再进构建; androidTest 走本地模拟器 (x86_64) / USB 真机, 不依赖外网。全部 M6~M10 中**仅两处**允许联网且单独标注: M9.2 corpus 制备 (开发机一次性, 可走镜像源)、M10.1 上游版本复查 (手动)。
2. `sample/nodejs` (含 `examples.json`) 仅在插件仓维护, 任何改动由插件侧 `verifyNodePluginExamples` 校验; 宿主不再保留样例镜像。
3. README/CHANGELOG 只改 `.readme/` 与 `.changelog/` 的 lang JSON 及模板, 经 `.python/generate_markdown.py` 生成, 不直接编辑产物 .md; 生成时被顺带重写但无实质变化的文件用 git checkout 还原。
4. 契约 (nodejs-api) 改动延续 M0.2 宽容原则: 新键缺失有默认、旧键不破坏、AIDL 事务号零改动优先; 插件仓作为权威源, 由 `verifyNodeHostApiMirror` 反向校验宿主编译镜像。
5. 延续既有红线: 不新增 probe/哈希锁类基础设施; 新守卫一律手动任务不挂默认构建链; 宿主仓用户未提交改动不碰。

**计划执行顺序**: M6 (全离线, 最快清欠账) → M7 与 M8 可并行 (互无依赖) → M9 → M10 按条件成熟推进。每项独立提交, 完成标准 = 该项 Check 全绿。

### M6 — 状态对齐与欠账清零 (全离线)

目标: 仓库自述与代码现实一致 — fs 放开 / TS fail-closed / M5 收官三条战线的文档、样例、发布物欠账全部清零。

- [x] **M6.1 仓库卫生小项打包**: ① `version.properties` 构建噪音 (BUILD 56→57) 随本项提交收编; ② `PADDLE_OCR_*` 三键确认零引用后删除 (宿主仓继承残留); ③ build-logic 插件 ID `org.autojs.build.local-arr-register-convention` 拼写修正为 `local-aar-...` (当前零 apply 引用, 零风险); ④ `tools/nodejs/runtime-build/README.md:23` 悬空任务引用改指 `build-node-runtime.sh` / `.ps1` 的 plan 档 (`verify-runtime-build-plan.js` 本体仍在)。Check: 全仓 grep 悬空任务名与 PADDLE_OCR 零命中; `:app:assembleDebug --offline` 通过。— 2026-08-25 完成: BUILD 57 收编、三项 Paddle 残留删除、插件 ID 与 runtime-build 文档修正; PowerShell plan 输出 Node 24.17 目标/24.5 当前版本与 3 ABI, 离线 assembleDebug 67 tasks 通过。后续 M8 验证构建按项目既有自动递增规则将当前工作树推进至 BUILD 58, 无需回退。
- [x] **M6.2 fs 放开收尾**: ① 新增 `UnrestrictedFsSmokeTest` — workspace 外路径 (如 `/sdcard/Download`) 写→读回断言, 模拟器经 `adb shell appops set ... MANAGE_EXTERNAL_STORAGE allow` 自动授权; ② `/proc` `/sys` `/dev` 拦截保持的负例断言 (放开模式下唯一硬边界); ③ `filesystemRoots` 元数据裁定 (采集了但零消费): 删除解析或注明"仅诊断元数据", 二选一记录于此。Check: 新冒烟用例模拟器绿; `:app:testDebugUnitTest --offline` 全绿。— 2026-08-25 完成: 保留 `filesystemRoots` 作为兼容的纯诊断元数据, 明确其不授予/扩大/收窄 fs 权限; API 37 x86_64 模拟器真实服务进程完成 `/sdcard/Download` 写读删及三敏感根负例 1/1, JVM 单测与 androidTest 编译全绿。
- [x] **M6.3 样例状态复核 (双仓同步)**: ① `disabled-features-demo` 期望输出与现状脱节 (child_process/worker_threads 已默认启用, 样例仍断言被禁) — 改造为显式传 false 的开关演示或更新期望, 裁定记录; ② `sandboxed-script` stable + design-gated 标注矛盾裁定 (其余 12 条 design-gated 全是 partial/disabled); ③ 5 条 TS 标签样例按 fail-closed 现状重标 (其中 4 条为 raw TS, 编译器缺位期间实际不可跑, M7 闭环后再升回); ④ 3 条 fs 面 partial 样例 (filehandle-advanced/fs-watch/compile-cache) 按放开后现状复核; ⑤ `examples.json` 为全部非 stable 条目补一行 `reason` 字段 (机器可读的"为什么不能跑", 现散落三处), `verifyNodePluginExamples` 加存在性校验 (轻量, 仅查非 stable 有 reason)。Check: `verifyNodePluginExamples --offline` 通过; 宿主 `verifyNodePluginExampleMirror` 通过 (--offline + plugin.root)。— 2026-08-25 完成: disabled-features-demo 改为验证当前默认开放面, sandboxed-script 转 disabled, 4 个 raw TS 转 disabled、typescript-smoke 保持 partial, filehandle-advanced/fs-watch 升 stable、compile-cache 保持 partial; M6 阶段分布 stable 23 / partial 23 / disabled 11, 34 个非 stable reason 全覆盖; M7.5 收账后当前分布为 stable 28 / partial 22 / disabled 7。插件门禁与宿主 358 文件字节镜像门禁均通过。
- [x] **M6.4 用户文档同步 (10 语言生成体系)**: ① README features 与快速上手补 fs 权限模式 (插件独立 uid、装后需手动授予"所有文件访问"、不授予时报 EACCES 属预期) 与 TS 现状 (需编译器, 迁移期 legacy 开关); ② CHANGELOG 补 1.1.0 与 1.2.0 条目 (M1~M5 用户可见变更: 流式输出/协作取消/串行队列/npm 实测/网络默认开/swipe/契约收口/fs 放开/TS fail-closed); ③ HOST-API.md 增补 "fs 访问模式" 与 "TypeScript 编译要求" 两节。Check: `generate_markdown.py` 全 10 语言生成无报错; 抽查 zh-Hans 与 en 两份内容正确; 无关文件 git checkout 还原后工作区仅含预期改动。— 2026-08-25 完成: 10 份语言源补齐 fs/TS 能力与快速上手, 10 语言 CHANGELOG 补发 v1.1.0/v1.2.0, HOST-API 明确 Android 权限边界、敏感根、诊断元数据与宿主编译责任; 生成器全量成功并抽查中英文产物。
- [x] **M6.5 catalog 1.3.0 发布 (本地发布物, 无联网)**: ① `accessibility.gesture` 补入 `bridge.permissionCapabilities`, 删除守卫历史例外; ② features 增补 fs 访问模式条目、修订 TypeScript 策略条目; ③ runtime-kit 同步引用新 catalog, 顺带清理 `runtime-build.lock.json` 的 `androidFork.localPath` 本机绝对路径 — **注意 lock 的 sha256 被 kit 的 buildProvenance 引用, 两文件必须同一提交联动更新**; ④ 终态三方对齐: 插件 manifest 48 = 宿主 manifest 48 = catalog 48。Check: 宿主 `:app:verifyNodeCapabilityManifestAlignment --offline` 三方一致且零白名单例外; 插件 `:app:assembleDebug --offline` (runtime-kit BuildConfig 回显) 通过。— 2026-08-25 完成: 发布 catalog/runtime-kit 1.3.0, 补 gesture/swipe operation 与 48th capability、fs/TS 策略和 TS 新错误码; 清除本机路径并联动更新三段 SHA-256, hashed JSON 统一 LF 以跨平台复现; 宿主守卫为 48=48=48、documentedDifferences=0, 插件 APK 内两份资产哈希与 release lock 完全一致, assembleDebug 通过并回显 kit 1.3.0。

### M7 — TypeScript 战线闭环 (4/5; M7.4 等待公开版本观察期)

目标: TS 从"默认不可用"回到一条端到端用户能力 — 编辑器 .ts 脚本一键运行, 报错栈指向 .ts 原始行号。涉宿主仓, 红线照旧。

- [x] **M7.1 编译器落点决策** (一次性, 同 M1.2 模式): 候选 ① 独立 TypeScript Compiler 插件 APK (错误文案的既有方向, 架构最干净, 工程量最大); ② 宿主借插件 Node 自举编译 — typescript npm 预置为资产, 编译请求本身作为一次插件 runtime 执行 (跑 tsc API), 产物 js + source map 回宿主后再派发真正执行, 零新仓库 (建议先做一次可行性 spike: 插件跑 tsc 编译 hello.ts 的耗时与内存); ③ 插件内嵌编译 (与 8e08b3b "插件退出 TS 转译业务"方向冲突, 仅作对照)。决策与依据记录写入本文件。— 2026-08-25 完成并纠正勘察误判: 采用候选①, 但无需新建工程 — 相邻仓 `AutoJs6-Plugin-TypeScript-Engine` 已存在, 当前正式候选为 v0.6.0 / TypeScript 6.0.3, 使用官方完整 `Program` 编译而非 transpile-only; 宿主 `0938deed2` 起已有公开 Binder client。候选②会重复已完成的独立编译服务且把编译与执行生命周期重新耦合, 不再 spike; 候选③继续否决。
- [x] **M7.2 编译链路端到端**: .ts 入口 → 编译产物 → `typeScriptPrecompiledSnapshot` + `typeScriptPrecompiledSourceNames` 请求键 (3f1b3b1 已备好消费端) → 插件执行; 覆盖单文件、多文件项目、动态 import (.ts 说明符经快照映射, 含 missing/ambiguous 负例)。Check: 宿主模拟器/真机用例 — TS hello + 多文件项目 + 动态 import 三例全绿; 插件 conformance x3d_06/07 回归。— 2026-08-25 完成: 宿主 `0ae1ec30e`/`622bfddcc`/`ebbf1b8d2` 已分别交付 Node 预编译、多文件项目与封闭动态映射; TypeScript Engine Roadmap 保存 API 31/35/37 的聚焦通过证据。当前插件源码的 x3d_06/07 在 API 37 回归 2/2; 仓库原样的 CJS、ESM、project、packaged-dynamic 四例经宿主→Compiler 0.6.0→Node Runtime 在同一目录各连续执行两次, 8/8 PASS。实跑发现并修复宿主 workspace v2 把虚拟 compiler `moduleSources` 回写成 `main.cjs/main.mjs`、导致复跑冲突的问题: 仅运行前不存在的 module-source 覆盖被标为 ephemeral, 输出快照不再物化它们; 原有真实支持文件和脚本其他输出仍同步。新增策略单测 1/1, 主应用离线构建安装通过, 四个目录复跑后文件清单与原始输入完全一致。
- [x] **M7.3 栈帧回映**: 宿主组合编译器 source map 与插件已归一化的栈位置基线 (61f3a36/92f3be6), TS 脚本 throw 时控制台栈指向 .ts 文件与原始行号 (M1.5 "码+一句话"风格保持)。Check: 用例断言栈文本含 `.ts:` 与正确行号。— 2026-08-25 完成: 宿主 `TypeScriptSourceMapTest` 7/7 离线通过, 覆盖 entry/imported 模块与相对路径唯一映射; TypeScript Engine S4-1 的三设备证据精确回映 `lib/fail.ts:3:11` 与 `main.ts:2:5`, 生成的 `fail.js` 不泄漏。Node Runtime 的 `61f3a36`/`92f3be6` 继续作为生成行偏移与 imported CJS 帧归一化基线。
- [ ] **M7.4 legacy 剥离器退役**: 新链路稳定后删除 `NodeTypeScriptStripper` (~800 行) 及其单测; `KEY_LEGACY_TYPESCRIPT_STRIPPING_ENABLED` 按宽容原则处理 (保留常量 + 显式传入时拒绝并提示新链路, 或直接移除, 决策时定); conformance x3d_04/05 断言同步。Check: `:app:testDebugUnitTest --offline` 全绿; grep 零 stripper 生产引用。— 2026-08-25 状态裁定: 不提前删除。生产宿主已无任何 `true` 调用点, `.ts/.mts/.cts` 默认且唯一走 compiler, 但 TypeScript Engine S4-2/S6-2 明确冻结 v0.6.0 为迁移观察版本; 观察期必须从实际公开分发起覆盖一个完整发布周期, 本地开发时间不冒充发布观察。后继版本开发前若无兼容报告, 再删除兼容键、正则实现与专属测试并勾选本项。
- [x] **M7.5 样例收账 (双仓同步)**: 5 个 TypeScript-tag 样例 (其中 4 个为可执行 raw TS 项目) 接新链路升 stable, expected-output/项目元数据更新; packaged-typescript 走打包与动态 import 路径验证。Check: `verifyNodePluginExamples` + 宿主镜像守卫通过; 设备至少抽 2 例实跑绿。— 2026-08-25 完成: typescript-cjs/typescript-esm/typescript-project/packaged-typescript 与声明元数据 smoke 全部升 stable, README/project.json 明确需 Compiler 0.6.0+ 且 raw 直派仍 fail-closed; 57 例当前分布 stable 28 / partial 22 / disabled 7, 29 个非 stable reason 全覆盖。插件样例门禁与宿主 358 文件字节镜像门禁通过; API 37 对 4 个可执行样例做同目录双跑 8/8 PASS, packaged 例真实输出 `packaged:dynamic`, 且零编译产物泄漏。

### M8 — 执行可靠性补课 ✅ (2026-08-25 完成)

目标: 失控脚本不再需要人工干预; 契约键名实相符。

- [x] **M8.1 脚本执行 wall-clock 超时**: `KEY_TIMEOUT_MS` 从"仅队列等待预算"扩展为"等待+执行总预算" (或拆双键, 设计时定, 注意宿主现有传值语义不破坏): 超时触发既有 `requestCooperativeCancellation` → `node::Stop` 路径 (基础设施已完备, 只差 watchdog 触发器), 结果置 `KEY_TIMED_OUT=true` + 专用错误码一句话; **未指定 timeout 保持无限运行** (M2.5 长驻语义不变)。宿主侧配套: runBlocking deadline 放弃前先发 `cancelScript` (涉宿主仓)。Check: watchdog 单测; androidTest — 死循环脚本 + timeout 3s 得到超时错误且同 PID 复用下一脚本; `LongRunningLifecycleSmokeTest` 回归确认无 timeout 行为不变。— 2026-08-25 完成: 采用既有单键作为 Binder 入口起算的 queue + preparation + native execution 总预算, `<=0` 仍不限制脚本执行、无显式预算的队列仅保留原 10 分钟安全上限; 不改 contract version/AIDL/已发布 nodejs-api 1.2.0。执行租约由布尔位升级为 `ACTIVE/CANCELLED/TIMED_OUT/COMPLETED` 原子仲裁, 消除完成/人工取消/watchdog 的末端竞态, 队列 deadline 同时改为饱和加法。预算耗尽统一返回 `timedOut=true`、`ERR_AUTOJS6_SCRIPT_TIMEOUT` 与阶段诊断, active watchdog 先发 `node::Stop`, 3 秒未释放再沿既有进程重启兜底。宿主 `NodeJsRuntimePluginHost` 已先于本项具备 `timeout+5s` Binder 响应兜底及超时后有界 `cancelScript`, 无需重复实现; 当前宿主 main Kotlin 编译与 nodejs-api 镜像门禁通过。JVM gate/deadline 14/14; `while(true)` 3 秒超时 + 同 PID 后继复用在 API 28/36/37 模拟器与 3 台真机 6/6, 队列预算同矩阵 6/6; `LongRunningLifecycleSmokeTest` 与 `CooperativeCancelSmokeTest` 各 6/6, 证明无 timeout 长驻与人工取消语义未回归。
- [x] **M8.2 取消兜底升级路径最小回归**: 构造协作停止停不掉的夹具 (候选: `Atomics.wait` 阻塞主线程; 若实测 `while(true)` 已足够停不掉则用之并记录), 断言 3s 宽限后 `process_restart` 兜底生效、错误码可读、进程重启后可服务下一请求。Check: 新 androidTest 模拟器绿 (可标记慢用例, 不入高频冒烟组)。— 2026-08-25 完成: `Atomics.wait`、`execFileSync` 与无握手 FIFO 的首轮试验都会在真正进入不可中断调用前被 `node::Stop` 正常终止, 不能冒充兜底证据; 终态 `CancellationRestartFallbackSmokeTest` 使用私有 cache FIFO + ready 文件握手 + 500ms 稳定窗, 取消前断言 Binder 调用仍未完成, 从而可靠阻塞在同步 `readFileSync` 系统调用。六设备矩阵 (API 28/36/37 模拟器 + Android 9/12/15 三台真机) 6/6 观察到 3s 协作宽限后 runtime-only `Process.killProcess`, Binder death 不早于 2.75s, 重绑 PID 必须变化且后继脚本输出 42。进程被杀时底层 AIDL 无法再返回结果 Bundle, 因而可读错误由宿主负责: `NodeJsRuntimePluginHost` 把已派发事务的 transport loss 包为 `dispatchStarted=true`, `NodeJsRuntimePluginBridge` 归一化为 `failure_kind=execution_lost`、`fallback_safe=false`、`ERR_AUTOJS6_NODE_PLUGIN_EXECUTION_LOST`; 新增纯分类单测 2/2 离线通过。该慢用例保持聚焦执行, 不并入高频冒烟组。
- [x] **M8.3 契约死键裁定**: ① `KEY_EXECUTION_MODE` 接线到 lifecycle-config (替代 engine-info 旁路), 使 `interactive_long_running` 请求能真实开启 checkpoint 门 — `restartPolicy=never` 与"进程死亡不自动重启"决策不变, checkpoint 价值 = 长驻脚本主动存进度、重启后自读恢复; ② `KEY_RUNTIME_ADAPTER` 零消费 → 契约标 deprecated 或删除, 按宽容原则处理。Check: 契约单测更新 (宿主镜像同步); grep main 源码死键零残留; checkpoint 门在显式 executionMode 下可开的单测。— 2026-08-25 完成: lifecycle config 现在以 Bundle `KEY_EXECUTION_MODE` 为权威, 仅在缺键时兼容回退 `engine-info`, 两者均缺失才落 `one_shot`; 无 `engine-info` 的显式 `interactive_long_running` 请求推导 `interactive_session`, 而宿主 live checkpoint 派发仍独立执行 mode + launch-surface 授权。`KEY_RUNTIME_ADAPTER` 为保持 contract-v2 字面兼容而保留并标 `@Deprecated` no-op, 宿主请求模型、wire 写入与插件 conformance 伪填充全部删除; C++ `embedded_script.runtime_adapter.*` 仅是内部 adapter 诊断, 与废弃请求键无关。验证: lifecycle 纯策略单测 4/4; 两仓契约单测各 7/7, 10 文件镜像守卫通过; 精确 grep 显示宿主 main 对 runtimeAdapter 零建模/发送、插件 app main 已真实读取 executionMode; `ExecutionModeLifecycleSmokeTest` 在 API 28/36/37 模拟器与 Android 9/12/15 三真机 6/6, 断言 checkpoint=true、automaticRestart=false、restartPolicy=never; 宿主 main Kotlin 离线编译通过。

### M9 — 能力面第二批扩展 ✅ (2026-08-25 完成)

目标: 每项一条用户可见能力, 按需求频度排序。

- [x] **M9.1 dgram (UDP) 放开**: denylist 移除, 并入 `rawNodeNetworkModulesEnabled` 辖区 (M2.6 网络默认开的自然延伸, manifest 已有 INTERNET); 顺带把 `trace_events` 从隐式缺失改为显式 denylist (给清晰的 `ERR_AUTOJS6_BUILTIN_DISABLED`)。C++ 改动先 NDK clang `-fsyntax-only` 离线校验。Check: androidTest UDP 回环 echo (createSocket/bind/send/message) 模拟器绿; trace_events require 得到清晰错误码; 新样例 udp-discovery (双仓同步) 过门禁。— 2026-08-25 完成: `dgram`/`node:dgram` 并入默认开启的 raw-network builtin 路由, `module.isBuiltin`、`module.builtinModules`、`require.resolve`、profile 与禁用请求负例同步; 同轮把生态依赖实际需要的 `http2` 纳入相同网络边界, `trace_events` 则转为明确 denylist。`DgramUdpSmokeTest` 在 API 37 x86_64 模拟器完成 UDP4 loopback bind/send/message/reply, 并断言关闭 raw-network 时 dgram/http2 可读拒绝、trace_events 始终返回 `ERR_AUTOJS6_BUILTIN_DISABLED`; `udp-discovery` 样例已双仓镜像且过样例门禁。
- [x] **M9.2 npm 生态第二批**: axios + express (M2.4 明言延后, 收账) + 2~3 个 ESM-only 真实包 (顺带收 `realEsmCorpus: partial_required` 的账)。corpus 在开发机一次性 `npm install` 制备 (**唯一联网步骤, 可走 npmmirror 镜像避 Cloudflare**), 产物进 androidTest assets, 设备端离线实跑。Check: `NpmEcosystemSmokeTest` 扩展 — axios loopback 自请求、express 起服务自访问、ESM 包 import 断言, 全绿。— 2026-08-25 完成: 真实锁定的 npm-install corpus 从 10 包扩到 15 包, 新增 axios 1.19.0、express 5.2.1、nanoid 6.0.1、p-limit 7.3.1、yocto-queue 1.2.2 及其传递依赖; 设备端全程离线, axios/express 各用 localhost HTTP 自服务验证完整网络栈, 三个 ESM-only 包用动态 import 验证 exports/条件解析。实跑暴露并修复 package `exports` 数组 fallback 解析以及 http2 builtin 路由两个兼容缺口; `NpmEcosystemSmokeTest` 在 Android 9/12/15 三台 arm64 真机与 API 36/37 x86_64 模拟器矩阵 6/6 通过, `realEsmCorpus` 欠账关闭。
- [x] **M9.3 registry 设备端下载评估** (`registryDownload: deferred` 收账): 先出一页决策 — 范围 (纯 JS 包 + 生命周期脚本维持 denied)、网络策略 (归入宿主 network 能力声明)、镜像源可配置; 决策通过再实施 `package_manager.install` 端到端。Check: 决策记录入本文件; 若实施, package-install 样例升级 + 真机跑通一例真实小包下载安装 (此步涉外网, 单独手动触发, 不入常规验证链)。— 2026-08-25 评估完成, **决策为暂不实施设备端 registry 下载**: 当前 `package_manager.install/update` 继续只消费宿主管理、已落盘且可验证的本地包, 这是能力收账而不是暗中开放网络。允许实施前必须同时具备 ① 仅纯 JS 包且 install/preinstall/postinstall 等生命周期脚本永久 denied, ② registry/mirror 显式 allowlist 与 HTTPS/重定向/SSRF 策略, ③ 宿主 `network` 能力声明和每次安装用户可见授权, ④ tar 路径穿越/符号链接/解压炸弹防护, ⑤ 单包/文件数/总字节/依赖深度与时间配额, ⑥ lockfile、完整性哈希及依赖图确定性, ⑦ 下载/解析/提交全链取消与原子回滚。现有 facade 没有这些边界, 为一个已有宿主离线解包替代方案的功能引入供应链攻击面收益不成立; 因而不升级 package-install 样例, `registryDownload: deferred` 保持为有理由的产品决策。

### M10 — 深水区 (2/3; M10.1 等待上游 Android 产物)

- [ ] **M10.1 Node 24.5.0 → 24.17.0 (lts-security) 晋级**: 被上游 Android 产物阻塞 (`node2417Promotion: deferred`, nodejs-mobile 24.17 port 不存在; runtime-kit `releaseReady: false` 同源)。动作: 每月手动复查上游一次 (联网, 单独触发); 长期无产物则评估自建容器源码构建 (现 `sourceBuild: bootstrap_only`, 需先钉容器 digest)。Check (晋级时): 3 ABI libnode.so 产出且 sha256/buildId 落 lock; 插件全量冒烟组回归; runtime-kit `releaseReady` 转 true。— 2026-08-25 已按月度动作复查: 上游 [degaso/nodejs-mobile releases](https://github.com/degaso/nodejs-mobile/releases) 最新仍为 `v24.5.0-r1`, [全部分支](https://github.com/degaso/nodejs-mobile/branches/all) 也没有 24.17 port/产物; Node 官方 24.17.0 已发布但不能替代 Android libnode。阻塞条件未变, 本项保持未勾选; 不以未经钉定容器与三 ABI 验证的自建产物冒充晋级。
- [x] **M10.2 inspector 本地调试档位**: 现为 Environment 级三重封锁 (`kNoCreateInspector` 等)。分解: ① 安全边界决策 (debug 构建限定 + 显式请求键双门 + 仅 localhost + adb forward; `sensitivePathRedaction: not_proven` 一并裁定); ② native 条件解锁; ③ 端到端 chrome://inspect 连插件进程断点调试。Check: debug 构建 devtools 可连且断点命中; release 构建三重封锁不变 (负例); inspector-debug/cpu-profile/heap-snapshot 三样例按落地面重标。— 2026-08-25 完成: 新增 contract-v2 `inspectorEnabled` 请求键, 宿主只在自身 Debug 构建发送, 插件再以 `BuildConfig.DEBUG` 和 native `AUTOJS6_NODE_DEBUG_BUILD` 双重裁剪; Release 三 ABI 构建实证宏恒为 0, 继续设置 `kNoCreateInspector`, Debug 显式开启才移除该位并声明 `kOwnsInspector`, 且始终保留 no-start-signal/no-wait。JS 只暴露受控 `Session/console/open/close/url`, `open` 强制 127.0.0.1、拒绝非本机 host 与 `wait=true`, 默认请求仍返回 `ERR_AUTOJS6_BUILTIN_DISABLED`; 为兼容 Node inspector 的惰性初始化, 原生模块在 process facade 冻结前预载。`InspectorDebugSmokeTest` 在 API 37 x86_64 模拟器用真实 WebSocket/CDP 完成 Runtime.enable、Debugger.enable、断点暂停与恢复, 同时验证 CPU Profiler profile 与 Runtime heap usage; 文档明确 adb forward、无敏感路径/值脱敏及仅限本地受信调试。inspector-debug/cpu-profile/heap-snapshot 双仓样例由 disabled 改为 partial: 前两者可用, heap snapshot 的有界导出仍未实现。公共 API 以向后兼容的常量增量发布 `nodejs-api:1.3.0` (AIDL transaction hash 保持不变), runtime-kit 1.3.0 同步钉定新 AAR SHA-256, 手动 publication 门禁通过。
- [x] **M10.3 冷启动优化评估**: V8 startup snapshot 现禁用 (`disabled-until-snapshot-batch`)。先测量常驻进程复用下冷启动的真实占比与耗时分布; 若冷启动 <1s 且频率低, 记录"不值得做"结论并关闭本项 (合法完成态); 否则再立实施项。Check: 测量数据与结论记录入本文件。— 2026-08-25 完成评估, **结论为当前不值得实现自定义 startup snapshot**。新增 `RuntimeColdStartMeasurementTest`: 每台设备先 force-stop, 将专用进程 bind + service 同步预热单独计时, 再在同一常驻 PID 内连续执行 21 个 fresh isolate (首轮 + 20 个 warm 样本), 记录 wall/native total 及 isolate/environment/bootstrap/script 分相。Android 15/12/9 三台 arm64 真机的 bind+首次执行分别为 403/362/661ms, warm fresh-isolate wall median/P95 分别为 121/135、157/166、361/404ms; API 37 x86_64 模拟器为 bind+首次 1767ms、warm median/P95 228/396ms, 其 1065ms 一次性 bind/预热成本不属于每次 isolate snapshot 的主要收益。四设备 80 个 warm 样本 P95 均远低于 1s, process runtime 全程复用且执行频率受单槽串行模型约束; 预期收益不足以抵消 snapshot 生成、ABI/Node 版本耦合和发布验证成本, 保持 `disabled-until-snapshot-batch`, 有新实机证据跨过阈值时再重开。

## 五. 明确不做的事

- ❌ 不再新增任何 probe / 能力目录 / 所有权策略 / 哈希锁类基础设施。
- ❌ 不做请求级多层防御性校验 (同一个值不在 Java/C++/JS 三层各验一遍)。
- ❌ 不追求发布工件的可复现性证明链 (哈希锁), 版本号 + git tag 足够。
- ❌ 不为未发生过的异常写处理代码; 异常发生后针对该异常修复并附一个最小回归用例。
