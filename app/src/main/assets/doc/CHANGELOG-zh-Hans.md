# v1.2.0

###### 2026/08/29

* `新增` `mediainfo` facade 新增 `capabilities()`, 且其可调用入口与 `read()` 支持显式选择插件快照 v1/v2; 省略 schema 时继续返回原有宿主 Node v1 快照
* `新增` 新增 module-source provider v3, 通过绑定精确字节数与 SHA-256 的有界 PFD 按需编译运行中创建的 `.ts/.mts/.cts`, 使用独立 30 s 编译预算, 稳定拒绝路径逃逸, 符号链接和歧义, 并保留宿主 TypeScript 诊断与 Source Map 栈映射
* `新增` 在 Android 应用权限范围内启用桌面式文件系统访问, 同时继续拒绝 `/proc`、`/sys`、`/dev`
* `新增` 支持 `accessibility.swipe` 与 `accessibility.gesture`, 并由独立能力 `accessibility.gesture` 门禁
* `修复` raw TypeScript 在宿主未提供编译产物时改为 fail-closed, 补齐快照动态 import 映射并归一化生成/导入栈帧
* `修复` 以 V8 native linker 替换快照式 partial ESM adapter, 修复循环 re-export 中可变导出不会实时更新的问题
* `修复` 修复 AutoJs6 兼容 facade 的 ESM 导入与不存在的 TypeScript 后缀探测, 同时保持已安装 npm 包优先
* `优化` 删除基于 regex 的 legacy TypeScript 类型剥离 fallback 及其请求开关, raw `.ts/.mts/.cts` 现始终要求宿主编译产物
* `优化` 对齐宿主/插件 v2 合约, 能力清单与 plugin-only 运行时职责边界
* `优化` 将 Node.js 样例, TypeScript 类型声明, 项目向导, 运行时默认值及宿主对齐校验统一归属插件仓库, 移除宿主侧 Gradle 开关与重复开发资产
