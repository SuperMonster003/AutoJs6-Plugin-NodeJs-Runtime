# AutoJs6 Node.js Runtime 插件 — 开发路线图 (Roadmap)

> 修订日期: 2026-08-17
>
> 本路线图取代此前所有里程碑编号 (X3d/X3e/X3f/X3g/X3i/X3j 等)。旧编号只保留在 `tools/nodejs/ownership/evidence/` 的历史证据文件中, 不再继续演进。

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

### M0 — 最简脚本先跑起来 (首个可交付任务组) ✦当前✦

目标: **一个只带 `source` 的最小请求就能执行 Node 脚本并拿到 stdout**; 宿主旧契约不再被硬拒; 开发循环恢复轻快。

- [x] M0.1 直跑模式 (direct-run): `runScript` 在缺少 workspace FD 时不再报错, 改为在插件私有目录直接执行 (自动建立工作目录, 路径映射恒等, 提交/回写自动跳过)。带 FD 时行为与原来完全一致。— `PluginWorkspaceArchiveSession.openDirect` + `hasWorkspaceDescriptors` 分流。
- [x] M0.2 契约宽容: 请求中缺失 `contractVersion` 视为当前版本 (显式错误值仍拒绝); module-source-provider 接受 v1..v2, 缺失版本按 v1 处理, 并在与宿主 provider 往返时回显协商版本 (v1 宿主发 v1 请求, v1 响应归一化为 v2 语义)。— 单测 75 个全绿。
- [x] M0.3 门禁下链: `check` / 常规构建不再依赖 capability-truth、runtime-ownership、resolver、nodejs-api publication 等验证门禁; 全部保留为手动任务 (`verifyNode*` 系列), 需要时单独执行。— `:app:check --dry-run` 已确认 0 引用。
- [x] M0.4 构建验证: `:app:assembleDebug` 全 ABI 通过 (v1.1.3 build 41)。
- [x] M0.5 真机烟雾验证: x86_64 模拟器 (API 36 与 API 37 各一台) 安装插件 APK, 最小请求 (仅 `source`, 无 FD/无契约字段) 跑通 `console.log`, 断言 stdout 含 `m0.smoke=42`, 约 0.9s 完成; 同机回归原 FD 路径 (CJS+ESM conformance x3d_02) 通过。承载测试: `SimpleRunSmokeTest`。
- [x] M0.6 本 Roadmap.md 落库, 旧里程碑体系归档说明 (见文档头部)。

交付物: 可安装 APK + 通过的烟雾验证 + 本文档。

### M1 — 与 AutoJs6 宿主真实打通

目标: 在真实 AutoJs6 App 里, 编辑器输入 `"nodejs";` 脚本 → 点击运行 → 看到输出。

- [x] M1.1 宿主侧联调: 用 master 分支 AutoJs6 (nodeFull flavor) + 本插件, 真机/模拟器跑通 `sample/nodejs/hello-node`。记录并修复联调中出现的每一个具体异常 (逐异常修复, 不做预防性改造)。— 2026-08-17 真机 (小米 Android 15 arm64) 插件 v1.1.3 + 宿主 v6.8.0: 宿主 `NodeExampleProjectInstrumentationTest` 7/7 (含 hello-node), 插件 conformance 24/24。修复 3 个异常: ① bootstrap 硬锁 config v2 → 宽容 v1..v2 并回显协商版本; ② conformance mock provider 不认 metadata preflight → not_found 回应 + 计数断言更新; ③ TS 剥离器字符串掩码用空格导致 `('cjs' as string)` 被剥成 `()` → 掩码改用非空白占位符 U+0001, 附回归单测。附加事实: 小米真机跑宿主 instrumentation 需先亮屏解锁并把宿主拉到前台一次, 否则 FGS 被拒、settle 超时。
- [x] M1.2 契约对齐决策: 与宿主仓库同步 `nodejs-api` (以哪边为准做一次性对齐, 建议宿主升级到插件的 v2 AIDL 包, 或插件回落 v1); 移除 `releases/` 下 AAR 哈希锁对迭代的阻塞。— 2026-08-17 决策: **以插件 v2 为准, 宿主择期升级; 过渡期由插件 v1..v2 宽容层承接** (已真机验证)。依据: 两边 AIDL 内容一致; 宿主 v1 provider 对 v2 请求的拒绝响应 (version=1 + INVALID_REQUEST) 恰好触发插件的一次性降级重试, 桥是闭合的; 宿主升 v2 需要实现 v2 操作语义 (materialize_missing_plaintext 等), 不是换常量, 按"逐异常修复"原则留到宿主仓库联调时做。哈希锁阻塞已随 `:nodejs-api` 源码模块化移除: app 改用 project 依赖, `assemble`/`check` 零锁引用 (dry-run 验证), 锁只留在手动发布任务 (`verifyNodeJsApiPublication` 等)。
- [x] M1.3 加密模块脚本联调 (宿主 provider v1 路径) 跑通。— 2026-08-17 真机: 宿主新增 `pluginRuntimeDecryptsEncryptedModuleThroughHostV1Provider` (AES 加密 `secret.cjs` + 宿主 v1 `NodeJsEncryptedModuleSourceProvider` → 插件 runtime 解密执行, 断言 decrypted_count=1, 一次性 v2→v1 降级协商生效), 与 live bridge 用例同类 2/2 通过。逐异常修复 2 个: ① 插件 engine-info 注入诊断把 workspace 映射预填误报为 "existing" → 新增 `withReplacedRuntimeModule` 保留真实来源 (host_broker); ② 宿主 bridge 测试仍断言旧 "working_directory" 标签 → 更新为 M0.2 后的 "exact_metadata_snapshot"。插件 conformance 24/24、宿主样例 7/7 回归通过。
- [ ] M1.4 项目级脚本 (project.json + 多文件 + node_modules) 通过工作区传输跑通。
- [ ] M1.5 失败路径可读: 联调中出现的错误信息能让用户看懂 (错误码 + 一句人话), 不再输出诊断键值对墙。

