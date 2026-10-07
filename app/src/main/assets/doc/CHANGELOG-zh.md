******

### 发行历史

******

# v1.6.0

###### 2026/10/07

* `优化` fs, fs/promises 及 node:sqlite 模块改为直接使用 Node.js 原生实现, 移除运行时自设的 /proc, /sys, /dev 拒绝边界与路径校验包装层, 文件可达性完全由 Android 权限决定, 异步文件 API 经 libuv 线程池执行而不再阻塞事件循环, 路径错误返回 Node.js 原生错误码 (ERR_AUTOJS6_FS_NUL_BYTE, ERR_AUTOJS6_FS_PATH_ESCAPE, ERR_AUTOJS6_FS_SCOPED_PATH 及 ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED 不再由文件及数据库操作产生)
* `优化` process.cwd 与 process.chdir 改为 Node.js 原生行为, 脚本执行开始时以原生 chdir 进入工作目录, 相对路径由 Node.js 自行解析
* `优化` node:sqlite 的 ATTACH (含绑定参数及表达式形式的文件名), VACUUM INTO, 目录 PRAGMA 及 setAuthorizer 均为 SQLite 原生行为, enableLoadExtension 与 allowExtension 遵循 Node.js 原生状态检查
* `优化` 全部 Node.js 内建模块 (含 cluster, domain, repl, trace_events, wasi, constants, node:sea 等) 按 Node 自身的 module.builtinModules 清单直接返回原生导出, require, require.resolve, import, process.getBuiltinModule 与 module.isBuiltin 行为与桌面版一致, 仅保留 worker_threads, child_process, inspector 及原生网络模块的请求级门禁; node:wasi 随 /proc 边界的删除一并放开 (trace_events 在当前未启用 tracing 的运行库中与 Node 一样抛出 ERR_TRACE_EVENTS_UNAVAILABLE)
* `优化` process 对象仅保留 exit 的生命周期映射与 dlopen 的暂时拒绝, abort, kill, umask, binding, report, features 及 POSIX 身份调用均为 Node.js 原生行为; os 模块原生, homedir 与 tmpdir 通过执行环境变量 HOME/TMPDIR 指向工作目录, userInfo 返回真实的 Android 应用用户
* `优化` module 内建模块改为仅覆盖 createRequire 的 Node.js 原生对象, registerHooks, enableCompileCache, findPackageJSON 等可直接使用; 删除仅在禁用原生网络时可达的 http, https, net, tls, dns 受限实现, 禁用时直接返回 ERR_AUTOJS6_BUILTIN_DISABLED
* `优化` 插件信息 (INFO) 与运行时诊断的 supportedAbis 改为读取构建期写入清单的 org.autojs.plugin.contract.SUPPORTED_ABIS meta-data (需要 AutoJs6-Gradle-Platform-Versions 1.8.4 起的 nativeAlignment 插件), 表示插件构建支持的全部 ABI 而非已安装分包; meta-data 缺失时回退为已安装包内实际打包的 ABI, 手写的 ABI 常量已删除

# v1.5.7

###### 2026/10/05

* `优化` 插件中心图标采用统一工作台调整后的尺寸, 位置, 亮暗图稿与圆形底色, 保留可重建原稿和参数
* `优化` Android 系统应用信息图标与图标工作台共用图稿和亮暗底色, 保留插件中心透明图稿及现有启动器选项

# v1.5.6

###### 2026/09/19

* `新增` Node.js 致命错误支持生成诊断报告, 失败结果及 getRuntimeInfo 可读取崩溃原因, 堆用量, 调用栈和报告路径, maxOldGenerationSizeMb 选项支持设置单次执行的 V8 老生代堆上限
* `修复` require 加载大型 CommonJS 模块时因扫描过程占用过多堆内存而崩溃的问题
* `修复` AGP 9.1 构建时的 SDK XML v4 解析警告, 以及 JVM 单元测试误触发 APK 原生库对齐检查的问题 (共享构建插件 1.8.3)

# v1.5.5

###### 2026/09/18

