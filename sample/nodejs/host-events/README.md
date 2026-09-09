# host-events

在 AutoJs6 中运行整个项目, 保留旁边的 project.json。先在 Android 设置中启用
AutoJs6 无障碍服务; 日志出现 READY 后, 在 45 秒内按一次设备实体音量加键。
收到 keyCode=24、action=down 才输出 PASS。ADB 注入不作为实体键验收。

Android 通知观察另外需要系统的“通知使用权”; Toast 观察依赖无障碍服务,
可在等待期间切换到其他应用触发 Toast。本样例会分别输出这些源的权限错误,
不会把它们打印为通过。PASS 只表示实体音量键验收。

`require('events')` 和 `require('node:events')` 是 Node 原生 EventEmitter。
Android 观察者使用 `require('autojs6:events')`, 注册函数需要 await。

The sample remains partial until a physical key receipt is recorded. Notification
and Toast access are independent Android permissions. All subscriptions close on exit.

完整步骤见 [人工验收说明](../../../docs/nodejs/MANUAL-ACCEPTANCE.md)。
