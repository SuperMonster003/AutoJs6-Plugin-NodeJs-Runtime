# v1.4.0

###### 2026/09/10

* `新增` MediaInfo 查询支持从 0 开始的 streamNumber, countGet 流计数以及用于单位, 说明和可读名称的 infoKind; Rhino 和 Node 保持默认第 1 条流的 TEXT 查询, 并协商插件扩展能力
* `修复` MediaInfo 与图片文件路径支持绝对路径, 父目录和合法文件名; 录音输出同步交由更新后的宿主按 Android 文件权限处理
* `修复` 桥能力未声明错误直接提示缺少的 node.permissions, 不再要求仅作诊断且不授予权限的 pro_compat_opt_in profile
* `修复` 项目向导不再将普通 fs 绝对路径和父目录路径误报为 FS_OUTSIDE_SCOPE, 实际文件访问交由 Android 判断
* `优化` 提供 Android 事件与 3 秒录音独立项目, 补齐截屏, OCR, 实体按键和 MediaInfo 的项目声明及人工验收步骤
* `优化` 截屏找图, 屏幕 OCR 与 3 秒 AAC 录音完成真机人工验收, 对应样例及复用相同调用链的 Pro 对齐片段转为稳定状态
* `优化` 事件验收样例提示暂时关闭宿主音量加停止快捷键; 配套宿主读取 project.json 的 node.timeoutMs, 避免较长等待脚本在约 5 秒后超时
