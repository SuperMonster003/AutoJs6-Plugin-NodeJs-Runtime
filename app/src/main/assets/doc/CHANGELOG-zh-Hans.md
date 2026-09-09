# v1.4.0

###### 尚未发布

* `修复` MediaInfo 与图片文件路径支持绝对路径, 父目录和合法文件名; 录音输出同步交由更新后的宿主按 Android 文件权限处理
* `修复` 桥能力未声明错误直接提示缺少的 node.permissions, 不再要求仅作诊断且不授予权限的 pro_compat_opt_in profile
* `优化` 提供 Android 事件与 3 秒录音独立项目, 补齐截屏, OCR, 实体按键和 MediaInfo 的项目声明及人工验收步骤
