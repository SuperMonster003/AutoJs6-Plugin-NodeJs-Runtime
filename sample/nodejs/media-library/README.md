# media-library

这是会写入并清理共享媒体库的人工测试。项目通过 media_store 模块访问 Android MediaStore:
capabilities() 报告集合、列与权限状态; insert() 把项目自带的 tone.wav 复制成一条
Music/AutoJs6Node/ 下的音频记录; query() 用结构化过滤 (startsWith + ownedOnly) 和排序
找回它; update() 改文件名 (MediaProvider 自行推导标题等元数据列, 桥不接受 title); exportFile() 把记录内容导出到项目目录的 exported-tone.wav;
scanFile() 请求系统索引一个文件; 最后 delete() 删除记录并用 get() 确认为 null。

项目声明 media、media.library 与 media.library.mutate 三个桥能力。Android 10 及以上
按分区存储规则只能修改 AutoJs6 自己创建的记录 (mutatePolicy 为 app_owned_items_only),
Android 9 及以下则需要在系统设置中授予存储权限。脚本不会提交任何 SQL, 过滤器由宿主
编译为参数化查询; 游标、Uri 等原生对象不会跨桥。

The sample inserts, queries, updates, exports, scans and deletes an app-owned MediaStore
audio item; cleanup runs even when a step fails. The expected output on Android 9 and
below differs in the mutatePolicy field.

完整步骤见 [人工验收说明](../../../docs/nodejs/MANUAL-ACCEPTANCE.md)。
