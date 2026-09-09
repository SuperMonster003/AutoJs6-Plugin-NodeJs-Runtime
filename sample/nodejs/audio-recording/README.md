# audio-recording

这是会实际打开麦克风的 3 秒人工测试。先安装并在 AutoJs6 中启用 MediaInfo 插件,
在 Android 设置中允许 AutoJs6 使用麦克风, 保持宿主在前台, 然后运行整个项目。
看到 Recording 提示后说一句短话。项目声明只授予桥能力, 不会自动授予系统权限。

输出文件名为 recording-<时间戳>.m4a, 日志会打印准确路径、字节数和 MediaInfo
音轨信息。脚本结束后项目文件回传完成, 可在项目目录中找到录音。
PASS 要求真实录音非空且 MediaInfo 能解析出音轨; 权限拒绝不会算通过。

The sample records three seconds of microphone audio when explicitly run. It stays
partial until the manual recording and metadata receipt are recorded. Cleanup runs
on success, failure and script termination.

完整步骤见 [人工验收说明](../../../docs/nodejs/MANUAL-ACCEPTANCE.md)。
