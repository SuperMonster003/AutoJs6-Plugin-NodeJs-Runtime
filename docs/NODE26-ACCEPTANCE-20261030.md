# Node 26.13.0 设备验收 (M25.2d, 2026-10-30)

本记录是 [Roadmap M25.2d](../Roadmap.md) 的验收证据: 入库的三个 ABI libnode 26.13.0 (M25.2a) 与 M25.2b / 2c 的 adapter, 槽位, 终端资产和 addon, 在四台设备上以 Debug 与配套混淆 Release 各跑一遍完整常规组. 被测构建仍是 1.32.0 (versionCode 322) 的开发构建, 不是发布产物; 版本号, changelog 与 2.0.0 发布归 M25.3. 开发 kit 仍为 `releaseReady=false`.

## 运行时事实

| 项目 | 实测值 |
|---|---|
| `process.version` / `getRuntimeInfo` | `v26.13.0` |
| V8 | `14.6.202.34-node.40` |
| OpenSSL / ICU | `3.5.9` / `78` (full-icu) |
| 终端 npm / corepack | `11.20.0` / `0.36.0` |
| `NODE_MODULE_VERSION` | `147` (addon 按 26 headers 重建) |
| 工具链 | libnode 用 NDK r29 `29.0.14206865`; 桥接 `libautojs6-node.so` 与 `libc++_shared.so` 用 AGP 默认 r28c `28.2.13676358` (附录 B Q10 = A) |
| Temporal | 三种 ABI 的终端启动器 `libnodexe.so -p` 均返回 `typeof Temporal === "object"`, `Temporal.PlainDate.from("2026-10-30").add({ days: 2 })` 为 `2026-11-01`, 带 `relativeTo` 的 Duration 取整为 `P1DT1H` |
| 26 移除的 API | `fs.F_OK` 为 `undefined` (改用 `fs.constants`); 插件内嵌 JS, 样例, 类型与测试均未使用 `fs.F_OK` / `writeHeader` / `_stream_*` 内部模块, corpus 中的 `readable-stream` 用自带的 lib 文件 |

## 设备矩阵

完整常规组 = `tools/nodejs/release/release_acceptance.py --classes full`: 34 个 `*SmokeTest` 类 (含 12 个高频冒烟组, NpmEcosystem corpus, Worker, SQLite, addon, OpenSSL, Inspector, 终端启动器, 原生崩溃报告) 加 NodeRuntimePluginAndroidConformanceTest (33 项), PluginManifest 与 PluginInfo 契约, 共 37 类 114 项.

| 设备 | Debug | Release |
|---|---|---|
| OPD2413 `63751f63`, Android 15 / API 35, arm64, 4 KB | 37/37 类, 114 项 | 37/37 类, 114 项; universal APK 4/4 类 (实际选择 `arm64-v8a`) |
| XQ-AT72 `QV710AF65F`, Android 12 / API 31, arm64, 4 KB | 36/37 类 (修复前构建, 唯一失败为下文 (1b)); 修复后 BridgeLatency, SimpleRun 与两个契约类 4/4 | 36/37 类; 唯一失败 DgramUdpSmokeTest 当次 instrumentation 未在设备上启动 (logcat 无该类的 TestRunner 记录, adb 传输中断), 单独重跑 1/1 |
| G8441 `BH900ASK9E`, Android 9 / API 28, armeabi-v7a 包 | 37/37 类, 114 项, 1 项 Assume 跳过 | 37/37 类, 114 项, 1 项 Assume 跳过 |
| AVD_API_35_16K `emulator-5590`, API 35, x86_64, `PAGE_SIZE=16384` | 37/37 类, 114 项 | 37/37 类, 114 项 |

G8441 的跳过是既有的 `UnrestrictedFsSmokeTest` SDK 条件 (MANAGE_EXTERNAL_STORAGE 需 Android 11). 全部运行中没有 libc++ 相关的崩溃. 墓碑中只有测试有意触发的 SI_USER / OOM 处理器 SIGABRT; 另有一次在宿主并行构建时, 模拟器测试运行器进程的 ART JIT 代码 SIGSEGV (pc 0, androidx ActivityFinisher), 与插件原生库无关, 该类单独重跑 33/33.

## 回归中发现并修复的问题

修复前的 Debug 全量在 XQ-AT72, AVD 与 G8441 上都只失败同样两类, 均为 1.32.x 既有问题, 与 Node 26 无关 (附录 B Q11, 已按推荐先行):

1. BridgeLatencySmokeTest: (a) rhino 兼容层 Java 代理的 `getStatic` / `describe` / 静态调用 / `new` 仍调用 5bc027d5 删除的内部函数, 现改走 `__autojs6_limited_java()` 的共享 Java facade (facade 禁用时与其异步调用一样 reject); (b) 修好 (a) 后暴露的 `m20.java.facade` 期望仍为 `allowlist`, 5bc027d5 起为 `public`.
2. WorkerDesktopSmokeTest 仍期望 Worker 内 `require("sqlite")` 抛错, 而 772ab29f 起 sqlite 是 Worker 可用的共享 facade.