* `修复` runtimeInfo 中能力目录版本及摘要过期的问题, 改为从运行时资源读取
* `修复` Worker 的 eval 模式将代码字符串误当作文件路径的问题
* `优化` 能力目录按实际运行时更新, TypeScript 宿主编译标为稳定, 移除已废弃的类型剥离及未实现的执行模式
* `优化` autojs6:profile 诊断信息按实际能力更新, 明确文件权限, process, Worker, 进程池, WASI 及原生扩展的支持范围
* `优化` 完善 ESM, 动态导入, Wasm 及桌面兼容性示例, 对照测试改为实际执行, 移除仅打印元数据的依赖安装示例 (编译缓存示例标为不适用)
* `优化` require/import 及 Worker 支持绝对路径, 上级目录及 file: URL, 文件访问遵循 Android 权限 (node_modules 从工作区查找, /proc, /sys, /dev 仍不可访问)
* `优化` Worker 消息及文件监听遵循 Node.js 原生限制, 移除额外的消息大小, 排队数量, 监听器及事件频率限制
* `优化` Worker 中的 process.exit 仅结束当前线程, process.getBuiltinModule 按 Worker 模块白名单开放
* `优化` Worker 支持通过工作区 node_modules 解析包名, 包含 exports/main/index 及包自引用
* `优化` Worker 支持动态 import, 包含相对或绝对路径, file: URL, 工作区包, 允许的内置模块及 JSON 导入属性
* `优化` 实体按键及通知访问示例完成真机验收, host-events 示例标为稳定
* `优化` scheduled-node-task 示例支持显示生命周期策略及保留定时任务, 补充 scheduled 执行模式测试
* `优化` scheduled 执行模式完成宿主 WorkManager 验证, 能力目录标为可用
* `优化` media.play 支持返回可控制的播放会话, media_store 支持受权限约束的媒体库读写, 兼容层补充 playMusic 等 Rhino 别名 (需要配套宿主及相应媒体能力授权)
* `优化` 媒体播放及媒体库示例完成宿主集成验证并标为稳定, 发布 1.5.5 能力目录快照供宿主核对
* `优化` fs 的流选项, 递归监听, 复制过滤, glob, readableWebStream 及文件类型构造器遵循 Node.js 24 原生行为, 移除额外选项拦截
* `优化` 递归 readdir/opendir 移除 4096 条目上限, readlink/chmod/chown/utimes 支持绝对路径, 普通文件错误保留 Node.js 错误码 (仍执行路径安全检查)
* `优化` 移除运行时自设的模块大小及数量限制, CommonJS 入口路径及 fs.mkdtemp 的路径和编码行为与 Node.js 保持一致
* `优化` node:sqlite 通过原生授权回调校验 ATTACH/VACUUM INTO 的路径, 支持 file: URI 及空临时库, 保留 /proc, /sys, /dev 边界限制 (仍拒绝通过绑定参数或表达式指定 ATTACH 文件名)
* `优化` 宿主桥接调用超出并发窗口时按顺序排队, 图像句柄及网络连接配额统一由宿主控制, 移除运行时重复限制及对应策略字段
* `优化` 桥接 fetch/WebSocket/axios 的超时, 大小及队列限制统一由宿主控制, 运行时最多跟随 20 次重定向, policy 改用 limitsEnforcedBy 描述限制来源
* `优化` opendir 返回原生惰性 fs.Dir, 保留调用者的路径写法, rm/rmdir 移除额外根目录限制并遵循 Node.js 与 Android 行为 (仍检查 NUL 字节及路径越界)
* `优化` 模块传输预算采用宿主公布的限制, fetch 响应体支持文件描述符传输, 超大 Binder 回复立即返回 ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED (需要配套宿主)
* `优化` fetch 请求正文及 WebSocket 消息超过 256 KiB 时支持文件描述符传输, 大小由宿主策略限制 (需要宿主声明 bridgeRequestBinaryTransport=pfd)
* `优化` 桥接 fetch 的 Response.url 返回最终响应地址, 避免始终为空字符串
* `优化` Java 互操作统一采用宿主提供的类及成员白名单, 支持对象句柄, getStatic/describe 及成员异常回传
* `优化` npm 同名包优先规则仅用于 axios/colors/mime/nanoid/opencc/undici 等替代模块, 保留 AutoJs6 专用模块, 运行时模块裸名支持 ESM 导入, Packages 代理支持 getStatic/describe
* `优化` 运行时进程异常退出时回报系统记录的低内存或原生崩溃原因, 结果附带 slotExit, getRuntimeInfo 提供 lastSlotExit (需要 Android 11+, 低版本提示原因不可用)

