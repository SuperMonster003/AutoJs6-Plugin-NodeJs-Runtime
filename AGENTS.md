# AutoJs6 Node.js Runtime 开发约定

本文件约束本仓库的开发工作, 用户的当前明确要求优先。唯一开发路线图是 [Roadmap.md](Roadmap.md); 开始任务先读其中的通用约定和当前未完成项。

## 项目身份与职责

| 项目字段 | 值 |
|---|---|
| rootProject.name | `autojs6-plugin-nodejs-runtime` |
| applicationId / namespace | `io.github.supermonster003.autojs6.plugin.nodejs` |
| app_name | `AutoJs6 Node.js Runtime`, 不翻译 |
| plugin ID / engine / category | `nodejs` |
| variant / runtime slot | `node24_5` |
| Node.js | `24.5.0` |
| INFO action / service | `org.autojs.plugin.INFO` / `NodeJsPluginInfoService` |
| RUNTIME action / service | `org.autojs.plugin.nodejs.RUNTIME` / `NodeJsRuntimePluginService` |
| 最低宿主 versionCode | `3923` |
| 支持 ABI | `arm64-v8a`, `armeabi-v7a`, `x86_64`; 另产出 universal APK |

插件负责真实 Node/V8 运行时、解析器、执行生命周期、公共 `plugin-api/nodejs-api`、`sample/nodejs`、`docs/nodejs/types` 和项目向导。宿主负责入口、发现、Binder 客户端、工作区传输、Android 能力与 TypeScript 编译路由; 样例和类型仅在本仓维护。依赖必须在本仓自包含, 不引用兄弟仓二进制。

## 工作区与提交

- 开始先看 `git status --short`、当前分支、最近提交与相关差异; 搜索目标目录中更深层的 AGENTS.md。
- 保留用户已有工作。禁止未经明确授权使用 `git reset --hard`、`git checkout -- <path>` 或覆盖无关改动。
- 每个 Roadmap 项按完整意图独立提交, 使用 Conventional Commits。只在相应 Check 通过后勾选; 未运行、外部待确认与设备限制必须如实记回 Roadmap。
- 除用户明确要求不提交外, 提交本次范围内已完成的变更。提交前检查 `git diff --check`、`git diff --cached` 和 `git status --short`。
- `VERSION_NAME` 遵循语义化版本; 每次提交前将 `VERSION_BUILD` 校正为 `git rev-list --count HEAD` 加 1, 使提交后的构建号与提交数一致。保留构建产生的真实 BUILD_TIME。
- push、tag、GitHub Release、远端分支删除和 catalog/runtime-kit 对外发布按 Roadmap 通用约定, 先准备可审阅产物, 再取得针对当次动作的用户明确同意。

## 构建与文档

- Settings 使用公共 `io.github.supermonster003.autojs6-platform-versions:1.7.4`; 不使用本地 Maven、消费端兼容表或本地平台插件源码覆盖。平台选择日志只允许一段。
- 本地 Gradle 验证优先 `--offline`, 用既有依赖缓存。CI 初次获取公开构建依赖与手动 LFS 物化需要网络, 不把它描述为离线验证。
- 保留签名约定。`sign.properties`、keystore、本地路径与密码不可提交或打印。Release 收集任务必须依赖 assembleRelease、要求有效签名、核对四个 ABI 包并追加 CRC32。
- `.so` 使用 Git LFS; 不将 LFS 指针当作实际库打包。常规 push/PR 只做 JVM 检查, APK job 手动或 tag 触发并按真实 LFS oid 缓存对象。
- README/CHANGELOG 只编辑 `.readme/lang_*.json`、`.changelog/lang_*.json` 及模板, 然后运行 `.python/generate_markdown.py` 与 `.python/check_markdown.bat`。保持十语言与应用内文档同步。
- 新能力同步 C++、插件 manifest、宿主 manifest、catalog、d.ts 与 HOST-API; 新请求字段缺失时提供兼容默认值, 优先保持 AIDL 事务号不变。宿主变更在宿主仓独立提交并记录提交号。

## 验证

本地基础命令:

```powershell
py .python/generate_markdown.py --check
py -m unittest discover -s .python -p 'test_*.py'
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:assembleDebug :app:assembleDebugAndroidTest
```

构建平台迁移额外运行:

```powershell
.\gradlew.bat --offline --no-daemon '-Djava.vendor=Eclipse Adoptium' '-Djava.vendor.version=Temurin-21.0.12.1+1' :app:assembleDebug :app:testDebugUnitTest
```

C++ 先用当前 `.cxx` 的 compile_commands.json 中 NDK clang 命令离线做 `-fsyntax-only`: 保留实际 `--target`、sysroot、include、宏和 `-std=c++20`, 去掉 `-c`/`-o` 及依赖文件输出参数, 再进 Gradle 构建。不要凭历史记录猜当前 NDK 路径。

高频 Android 冒烟组为以下 12 个类, 包前缀统一为 `io.github.supermonster003.autojs6.plugin.nodejs`:

1. SimpleRunSmokeTest
2. StreamingOutputSmokeTest
3. CooperativeCancelSmokeTest
4. SerialQueueSmokeTest
5. LongRunningLifecycleSmokeTest
6. NetworkDefaultOnSmokeTest
7. NpmEcosystemSmokeTest
8. ExecutionTimeoutSmokeTest
9. QueuedExecutionTimeoutSmokeTest
10. ExecutionModeLifecycleSmokeTest
11. DgramUdpSmokeTest
12. UnrestrictedFsSmokeTest

使用 `adb -s <serial> shell am instrument -w -e class <逗号分隔全类名> io.github.supermonster003.autojs6.plugin.nodejs.test/androidx.test.runner.AndroidJUnitRunner` 聚焦执行, 检查最终 `OK (...)`、断言失败与进程崩溃, 不仅依赖 adb 退出码。发布前至少做 arm64 真机与 x86_64 模拟器 SimpleRunSmokeTest; 16 KB 支持需先读设备 PAGE_SIZE 再跑实机用例。

触碰 builtin facade 或 require 链必须跑 NpmEcosystemSmokeTest 和 NodeRuntimePluginAndroidConformanceTest 全类。取消重启兜底、冷启动测量和 InspectorDebugSmokeTest 按改动单独执行。Manifest/PluginInfo 变更执行各自契约测试。样例/类型改动分别执行 verifyNodePluginExamples、typeCheckNodeTypescriptSmoke。

## 红线

- 完成标准是一条可用能力, 不新增 probe、能力目录、所有权策略、哈希锁或重型默认门禁。
- 不为未发生的异常预造处理; 针对复现问题做最小修复与必要回归。
- 文件系统受 Android 权限约束, `/proc`、`/sys`、`/dev` 硬边界保留; 不新增 hardened sandbox。
- 不实现设备端 npm registry 下载、native addon 加载、远程 inspector 监听或未经重新评估的 V8 startup snapshot。
- raw TypeScript 必须经宿主 Compiler 产出 JavaScript, 不恢复 regex stripping fallback。
- 不改写已发布 catalog/runtime-kit 快照, 不将未产出的 Node 24.x Android 版本标成已晋级。