另按 26 对齐: conformanceSet `node26_native_builtins`, `kEmbedderSymbols` 的 `Utf8Value` 同时列出带 `WriteOptions` 与不带的构造符号, 两条原生错误信息去掉 "Node 24", NativeCrashReportSmokeTest 期望 `v26.` 前缀, hello-node 期望输出, d.ts / HOST-API / COMPATIBILITY_PROFILE / 类型 README 的版本说明, runtime kit 的 buildProvenance 与 payload 摘要, THIRD_PARTY_NOTICES 的 NDK 行.

## 冷启动 (M23.8 口径)

单位 ms. Debug 5 轮, Release 3 轮; 每轮 force-stop 后 1 个进程冷样本 + 20 个常驻样本. 冷 P50/P95 为跨轮统计 (N <= 5 时 P95 即最大值); 常驻与分相为各轮对应百分位的中位数. 内嵌 JS 对应 `warmPhaseDistribution.bootstrapMs`. `node_bridge_sources.cpp` 为 38433 行, 内嵌源码 38076 行 / 1866296 字节.

| 构建 | 设备 | 冷首输出 P50/P95 | 常驻首输出 P50/P95 | 环境创建 P50/P95 | 内嵌 JS P50/P95 |
|---|---|---:|---:|---:|---:|
| Debug | arm64 / API35 (OPD2413) | 384/419 | 104/117 | 29/31 | 18/21 |
| Debug | x86_64 / API35 / 16 KB | 807/874 | 131/176 | 40/55 | 28/36 |
| Release | arm64 / API35 (OPD2413) | 222/243 | 93/110 | 27/33 | 16/20 |
| Release | x86_64 / API35 / 16 KB | 303/696 | 133/416 | 42/132 | 31/82 |
| Release | arm64 / API31 (XQ-AT72) | 415/442 | 154/164 | 49/55 | 26/26 |
| Release | armv7 / API28 (G8441) | 758/774 | 351/377 | 90/96 | 64/82 |

对照 M23.8 (Node 24.21, arm64 / API35 同型号设备): Debug 586/714, 117/126, 40/46, 18/18; Release 475/477, 103/113, 42/45, 18/18. 26 的 arm64 冷首输出与环境创建都更快, 内嵌 JS 持平. x86_64 一行用的是 2 GB 内存的 API 35 16 KB AVD (M23.8 为 API 37), P95 波动大: 同一 Release 包在其他设备并行测试时测得 305/362, 157/366, 51/73, 35/63, 宿主空闲时为表中一行; 只作量级参考.

## 工件

Release 包由 `-PnodeAndroidTestBuildType=release :app:assembleRelease :app:assembleReleaseAndroidTest` 产出, 没有执行 `appendDigestToReleasedFiles` (版本号仍为 1.32.0, 该任务会替换 `app/releases/1.32.0/` 里已发布的包). 四个 Release 包与三个 Debug 包都通过 `apksigner verify` 与 `zipalign -c -P 16 4`.

| 工件 | 字节 | 相对 v1.32.0 | SHA-256 |
|---|---:|---:|---|
| Release arm64-v8a | 46393835 | +3231086 (+7.5%) | `50a03bd9ea1edd5039d9e732a5e8dee9c6c8e74a545f1e1f15913aa5e022b378` |
| Release armeabi-v7a | 45441703 | +3299074 (+7.8%) | `7f717e5d5401ce112857de56c86562b9b65f344bd959016b1dd0b99c15e26c1b` |
| Release x86_64 | 48222139 | +3425458 (+7.6%) | `ec01f50ad50beea7b79c66e560dd28491f812543a84814906fb15f52473fd779` |
| Release universal | 132571431 | +9608402 (+7.8%) | `48199f2033bd32819973cf3a5fe0411bd3fc4454514e6a16aae605a87f3aedb6` |
| Release androidTest | | | `09a80f3c6c5dc6477070d53ea9762ef563507f849b44a8b050be73e38be56488` |
| Debug arm64-v8a | 52623324 | | `eedf633871e16b3e19ddda6f0b833a41d70de59f760c41060af106430d29d577` |
| Debug armeabi-v7a | 50960683 | | `54f67ae7b3ce8ab8fbd1aafe3bc7aa4e49581b649a9b1a72b62682c025600a39` |
| Debug x86_64 | 54499367 | | `2ca1543aaf3d81ad5da8cd7d3badd554a1e2fe362d4b7c8d2e2dbafba26da565` |
| Debug androidTest | | | `c45a7883d650a15bc7f1d42e4724b6d0fa4a752a6e85becef511bca9d6a47e54` |

## 未覆盖

- 原生 16 KB 内核的 arm64 实机: 本轮只有 x86_64 AVD 的用户空间 16 KB 模拟, 实机由维护者租用 RDB 后在 M25.3 补验 (附录 A D4).
- 宿主在 1.32.x 与 2.0.0 两版插件下的契约测试, 签名收集后的最终安装验收: 归 M25.3.