# v1.5.4

###### 2026/09/17

* `修复` 嵌入执行输出多余 ExperimentalWarning 提示的问题, 保留警告事件, 普通警告, 弃用警告及错误 (不改变终端行为)
* `优化` 文件流, gzip/deflate/Brotli 压缩流及基础 VM 执行转为正式支持, Debug Inspector 支持显式启用且仅监听 localhost

# v1.5.3

###### 2026/09/16

* `优化` Android 17 本地网络授权移至插件中心启用流程及插件设置, 移除启动器授权页面 (未授权时保持关闭并跳过自动启动)
* `优化` 适配 Android 17 (SDK 37), 提供插件独立的本地网络权限控制及恢复引导

# v1.5.2

###### 2026/09/15

* `优化` compileSdk 升级至 37 (Android 17), targetSdk 保持 36

# v1.5.1

###### 2026/09/15

* `修复` Node.js 致命错误及警告仅写入 logcat, 导致终端静默退出的问题
* `优化` Node.js 启用 small-icu, 支持 Intl 及正则 Unicode 属性转义, corepack 可运行 pnpm 11/Yarn Berry (内置英文区域数据, 可通过 NODE_ICU_DATA 加载完整 ICU 数据)

# v1.5.0

###### 2026/09/14

* `新增` node/npm/npx/corepack/yarn/pnpm 终端启动器, 支持各 ABI 及 16 KB 内存页
* `新增` 内置 Node.js 24.21.0 官方发行包中的 npm 11.19.0 及 corepack 0.36.0, 通过插件信息及 runtimeInfo 声明终端能力

# v1.4.2

###### 2026/09/13

* `修复` 插件信息报告安装包未包含的原生 ABI 的问题
* `修复` 插件信息中的构建日期受构建环境语言影响, 未统一使用英文格式的问题
* `修复` 发行包与归档版本信息不一致的问题
* `修复` Android 7 的工作目录文件兼容性问题 (保留文件描述符隔离)

# v1.4.1

###### 2026/09/13

* `优化` 构建阶段校验 64 位原生库的 16 KB 内存页对齐及 Manifest 配置, 并输出校验报告
* `优化` 统一宿主激活流程, 插件信息及多语言文档, 并完善签名发布校验

# v1.4.0

###### 2026/09/10

* `新增` MediaInfo 查询增加 streamNumber 流序号, countGet 流计数及 infoKind 信息类型 (流序号从 0 开始, 默认查询第 1 条流的 TEXT 信息)
* `修复` MediaInfo, 图像及录音路径的额外限制问题, 文件访问统一交由配套宿主按 Android 权限处理
* `修复` 缺少桥接能力时错误要求配置 pro_compat_opt_in 的问题, 改为提示所需 node.permissions
* `修复` 项目向导将 fs 绝对路径及上级目录误报为 FS_OUTSIDE_SCOPE 的问题
* `优化` 补充 Android 事件及 3 秒录音示例项目, 完善截屏, OCR, 实体按键及 MediaInfo 的声明和验收说明
* `优化` 截屏找图, 屏幕 OCR 及 3 秒 AAC 录音示例完成真机验收, 对应示例及 Pro 兼容片段标为稳定
* `优化` 事件示例增加音量加停止快捷键提示, 宿主支持读取 node.timeoutMs, 避免长时间等待的脚本提前超时

# v1.3.0

###### 2026/09/09

