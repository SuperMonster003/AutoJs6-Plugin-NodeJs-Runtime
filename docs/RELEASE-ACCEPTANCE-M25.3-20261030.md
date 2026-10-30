# 2.0.0 与 1.32.x 发布验收 (M25.3, 2026-10-30)

本记录是 [Roadmap M25.3](../Roadmap.md) 的验收证据: 2.0.0 (Node.js 26.13.0, build 340, 提交 `0e1e6d14`) 与 1.x 维护分支 1.32.2 (Node.js 24.21.0, build 303, `release/1.x` 提交 `c8a64878`) 的四个签名 Release APK, 在两台真机上跑配套混淆测试包的完整常规组, 并在模拟器上跑宿主的 Node 插件契约测试. 1.32.1 (build 302) 的设备验收记在 Roadmap M25.3 的进度说明里, 这里只列它的宿主契约结果.

## 发布包

| 版本 | ABI | 大小 (B) | SHA-256 前 16 位 |
|---|---|---|---|
| 2.0.0 | arm64-v8a | 46441522 | `2df318aec73e10fd` |
| 2.0.0 | armeabi-v7a | 45482974 | `6f845c1bfac92164` |
| 2.0.0 | universal | 132646150 | `a3ed68be67075e89` |
| 2.0.0 | x86_64 | 48269870 | `df9ab08d8237ccf7` |
| 1.32.2 | arm64-v8a | 43172333 | `2c774df2dfeeb114` |
| 1.32.2 | armeabi-v7a | 42147597 | `45ecfd4c8153b3b8` |
| 1.32.2 | universal | 122983565 | `96561973c27cad8c` |
| 1.32.2 | x86_64 | 44805705 | `15aa870575dd1f4a` |

两版都用现有发布证书签名 (signer SHA-256 `31a681fc...4c213`, 与 1.32.0 相同), 64 位原生库与 APK 通过 16 KB 对齐检查. 完整摘要见各 Release 的 `SHA256SUMS` 与官方索引的 admission manifest (302 / 303 / 340.json).

## 设备验收

命令: `tools/nodejs/release/release_acceptance.py --classes full`, 被测包为对应 ABI 的 Release APK, 测试包为同一提交用 `-PnodeAndroidTestBuildType=release` 构建的配套混淆测试包. 设备: G8441 `BH900ASK9E` (Android 9 / API 28, 装 armeabi-v7a 包), OPD2413 `63751f63` (Android 15 / API 35, arm64, ColorOS).

| Class | 2.0.0 G8441 | 2.0.0 OPD2413 | 1.32.2 G8441 | 1.32.2 OPD2413 * |
|---|---|---|---|---|
| AsyncExecutionSmokeTest | ok 2 | ok 2 | ok 2 | ok 2 |
| BridgeFileTransportSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| BridgeLatencySmokeTest | ok 9 | ok 9 | ok 9 | ok 9 |
| BuiltinPassthroughSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| CancellationRestartFallbackSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| CompileCacheSmokeTest | ok 3 | ok 3 | ok 3 | ok 3 |
| ConcurrentExecutionSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| ConfigurableProcessPoolSmokeTest | ok 5 | ok 5 | ok 5 | ok 5 |
| CooperativeCancelSmokeTest | ok 2 | ok 2 | ok 2 | ok 2 |
| DgramUdpSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| ExecutionModeLifecycleSmokeTest | ok 2 | ok 2 | ok 2 | ok 2 |
| ExecutionTimeoutSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| FsAsyncParitySmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| HostInputSmokeTest | ok 5 | ok 5 | ok 5 | ok 5 |
| IdleExitSmokeTest | ok 2 | ok 2 | ok 2 | ok 2 |
| InspectorDebugSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| LongRunningLifecycleSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| NativeAddonSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| NativeCrashReportSmokeTest | ok 2 | ok 2 | ok 2 | ok 2 |
| NativeWebGlobalsSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| NetworkDefaultOnSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| NodeCliLauncherSmokeTest | ok 6 | ok 6 | ok 6 | ok 6 |
| NodeSqliteSmokeTest | ok 2 | ok 2 | ok 2 | ok 2 |
| NpmCliSmokeTest | ok 2 | ok 2 | ok 2 | ok 2 |
| NpmEcosystemSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| OpenSslSecuritySmokeTest | ok 4 | ok 4 | ok 4 | ok 4 |
| QueuedExecutionTimeoutSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| RuntimeSettingsUiSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| SerialQueueSmokeTest | ok 2 | ok 2 | ok 2 | ok 2 |
| SimpleRunSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| SlotExitReasonSmokeTest | ok 2 | ok 2 | ok 2 | ok 2 |
| StreamingOutputSmokeTest | ok 1 | ok 1 | ok 1 | ok 1 |
| UnrestrictedFsSmokeTest | ok 1, 1 skip | ok 1 | ok 1, 1 skip | ok 1 |
| WorkerDesktopSmokeTest | ok 10 | ok 10 | ok 10 | ok 10 |
| NodeRuntimePluginAndroidConformanceTest | ok 33 | ok 33 | ok 33 | ok 33 |
| PluginManifestContractTest | ok 2 | ok 2 | ok 2 | ok 2 |
| PluginInfoContractTest | ok 1 | ok 1 | ok 1 | ok 1 |
| **Classes passed, tests** | 37/37, 114 | 37/37, 114 | 37/37, 114 | 37/37, 114 |

