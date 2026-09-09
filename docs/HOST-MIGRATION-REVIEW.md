# 宿主 Node 文件迁移复查

复查日期: 2026-09-10。迁移前宿主提交: `0757cb296e440b9e607affc0efe03776666bef3c`。
本记录对应 [Roadmap M19](../Roadmap.md), 说明本次迁移及仍有宿主消费者的文件。

本轮迁移 7 份使用指南和 11 份历史文档。宿主 `docs/nodejs` 的 32 个 Markdown 文件
从 302,415 字节降为 29,715 字节, 减少 272,700 字节 (90.2%)。其中 18 个原路径改为
短跳转, 其余 14 份宿主集成、测试和维护文档保留。数字来自 UTF-8 文件内容, 不含 Git
对象, 不是 APK 体积变化。文档原本不参与 Android 资源或原生代码打包。

| 路径/范围 | 实际消费者与职责 | 本轮结果 |
|---|---|---|
| `QUICK_START.md`, `PROJECT_WIZARD.md`, `EXAMPLES.md` | Node 项目用户、插件拥有的样例与桌面向导 | 正文迁入本仓 [docs/nodejs](nodejs); 宿主保留跳转 |
| `COMPATIBILITY_PROFILE.md`, `MIGRATION_FROM_RHINO.md`, `ERROR_CATALOG.md`, `SECURITY_MODEL.md` | 运行时行为与错误解释 | 迁入本仓, 更新 Node 24.21、宿主 Compiler、async/await、events 命名、文件路径和 Debug Inspector 的现状 |
| `COMPLETION_ROADMAP.md`, `INTEGRATION_STATUS_PRO9_COMPARISON.md`, `COMPATIBILITY_NOTES_PRO_AUTOX.md`, `NODE_PROFILE_V1_1_USER_DRAFT.md`, `NODE_DEPLOYMENT_SHARED_CONTEXT.md` | 旧内嵌运行时方案和开发历史 | 迁入 [history](nodejs/history), 标记来源提交和唯一现行 Roadmap |
| `R2_ACCEPTANCE_SAMPLING.md` 至 `R5_ACCEPTANCE_SAMPLING.md`, `R6_ACCEPTANCE_EVIDENCE.md`, `RHINO_AUGMENT_COMPATIBILITY_MATRIX.md` | 历史验收和兼容性记录 | 一并归档; 历史源代码相对路径仍按原宿主解释, 文档导航指向现行位置 |
| 宿主其余 14 份 `docs/nodejs/*.md` | Binder 集成、Doctor/崩溃诊断、宿主发布/回滚与设备验收 | 保留宿主维护, 活跃使用指南链接改指插件 |
| 宿主 `plugin-api/nodejs-api` | Binder 客户端与请求构建 | 保留编译镜像: 5 个 AIDL、4 个 Kotlin 常量文件、1 个契约测试及 2 个构建文件; 权威源仍为插件 |
| `NodeBridgeProtocol.kt`, `NodeJsHostCapabilityBroker.kt` | Android 能力执行、资源清理和插件回调 | 留在宿主; 原始 Android 对象在宿主使用 |
| `NodeBridgePermissionManifest.kt` | Node Host broker 与 `McpHostCapabilityBroker` 授权声明 | 不直接删除。插件有独立 Java 预检; 默认值和诊断可在 M19.3 通过既有契约归并 |
| `NodeBridgeFileScope.kt`, `NodeBridgeSamplesBridge.kt`, `NodeBridgeSampleCatalog.kt` | MCP files、`app.listSamples/readSample` 和宿主 Android assets | 保留共享 Android 能力; 它们读取宿主样例资源, 不是插件 `sample/nodejs` 的副本 |
| `NodeProfileSelectionDiagnostics.kt` | `NodeDoctorReportGenerator`、`NodePrivacyDisclosurePolicy` | 仍有实际消费者; M19.3 从插件取得当前状态后再删除重复判断 |
| `NodeProjectRuntimeCompatibilityDescriptor.kt` | `NodeProjectLaunchDescriptor`、`AssetsProjectLauncher`、Doctor | 保留启动描述符兼容读取; 改读插件默认值需验证旧描述符与旧宿主 |
| 宿主路由、工作区归档、模块源 Provider、Compiler 路由 | 脚本入口、项目文件回传、TypeScript 编译 | 属于宿主跨应用协调职责, 保留 |
| 宿主 Gradle、Settings 与 API 模块构建 | Android 构建及 API 镜像编译 | 未发现仍需迁移的 libnode/JNI、Node 构建开关或样例/类型/向导校验脚本; 这些资产此前已迁到插件 |

宿主主源码中以 `Node` 开头的 Kotlin 文件有 79 个。前缀不代表文件必须移动:
大部分是路由、Android provider 或跨应用协议消费者。本轮没有为了减少文件数移动共享
provider, 也没有引入第二套跨仓清单或默认构建门禁。

审计还发现桌面项目向导的 `FS_OUTSIDE_SCOPE` 静态提示与当前 Android 文件访问行为不符,
已在后续 M20.2a 修正, 见 [PROJECT_WIZARD](nodejs/PROJECT_WIZARD.md)。进一步精简 profile/兼容性
描述符、权限默认值和资源策略分别留在 M18.2、M19.3、M20.2, 按真实调用和兼容性回归推进。

验证: 18 份宿主跳转都对应本仓正文; 7 份活跃指南的本地 Markdown 链接可解析; 历史档案
注明原仓路径语义。宿主构建脚本不消费迁走文档。49 个样例与 38 个声明模块的既有检查
通过, 事件/录音样例保持人工验收待办。双仓分别提交迁移, 提交号记入 Roadmap。
