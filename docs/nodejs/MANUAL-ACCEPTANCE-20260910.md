# 2026-09-10 人工验收回执

用户在既有验收项目目录 `/sdcard/Scripts/nodejs-roadmap-acceptance-20260910/sample/nodejs/` 运行样例并回报结果。设备 QV770340J7 实读为 Sony XQ-DQ72 / Android 13 (API 33), 宿主 6.8.0 / build5279, Node 插件 1.3.0 / build146。以下人工回执与既有自动化回归共同补充 [Roadmap](../../Roadmap.md)。

## 截屏、找图与 OCR

`screenshot-find-image` 回报:

```text
sample.screenshot-find-image.size=1096x2560
sample.screenshot-find-image.point={"x":468,"y":1220}
sample.screenshot-find-image=PASS
```

设备 `wm size` 同为 1096x2560, 落盘 `screen.png` 的 PNG IHDR 尺寸也已核对。样例从截图中心裁剪 160x120 模板, 预期位置为 `(468,1220)`, 实际匹配偏差为 0 px。此回执补上 MediaProjection 人工授权、captureScreen、PNG 保存、裁剪、模板保存/回读和真实截图找图链。颜色和其他图像变换沿用已通过的真机固定像素回归, 不把本次找图日志描述成用户另行执行了所有图像方法。

`ocr-automation` 回报 `sample.ocr-automation=PASS`, 识别文本包含宿主界面的 `AutoJs6`、`文件`、`expected-output.txt` 和 `main.cjs`。本次使用实际 MediaProjection 图片句柄; 既有 OCR/Barcode 固定画面用例继续作为二维码和错误分支证据。

由此完成 M14.1/M14.2/M14.4 的待补人工链, `screenshot-find-image`、`ocr-automation` 与使用相同捕获/OCR 调用链的 pro-parity `screenshot-ocr` 晋为 stable。后者没有单独的用户运行日志, 晋级依据为公共调用链回执加既有识别回归及样例检查。稳定状态仍要求实际 Android 授权和对应识别/OpenCV 插件。

## 三秒麦克风录音

用户回报 `sample.audio-recording=PASS`, 输出文件为 `recording-1789035512887.m4a`。

| 字段 | 回执 |
|---|---|
| 自动停止原因 | `max-duration` |
| 配置时长 / 实际时长 | 3000 / 3156 ms |
| 文件大小 | 51,938 bytes |
| 最终 recording | `false` |
| MediaInfo 音轨 | AAC LC / `mp4a-40-2`, 单声道 |
| 采样率 / 码率 | 44.1 kHz / 128 kb/s |
| 解析时长 | 3 s 42 ms |

此回执补上 M15.4 的真实麦克风、非空文件和 MediaInfo 解析 Check。结合既有悬浮窗及资源释放回归, `audio-recording` 与复用相同 start/stop/read 链的 pro-parity `media-recorder` 晋为 stable, 后者没有单独的用户运行日志。麦克风权限及 Android 前台运行条件继续由实际系统状态决定。本轮未重复启动麦克风。

## host-events 的两个失败与修复

用户使用的 project.json 已正确声明 `node.timeoutMs: 65000` 及事件子权限。等待约 5 秒的 `ERR_AUTOJS6_SCRIPT_TIMEOUT` 来自宿主项目入口丢失配置: `NodeProjectScriptSourceFactory` 没有显式启动覆盖值, 而 `NodeProjectRunner.buildProjectRequest` 只使用覆盖值或 5000 ms 默认值。原设备错误中的 4998 ms 为派发后剩余预算。

新增宿主 `NodeProjectTimeoutInstrumentationTest` 从实际 project.json 创建项目启动请求。修复前在小米 ARM64 真机复现 `configured=65000 launchOverride=null request=5000`, 两个用例有一个断言失败。宿主提交 `36d1e8c52` 改为读取项目正数 timeout, 并保持显式覆盖优先及无配置默认值不变。修复后得到 `request=65000`, 实际 Node `setTimeout(..., 6500)` 脚本成功结束。QV770340J7 也已安装本次保留数据的 Debug 宿主 6.8.0 / build5279, 配合原 Node 1.3.0 / build146 完成 2/2, 不要求先升级 Node 插件。

音量加键后的 `ERR_AUTOJS6_NODE_QUEUE_CANCELLED` 与设备日志中的 `GlobalKeyObserver.onVolumeUp` 相隔 5~6 ms。实际偏好中音量加停止脚本和音量减控制录制均为开启状态; 宿主全局停止快捷键正常触发, 所以样例继续使用音量加键, 文档要求测试前暂时关闭该停止设置并在结束后恢复。本轮没有改动用户快捷键设置, 也没有把 ADB 注入当作实体按键回执。

`notification observer unavailable: Enable notification access ...` 表示 Android 通知读取授权未启用, 与 `require("autojs6:events")` 的正确模块入口无关。该来源被独立处理, 不妨碍按键用例。通知与外部 Toast 仍应各自保留真实事件日志。

| 本轮自动化检查 | 结果 |
|---|---|
| 小米 968e9f18, Android 15 ARM64: 新项目超时回归 | 2/2 |
| 同设备: Android events / 旧 input_observer / 宿主 bridge conformance | 5/5 + 6/6 + 5/5 |
| Sony QV770340J7, Android 13 ARM64: 新项目超时回归, 原 Node 1.3.0 | 2/2 |

小米首次扩展检查为 17/18: 既有 MediaInfo host dispatch 已包含 `countGet`, conformance 预期清单尚未同步; 宿主测试提交 `9e718797d` 更新这一预期后完整 18/18 通过。首次失败日志保留。构建为本地离线 Debug, 沿用定向 Node 测试 init script 排除两个已有 Console 测试编译阻塞, 并排除本轮 D8 报 `Invalid empty classfile` 的既有 `PythonRuntimeAndroidConformanceTest`; 不宣称完整宿主 AndroidTest 构建或所有宿主测试通过。

原始日志在本机 `build/m18-feedback-20260910/`: `timeout-before-fix.txt`, `host-fixed-xiaomi-tests.txt`, `host-final-xiaomi-tests.txt`, `host-fixed-sony-tests.txt` 及对应构建日志。M15.2 与 host-events 的 partial 状态保留, 等待按 [更新后的步骤](MANUAL-ACCEPTANCE.md#m152-android-事件观察者) 取得实体按键及独立事件来源回执。本轮没有启动临时 AVD。