* `新增` 传感器, WebSocket, UI, 悬浮窗及输入事件推送, 支持 on/once/off 及 drainEvents
* `新增` idleExitMs 空闲退出选项及 idleForMs 诊断信息, 支持后续脚本重新连接 (默认常驻)
* `新增` 全局 fetch, Request, Response, Headers, FormData 与 WebSocket 默认使用 Node 原生 Web API; autojs6:fetch 与 autojs6:websocket 显式模块继续提供宿主网络栈
* `新增` node:sqlite 数据库及 node:test 报告器, 支持数据库增删改查, 事务及备份, 补充 zod/cheerio/date-fns/mqtt/ws 离线示例
* `新增` process.stdin/readline 控制台输入及 autojs6:host 执行级 JSON 消息, postMessage v3 兼容旧版宿主
* `新增` Node.js 截屏会话接入 Android 授权和既有前台服务, 支持图片句柄, PNG/JPEG/WebP 保存及停止或脚本退出时清理
* `新增` Node.js 图片句柄支持裁剪, 缩放, 灰度, 阈值, 找图及找色, 复用宿主图像后端, 返回独立图片并在执行结束时清理
* `新增` image.toBytes 方法, 支持通过文件描述符传输 PNG 或 RGBA 像素至 Buffer, 并自动回收传输附件
* `新增` device 设备, 屏幕, 电池及内存信息查询, 亮度及保持亮屏控制, media 音量设置 (遵循 Android 权限)
* `新增` autojs6:events 通知, 外部 Toast, 无障碍按键及屏幕和电池广播监听, 支持自动清理订阅
* `新增` app 服务启动, 广播及应用查询, dialogs 输入, 单选, 多选及进度对话框, keys 无障碍系统操作
* `新增` recorder AAC 录音及 ui.overlay 浮窗更新, 拖动和事件推送, 录音支持权限申请, 前台通知, 时长限制及自动清理
* `新增` 两个独立进程并发执行 Node.js 脚本, 支持按执行任务取消及发送输入
* `修复` 长期运行的脚本持续累积桥接请求及响应记录的问题, 完成后及时清理并仅保留最近 32 条响应
* `修复` 原生异步任务完成前返回成功结果, 导致后续错误及退出码丢失的问题
* `修复` OpenSSL STORE 密钥 URL 绕过文件系统限制的问题 (请通过 node:fs 读取密钥字节)
* `优化` 宿主桥接默认使用 JNI/Binder 传输以降低延迟, 保留文件传输回退及待处理调用上限
* `优化` Node.js stream, crypto, timers, util, node:test 等内建模块恢复原生导出, 保留文件系统边界与宿主目录策略
* `优化` Worker 默认按 CPU 并行度运行且最多 8 个, 支持执行级网络及文件开关和请求级限制, 池任务默认不设超时
* `优化` 移除两个已禁用的 WASI 示例, 保留原生 WASI 限制及普通 WebAssembly/WASM Worker 支持
* `优化` 屏幕 OCR 示例使用真实截屏及图像句柄, 缺少 OCR 或条码插件时返回 unavailable 错误
* `优化` Node.js 脚本支持异步启动及完成回调, 长期运行时释放 Binder 线程, 兼容旧版同步宿主
* `依赖` 升级 Node.js 版本 24.5.0 -> 24.21.0, 三个 ABI 均从源码构建

# v1.2.0

###### 2026/08/29

