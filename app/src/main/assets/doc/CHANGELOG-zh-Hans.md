# v1.3.0

###### 尚未发布

* `新增` Node.js 桥订阅通过既有回调推送传感器, WebSocket, UI, 悬浮窗与输入事件, 提供 on/once/off 监听, 有界事件队列及 drainEvents 兼容
* `新增` Node.js 运行时支持可选 idleExitMs 空闲退出与后续脚本重新连接, 提供 idleForMs 诊断, 默认保持常驻
* `新增` 全局 fetch, Request, Response, Headers, FormData 与 WebSocket 默认使用 Node 原生 Web API; autojs6:fetch 与 autojs6:websocket 显式模块继续提供宿主网络栈
* `修复` 修复长驻脚本的桥会话持续累积请求与响应记录的问题, 清理已完成请求, 仅保留最近 32 条响应并限制诊断体积
* `修复` 原生异步任务完成前提前生成成功结果, 导致异步错误与后续退出码丢失的问题; 终态改为跟随 Node 事件循环最终退出
* `优化` 实时桥默认使用 JNI/Binder 直通并经 Node 事件循环返回响应, 降低调用延迟, 保留可选文件回退与待处理调用上限
* `优化` Node.js stream, crypto, timers, util, node:test 等内建模块恢复原生导出, 保留文件系统边界与宿主目录策略
* `优化` 原生 worker 默认按 CPU 并行度运行 (最多 8 个), 继承执行的网络与文件开关, 支持请求级资源上限且池任务默认不设超时; CPU 与 WASM 样例改为真实多线程执行
