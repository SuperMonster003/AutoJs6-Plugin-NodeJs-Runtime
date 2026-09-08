# v1.3.0

###### 尚未发布

* `新增` Node.js 桥订阅通过既有回调推送传感器, WebSocket, UI, 悬浮窗与输入事件, 提供 on/once/off 监听, 有界事件队列及 drainEvents 兼容
* `新增` Node.js 运行时支持可选 idleExitMs 空闲退出与后续脚本重新连接, 提供 idleForMs 诊断, 默认保持常驻
* `新增` 全局 fetch, Request, Response, Headers, FormData 与 WebSocket 默认使用 Node 原生 Web API; autojs6:fetch 与 autojs6:websocket 显式模块继续提供宿主网络栈
* `新增` 新增带文件边界检查的原生 node:sqlite, 支持 CRUD, 事务与备份; 提供原生 node:test 报告器和可运行测试样例, 离线 npm corpus 扩展 zod, cheerio, date-fns, mqtt 与 ws
* `新增` process.stdin / readline 接收控制台输入, autojs6:host 接收执行级 JSON 消息, v3 postMessage 事务兼容旧宿主
* `新增` Node.js 截屏会话接入 Android 授权和既有前台服务, 支持图片句柄, PNG/JPEG/WebP 保存及停止或脚本退出时清理
* `新增` Node.js 图片句柄支持裁剪, 缩放, 灰度, 阈值, 找图及找色, 复用宿主图像后端, 返回独立图片并在执行结束时清理
* `新增` Node.js image.toBytes 通过文件描述符将 PNG 或 RGBA 像素传入原生 Buffer, 支持 JNI 与文件桥接, 在使用后, 超时或执行结束时回收附件
* `新增` Node device 接口提供实时构建, 屏幕, 电池和内存信息, 亮度控制及定时保持亮屏; media 支持设置音频流音量, 遵循 Android 权限
* `新增` autojs6:events 通过推送回调观察 Android 通知, 外部 Toast, 无障碍按键及屏幕和电池广播, 使用显式权限并自动清理订阅; Node 原生 events 保持兼容
* `新增` Node app 支持 Android 服务启动, 广播发送和已安装应用查询; dialogs 支持文本输入, 单选, 多选及随脚本清理的进度窗口, keys 提供无障碍系统操作
* `新增` Node recorder 支持带麦克风权限, 前台通知, 时长上限与脚本清理的 AAC 录音; ui.overlay 支持声明式属性更新, 拖动与事件推送
* `新增` Node 脚本可在两个独立运行时进程中并发执行, 共用 FIFO 队列并按执行任务路由取消和输入
* `修复` 修复长驻脚本的桥会话持续累积请求与响应记录的问题, 清理已完成请求, 仅保留最近 32 条响应并限制诊断体积
* `修复` 原生异步任务完成前提前生成成功结果, 导致异步错误与后续退出码丢失的问题; 终态改为跟随 Node 事件循环最终退出
* `优化` 实时桥默认使用 JNI/Binder 直通并经 Node 事件循环返回响应, 降低调用延迟, 保留可选文件回退与待处理调用上限
* `优化` Node.js stream, crypto, timers, util, node:test 等内建模块恢复原生导出, 保留文件系统边界与宿主目录策略
* `优化` 原生 worker 默认按 CPU 并行度运行 (最多 8 个), 继承执行的网络与文件开关, 支持请求级资源上限且池任务默认不设超时; CPU 与 WASM 样例改为真实多线程执行
* `优化` 维持原生 WASI 禁用以保留文件系统边界, 移除两个 disabled WASI 样例; 普通 WebAssembly 与 WASM worker 继续可用
* `优化` 屏幕 OCR 样例申请 Android 截屏授权并识别真实图片句柄; OCR 或条码插件不可用时返回可读 unavailable 错误, 识别失败仍按错误处理
