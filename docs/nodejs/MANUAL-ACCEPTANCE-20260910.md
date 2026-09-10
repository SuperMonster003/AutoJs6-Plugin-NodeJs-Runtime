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
