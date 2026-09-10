# Samsung ARM64 16 KB 真机验收 (2026-09-10)

当前 Node.js Runtime 1.4.0 开发版在 Samsung SM-A566B 的原生 16 KB 内核上完成 Debug、混淆 Release、宿主联调与 universal APK 验收。最终验收组共 130/130 项通过, 无忽略或 Assume 跳过。首次宿主联调的依赖配置失败单独保留在下文。

本记录补充 [Roadmap M17.2a](../Roadmap.md) 与 [16 KB 页大小说明](../THIRD_PARTY_NOTICES.md#16-kb-page-size-validation)。本轮只增加验证记录, 运行时代码基线为 `3b7d66b`; 已发布 v1.3.0、catalog/runtime-kit 1.4.0 保持原样, 当前开发 kit 1.5.0 仍为 `releaseReady=false`。

## 设备与页大小证据

| 项目 | 实测值 |
|---|---|
| 连接 | 用户提供的 RDB 设备, ADB serial `localhost:31277` |
| 型号 / device | Samsung SM-A566B / `a56x` |
| 系统 | Android 16 / API 36 |
| ABI 列表 | `arm64-v8a` |
| `getconf PAGE_SIZE` | `16384` |
| `/proc/self/smaps` | `KernelPageSize: 16 kB`, `MMUPageSize: 16 kB` |
| Node worker 的原生库映射 | `libnode.so`、`libautojs6-node.so`、`libc++_shared.so` 共 11 个映射, KernelPageSize/MMUPageSize 均为 `16 kB` |
| build fingerprint | `samsung/a56xnaeea_16kb/a56x:16/BP2A.250605.031.A3/A566BXXU6BYIF_OXM6BYIF:user/release-keys` |

Node worker 的映射通过 Debug 应用的 `run-as` 读取, 与 shell 进程页大小交叉核对。此前 x86_64 AVD 的 `PAGE_SIZE=16384`、`KernelPageSize=4 kB` 记录仍属于用户空间模拟; 本次补上原生 ARM64 16 KB 内核的执行证据。

## 构建与测试结果

插件 Debug 和 Release 均使用现有本地 Gradle/NDK 缓存, 通过 `--offline` 构建。设备经用户提供的 RDB 连接; 测试中的网络用例使用设备本地服务, 本轮没有下载构建依赖或 npm 包。

| 验收组 | 被测 APK | 结果 |
|---|---|---|
| 完整常规组: 26 类 | arm64 Debug 1.4.0 / build152 | 53/53, 90.603 s |
| 实际 CDP 断点与默认禁用行为 | 同上 | InspectorDebugSmokeTest 1/1 |
| force-stop 后冷启动与 20 次暖进程执行 | 同上 | RuntimeColdStartMeasurementTest 1/1 |
| 完整常规组: 26 类 | arm64 Release 1.4.0 / build153 + 配套 Release 测试 APK | 53/53, 86.521 s |
| 宿主媒体、图像处理、图像字节传输: 3 类 | 宿主 Debug + Node Debug | 9/9, 9.581 s |
| 相同宿主联调组 | 宿主 Debug + Node Release | 9/9, 9.660 s |
| 安装、运行、npm、INFO/Manifest 契约 | universal Release 1.4.0 / build153 | 4/4, 17.151 s; 实际选择 `arm64-v8a` |

常规组涵盖 12 类高频冒烟、完整 NodeRuntimePluginAndroidConformanceTest、npm CJS/ESM 生态、SQLite、Worker、子进程、Web globals、UDP/本地网络、并发执行、取消与重启兜底、超时、输入输出、文件桥、空闲退出及插件契约。Inspector 和冷启动从常规组中排除, 单独执行。

宿主联调使用设备已有的 AutoJs6 6.8.0 / build5279 及其配套测试 APK, 安装包 SHA-256 与本地构建文件一致。宿主源码本轮没有修改。三个测试类为:

- `org.autojs.autojs.engine.NodeMediaBridgeInstrumentationTest` (5 项): 实际 MediaInfo v1/v2、相对/绝对/父目录/符号链接路径、含 `..` 和 `:` 的合法文件名、图片读写、mode-000 文件的 Android 拒绝、麦克风未授权负例及桥声明/兼容行为。
- `org.autojs.autojs.engine.NodeImageOperationsInstrumentationTest` (2 项): 图像裁剪、缩放、灰度/二值化、找色、模板匹配、句柄生命期与非法参数。
- `org.autojs.autojs.engine.NodeImageBytesInstrumentationTest` (2 项): JNI 与文件后备传输均跨进程传递 1080 x 1920 RGBA Buffer (8,294,400 字节), 验证通道/alpha、PNG 解码、ArrayBuffer 共享及图像句柄回收后的 Buffer 生命期。

图像字节测试使用 UiAutomation 截图构造固定像素夹具, 不覆盖 MediaProjection 的用户授权流程。Debug 联调记录的 RGBA 中位值为 JNI 29.40 ms / 文件 62.23 ms; Release 联调为 38.47 / 45.76 ms。测试取 8 次排序后的第 5 个值, 均低于既有 200 ms 检查值。Debug 冷绑定与首次执行合计 658 ms, 暖进程执行中位值/P95 为 216/225 ms; 这些是本轮样本, 不构成跨设备性能保证。

## 首次失败与依赖补齐

首次宿主联调结果为 6 项通过、3 项断言失败, 无跳过。设备尚未安装 OpenCV, 导致 2 项图像操作用例返回缺少依赖; 本轮最初安装的 MediaInfo 2.0.0 / build10 不声明 snapshot v2, 导致 1 项 schema 检查失败。其余用例, 包括两条 1080p Buffer 传输, 已通过。

随后从既有 Xiaomi 测试设备只读复制 OpenCV 1.1.0 / build10 APK, 并安装本地已构建的 MediaInfo 2.1.0 / build11 Debug APK。两份 ARM64 原生库的 PT_LOAD 均为 `0x4000`。补齐依赖后, 宿主的完整 9 项在 Node Debug 和 Release 上分别通过。没有为这些配置问题改动插件或宿主实现, 首轮失败日志保留为 `debug-host-initial-dependencies.txt`。

## 工件核对

Debug APK 实际 `versionCode=152`; Debug assemble 完成时仓库按既有规则递增到 153, 后续 Release APK 实际 `versionCode=153`。两者使用同一运行时代码基线, 实测回报 Node 24.21.0 / OpenSSL 3.5.8。

四份收集后的 Release APK 均通过 `apksigner verify --verbose` 与 `zipalign -c -P 16 -v 4`。arm64 Debug/Release 的 `libnode.so`、`libautojs6-node.so`、`libc++_shared.so` 每个 PT_LOAD 均为 `0x4000`, APK 内原生库为压缩条目, 安装后抽取使用。下面的 SHA-256 用于标识本次实际测试工件, 不新增构建门禁。

| 工件 | SHA-256 |
|---|---|
| Node arm64 Debug build152 | `9b32b6ac468ac8d69d704d242459c3e6f8ebd7ac3c17bbe9f1619deb0f33dcda` |
| 配套 Debug AndroidTest | `281960dc83df4a5c9364b7c465bba7901917b8d6ed48e48327411502fc22dcf1` |
| Node arm64 Release build153, CRC32 `689A4024` | `93c2da3d5d08866d2b826387a544ad6f2c6796b09b85ba7ca485b55595f01f10` |
| Node universal Release build153, CRC32 `085EBB88` | `202158849d22b4aaf874e14be84e4410ac54c44fe02be2b0278688ebaa3fb6db` |
| 配套 Release AndroidTest | `4060bd79b7ada90e940383be222eaae8838dc66b3d7b0c6da2189007d7fa6883` |
| MediaInfo 2.1.0 Debug build11 | `245b2bb54a6d8bf48900db8553e8cc1f8a87e621822a33acf24f9f6a9d59540a` |
| OpenCV 1.1.0 build10 | `03357aa999a110eabd1aa281d06101564ae3d7b419c6d43c33d65cad8f918a74` |

`libnode.so` SHA-256 为 `c48da9b772635e8e76ce0a06de09e71dbd70efe602903f1f5c9042672e629b2d`, 与当前源码构建记录一致。完整文件大小、原生库摘要、ELF headers、原始 instrumentation 和 JSON 回执保存在本机 `build/samsung-arm64-16k-20260910/`; 收集后的开发版 APK 位于 `app/releases/1.4.0/`。

## 复核入口与清理

```powershell
$adb = 'E:/.android/sdk/platform-tools/adb.exe'
$serial = 'localhost:31277'
& $adb -s $serial shell getconf PAGE_SIZE
& $adb -s $serial shell 'head -30 /proc/self/smaps'

# Debug 与其测试 APK 配套安装。
.\gradlew.bat --offline :app:assembleDebug :app:assembleDebugAndroidTest
& $adb -s $serial install -r -t app/build/outputs/apk/debug/autojs6-plugin-nodejs-runtime-v1.4.0-arm64-v8a.apk
& $adb -s $serial install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

$prefix = 'io.github.supermonster003.autojs6.plugin.nodejs.'
$classes = @(rg --files app/src/androidTest/java -g '*Test.java') |
    ForEach-Object { [IO.Path]::GetFileNameWithoutExtension($_) } |
    Where-Object { $_ -notin @('InspectorDebugSmokeTest', 'RuntimeColdStartMeasurementTest') } |
    Sort-Object | ForEach-Object { $prefix + $_ }
& $adb -s $serial shell am instrument -w -r -e class ($classes -join ',') io.github.supermonster003.autojs6.plugin.nodejs.test/androidx.test.runner.AndroidJUnitRunner

# Release 使用收集任务打印的实际 CRC32 文件名及 Release 测试 APK。
.\gradlew.bat --offline -PnodeAndroidTestBuildType=release :app:appendDigestToReleasedFiles :app:assembleReleaseAndroidTest
```

每轮检查最终 `OK (...)`、逐项失败、忽略/Assume 状态和 crash buffer, 不仅检查 adb 退出码。本轮开始至最终验收的 crash buffer 内容未增加; 现有历史崩溃未清空。文件系统用例临时授予的 `MANAGE_EXTERNAL_STORAGE` 已恢复原 `default`, 验收后停止本轮 Node 运行进程。设备保留 universal Release build153、配套 Release 测试包及上述两个依赖插件。没有启动 AVD, 其他设备及其测试应用未改动。

M14 截屏/OCR、M15 真实通知/Toast/按键与麦克风录音仍按 [人工验收指南](nodejs/MANUAL-ACCEPTANCE.md) 等待用户下一次回报。设备 `QV770340J7` 上的 `/sdcard/Scripts/nodejs-roadmap-acceptance-20260910/sample/nodejs/` 继续作为人工测试入口。本次结论对应上表设备、系统及工件, 不扩大到所有 ARM64 设备、32 位 ABI 或未执行的人工场景。