### M2 — 桌面级 Node 开发体验

目标: 常驻交互与长脚本可用, 接近本地 `node foo.js` 的手感。

- [ ] M2.1 stdout/stderr 流式回传 (脚本运行中即时输出, 不等结束)。
- [ ] M2.2 协作式取消: 不重启进程即可停止脚本 (`worker/isolate` 终止), 重启仅作兜底。
- [ ] M2.3 并发执行或串行队列 (替代直接 `BUSY` 拒绝), 至少支持 2~4 个并行脚本。
- [ ] M2.4 npm 生态实测: 选 10 个高频纯 JS 包 (lodash, dayjs, axios(可延后), express(可延后) 等) 实跑, 逐个修复暴露的问题。
- [ ] M2.5 长驻脚本 (定时器 / 事件循环不退出) 生命周期管理与手动停止。
- [ ] M2.6 网络模块默认放开 (http/https/net/dns), `rawNodeNetworkModules` 不再默认关闭; 出现具体滥用问题再针对性处理。

### M3 — AutoJs6 API 能力面扩展

目标: Node 脚本里可用的宿主 API 覆盖日常自动化场景。

- [ ] M3.1 盘点 live bridge 现有可调用能力 (以 `require("accessibility")` 样例为基线), 输出一页 "已可用 API 清单"。
- [ ] M3.2 补齐高频 API: toast / app.launch / click / swipe / text 查找 / 剪贴板 / shell。
- [ ] M3.3 files / http 等与 Node 原生能力重叠的 API: 直接文档引导用 Node 原生实现, 不重复造桥。
- [ ] M3.4 `sample/nodejs` 收敛: 60 个样例按 "能跑/不能跑" 重新标注, 不能跑的要么修好要么移入 `sample/nodejs/_pending/`。

### M4 — 减脂与常态维护

目标: 代码量与真实功能匹配, 新人可读。

- [ ] M4.1 删除 C++ probe 体系 (embedded_probe_* 全家, 191 值枚举, ~200 个 probe JNI 导出), 预期 C++ 从 ~83k 行降到 <15k 行。
- [ ] M4.2 删除/归档 capability-truth、runtime-ownership、runtime-kit、conformance gradle (共 ~7k 行 gradle+js), 保留 runtime-build (libnode 构建脚本是真资产)。
- [ ] M4.3 conformance androidTest 收敛为 <10 个冒烟用例; 删除已无对应功能的用例。
- [ ] M4.4 `NodeJsRuntimePluginService` 拆分瘦身 (2.3k 行 → 目标 <800 行)。
- [ ] M4.5 文档一页化: README 里写清 "怎么装 / 怎么跑 / 出错了看哪里"。

## 五. 明确不做的事

- ❌ 不再新增任何 probe / 能力目录 / 所有权策略 / 哈希锁类基础设施。
- ❌ 不做请求级多层防御性校验 (同一个值不在 Java/C++/JS 三层各验一遍)。
- ❌ 不追求发布工件的可复现性证明链 (哈希锁), 版本号 + git tag 足够。
- ❌ 不为未发生过的异常写处理代码; 异常发生后针对该异常修复并附一个最小回归用例。