格中数字为该类的测试项数; G8441 上 UnrestrictedFsSmokeTest 的 1 项按 Assume 跳过 (既有 SDK 条件, MANAGE_EXTERNAL_STORAGE 需 Android 11), 与 M25.2d 相同.

\* 1.32.2 在 OPD2413 上首次完整运行为 36/37: BridgeLatencySmokeTest 报 `Process crashed`. 原因是此前一次超时的 `adb install` 在 ColorOS 安装确认页上排着队, 维护者确认后它在 12:21:31 提交, `installPackageLI` 强制停止了被测包, 不是插件缺陷. 该类单独重跑 9/9 通过, 表中为重跑结果. 以后在 ColorOS 上安装超时, 要先处理掉排队的安装会话再继续.

2.0.0 (337) 是 Q18 修复前的发布提交 `4d3a18f6`, 当时两台真机也都是 37/37; 修复后的 340 是最终发布的包, 表中只列 340.

## 宿主契约测试

命令: `tools/nodejs/release/host_contract.py`, 宿主 AutoJs6 6.8.0 (versionCode 5935, 含本地提交 `9dec1c1ad`, 附录 B Q16), 模拟器 AVD_API_33 (`sdk_gphone64_x86_64`, API 33, x86_64). 默认 7 类; NodeBridgeConformanceInstrumentationTest 按附录 B Q17 移出默认列表 (宿主快照整体过时, 已转交 api-coverage).

| Class | 1.32.1 (302) | 1.32.2 (303) | 2.0.0 (337) | 2.0.0 (340) |
|---|---|---|---|---|
| NodeHostPluginIntegrationInstrumentationTest | ok 3 | ok 3 | ok 3 | ok 3 |
| NodeRuntimePluginBridgeInstrumentationTest | ok 6 | ok 6 | ok 6 | ok 6 |
| NodeRuntimePluginR5InstrumentationTest | ok 2 | ok 2 | ok 2 | ok 2 |
| NodeBridgePermissionManifestInstrumentationTest | ok 11 | ok 11 | ok 11 | ok 11 |
| NodeBridgeProviderRegistryInstrumentationTest | ok 5 | ok 5 | ok 5 | ok 5 |
| NodeR6EngineSwitchInstrumentationTest | ok 1 | ok 1 | ok 1 | ok 1 |
| NodeR6RequiredPluginSecurityInstrumentationTest | ok 1 | ok 1 | ok 1 | ok 1 |
| **Classes passed, tests** | 7/7, 29 | 7/7, 29 | 7/7, 29 | 7/7, 29 |

Q16 之前, 同一宿主 HEAD 上 1.32.1 为 3/8 类, 2.0.0 为 2/8 类, 两版失败的 5 类完全相同, 都是宿主测试期望过时 (预热前要求远端 PID, provider 数 28 -> 29, app 分发列表, Rhino 返回 `Undefined`, 模拟器未装 ML Kit 时的分支); 2.0.0 独有的差异只有 Bridge 派发数 8 -> 9 (storages 名称注册, D-5 / Q79). `9dec1c1ad` 更新这些断言后四个包全部 7/7.

## shell.exec 竞态 (附录 B Q18 / Q19)

宿主 Bridge 类在 2.0.0 (337) 上跑了约 8 次, 有 1 次 `shell.exec` 抛 `ERR_STREAM_DESTROYED`: 命令很快结束时, 子进程已退出, `closeInput()` 仍对已销毁的 stdin 调用 `end()`. 自 1.32.0 引入 shell facade 起就存在. 修复 (`0e1e6d14`, 随 1.32.2 发布提交 `c8a64878` 回移到 release/1.x): `closeInput()` 发现输入已关闭或已写完时直接按成功返回, `end()` 的错误只在进程未退出时才 reject. 修复后 2.0.0 (340) 的宿主 Bridge 类另重复 5 次均通过, 上表两版 1.32.2 / 2.0.0 (340) 的完整组与契约组也都通过.