* `新增` mediainfo.capabilities 能力查询及 v1/v2 快照选择, read 支持指定 schema (默认保持宿主 Node v1 格式)
* `新增` 模块源码协议 v3, 支持按需编译运行中创建的 .ts/.mts/.cts 文件, 校验源码大小及摘要, 保留诊断及 Source Map (独立编译超时为 30 秒)
* `新增` Android 权限范围内的桌面式文件访问 (仍不可访问 /proc, /sys, /dev)
* `新增` accessibility.swipe/gesture 手势接口, 需要声明 accessibility.gesture 能力
* `新增` 脚本超时选项, 覆盖排队及执行过程, 超时返回 ERR_AUTOJS6_SCRIPT_TIMEOUT (默认不设超时)
* `新增` 默认启用 dgram (UDP) 与 http2, 并对 trace_events 返回明确的禁用错误
* `新增` 支持 Debug 构建显式开启本地 inspector 调试, 仅监听 localhost 并通过 `adb forward` 连接
* `新增` 插件中心无界面激活入口及使用说明, 禁用应用数据备份
* `修复` 缺少宿主编译产物时仍尝试执行 TypeScript 的问题, 补充动态导入映射及调用栈映射
* `修复` ESM 循环重导出时可变导出未实时更新的问题, 改用 V8 原生链接器
* `修复` AutoJs6 兼容模块的 ESM 导入及 TypeScript 后缀探测问题 (保留已安装 npm 包优先规则)
* `优化` 移除基于正则的 TypeScript 类型剥离及其选项, .ts/.mts/.cts 统一使用宿主编译产物
* `优化` 宿主及插件 v2 契约, 能力清单和职责说明
* `优化` 将 Node.js 样例, TypeScript 类型声明, 项目向导, 运行时默认值及宿主对齐校验统一归属插件仓库, 移除宿主侧 Gradle 开关与重复开发资产
* `优化` npm 兼容性验证扩展至 15 个包, 包含 axios/express/nanoid/p-limit/yocto-queue
* `优化` 执行模式统一使用 executionMode, 废弃无实际作用的 runtimeAdapter
* `优化` 移除仅输出固定状态的示例, 类型声明明确截图, 图像分析及录音尚无宿主实现

# v1.1.0

###### 2026/08/18

* `新增` stdout/stderr 实时输出及协作式取消
* `新增` 串行执行队列, 支持最多 3 个等待任务及长期运行的脚本
* `新增` Node.js 原生网络模块, worker_threads 及 child_process, 完成 10 个常用纯 JavaScript npm 包的兼容性验证
* `优化` 无需工作区归档的直接执行及模块源码协议 v1/v2 协商, 错误输出包含错误码及 JavaScript 调用栈

# v1.0.0

###### 2026/07/18

* `新增` Node.js 运行时插件, 插件及引擎标识为 nodejs, 运行时版本标识为 node24_5
* `新增` 通过 libnode.so 和 `libautojs6-node.so` 提供 Node.js 24.5.0 原生运行时
* `新增` 独立常驻进程, 每次执行创建独立的 V8 上下文及 Node.js 环境
* `新增` 支持通过 org.autojs.plugin.INFO 发现插件信息, 并通过 org.autojs.plugin.nodejs.RUNTIME 调用运行时服务
* `新增` 支持 CommonJS/ESM 源码, 模块源码, 工作目录, 沙盒根目录, 环境变量, stdout/stderr 结果回传和运行时预热
* `新增` 工作区归档传输 v2, 支持输入映射, 私有工作区, 输出回写及删除记录 (不扫描宿主沙盒)
* `新增` 单任务执行及重启进程式取消, 忙碌时返回 ERR_AUTOJS6_NODE_PLUGIN_BUSY
* `新增` 宿主能力桥接及 autojs6:host-app-info/device-info/engine-info/lifecycle-config/bridge-permissions 等运行时模块
* `新增` arm64-v8a/armeabi-v7a/x86_64 独立安装包及通用安装包
* `新增` 分包及通用 APK 输出均随 Node.js 运行库打包 `libc++_shared.so`
* `新增` `sample/nodejs` 示例项目, Node 解析诊断工具和运行时构建计划校验工具
* `新增` 插件信息, 使用说明, README 及 CHANGELOG 支持 10 种语言 (西班牙语/法语/俄语/阿拉伯语/日语/韩语/英语/简体中文/香港繁体/台湾繁体)
* `修复` 重启进程式取消期间, 运行时进程回收时可能提交不完整工作区快照的问题
* `修复` 请求校验或工作区准备失败时, 归档文件描述符可能泄漏的问题
* `优化` 运行时诊断信息, 覆盖 ABI, 能力, 常驻状态, 任务准入, 取消及独立进程
* `优化` 分阶段耗时诊断, 覆盖源码准备, 启动, 执行, 结果生成及清理, 区分已跳过与不适用状态
