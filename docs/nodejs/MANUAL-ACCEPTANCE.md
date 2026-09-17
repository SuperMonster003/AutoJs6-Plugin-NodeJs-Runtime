# Node 截屏、事件与录音人工验收

适用于 v1.3.0 和带有 M14/M15 provider 的宿主, 例如本次联调宿主提交 0757cb296。
这三个流程需要 Android 的真实用户授权或硬件动作, 项目声明不能替代系统权限。

## 先按项目运行

将下面各样例的整个目录复制到设备的 AutoJs6 工作目录, 保留 project.json、
package.json 和 main.cjs。在 AutoJs6 文件列表中进入项目, 使用项目运行入口。
只把 JS 粘贴到远程 Untitled 单文件中执行, 不会带上旁边项目的 node.permissions。
不需要 npm install。

| 验收 | 本仓目录 | Android / 外部条件 |
|---|---|---|
| 截屏、裁剪、模板匹配 | sample/nodejs/screenshot-find-image | 系统整屏录屏授权, OpenCV 插件 |
| OCR | sample/nodejs/ocr-automation | 系统整屏录屏授权, 可用 OCR 插件 |
| 实体按键、事件入口 | sample/nodejs/host-events | AutoJs6 无障碍; 通知观察另需通知使用权 |
| 3 秒录音及 MediaInfo | sample/nodejs/audio-recording | AutoJs6 麦克风权限, MediaInfo 插件, 宿主在前台 |
| scheduled 执行模式 (WorkManager 定时运行) | sample/nodejs/scheduled-node-task | 宿主 WorkManager 定时运行器; 设备保持可运行后台任务 |

## M14: 截屏与 OCR

截屏项目中的配置是:

```json
{
  "name": "screenshot-find-image",
  "type": "node",
  "main": "main.cjs",
  "node": {
    "timeoutMs": 150000,
    "permissions": ["image", "screen_capture"]
  }
}
```

桥 API 返回 Promise, 须等待结果:

```js
"nodejs";
(async () => {
  const images = require("images");
  let capture;
  try {
    await images.requestScreenCapture({ timeoutMs: 120000 });
    capture = await images.captureScreen();
    await images.saveImage(capture, "screen.png");
    console.log("screen=" + capture.width + "x" + capture.height);
  } finally {
    if (capture) await images.recycle(capture);
    await images.stopScreenCapture();
  }
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
```

系统出现录屏授权窗口时, 手工选择“整个屏幕”并允许。Android 新版本可能默认
选择单个应用, 此选项不满足“PNG 尺寸等于整屏分辨率”的 Check。脚本结束后检查
screen.png; 完整 screenshot-find-image 样例还输出模板匹配位置并生成 template.png。
OCR 项目多声明 `ocr`, 请捕获带有已知文字的宿主界面, 对照实际识别文本。
授权拒绝、缺少 OCR/OpenCV 或 skipped 输出均不算完整验收通过。

报错中的 `pro_compat_opt_in` 是旧 profile 诊断文字; 仅设置 profile 不能补齐
缺失声明。当前要声明 screen_capture, 图像操作还需 image。

## M15.2: Android 事件观察者

```js
const nativeEvents = require("events"); // Node 原生 EventEmitter, typeof 为 function
const events = require("autojs6:events"); // Android 观察者
await events.observeKey();
await events.observeNotification();
await events.observeToast();
```

三项分别需要 `events + events.key`、`events + events.notification` 和
`events + events.toast`。host-events 样例已声明这些能力并处理独立来源的权限错误。
按以下步骤检查实体按键:

1. 启用 AutoJs6 无障碍, 并在 AutoJs6 设置的 "脚本运行" 中暂时关闭 "使用音量加键控制脚本运行"。
   该设置开启时, 音量加键会先触发宿主停止脚本, 结果可能是 `ERR_AUTOJS6_NODE_QUEUE_CANCELLED`。
2. 从 host-events 项目入口运行, 日志出现 READY 后在 45 秒内按设备实体音量加键。
   应看到 keyCode=24、action=down 和 `sample.host-events=PASS`。脚本不会吞掉按键。
3. 测试结束后恢复原来的音量键控制设置。不要直接改用音量减键, 它可能绑定了宿主录制功能。

项目中的 `node.timeoutMs: 65000` 是正确配置。2026-09-10 修复前的宿主项目启动路径
没有读取此字段, 导致等待约 5 秒就超时, 错误中的 4998 ms 是扣除派发耗时后的剩余预算。
需要安装包含宿主提交 `36d1e8c52` (`NodeProjectRunner` 超时修复) 的版本;
仅升级 Node 插件不能修复此入口。原 Node 1.3.0 插件可配合修复后的宿主继续使用。
修复后超时优先级为启动请求显式覆盖值、project.json 的正数 node.timeoutMs、既有的 5000 ms 默认值。

通知使用权在系统特殊应用访问设置中单独启用; 开启观察后可从另一应用发通知。
Toast 观察同样可在等待期间从其他应用触发。保留各来源的实际日志, 不把某一源的
PASS 代替其余来源的检查。

2026-09-17 回执: 实体按键与通知访问授权检查已由用户按上述步骤完成, 结果 PASS; host-events 样例转为 stable。

## M15.4: 录音与 mediainfo

运行 audio-recording 项目前, 确認现场可以录音, 手工授予 AutoJs6 麦克风权限,
并安装、启用 MediaInfo 插件。项目会录制 3 秒, 自动停止并解析文件。
检查结果中的 byteCount > 0 和实际音轨信息, 结束后确认录音通知消失。

只读取已有文件时, 可以使用以下 main.cjs, 项目 node.permissions 声明
`["media", "media.metadata"]`:

```js
"nodejs";
(async () => {
  const mediainfo = require("mediainfo");
  const info = await mediainfo.read("./recording-实际时间戳.m4a");
  console.log(JSON.stringify(info, null, 2));
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
```

`typeof mediainfo === "function"` 是正常的, 它是可调用的模块对象, 同时有 read/get。
文件名必须与实际文件完全一致; 扩展名不是 API 的必选条件, 但漏写实际存在的 .m4a
会指向另一个路径。v1.3.0 的 mediainfo 只接受项目内相对路径, 跨目录/绝对路径的
放宽已在本地 v1.4.0 开发版的 M20.1 中完成, 需要同时升级插件与配套宿主。
不要把 `console.log(mediainfo.read(...))` 打印出的 Promise
当作解析结果。

## 回报内容

记录设备型号/API、宿主与插件版本、所运行项目名、系统授权选择、PASS 或完整错误,
以及截屏尺寸/识别文本、keyCode/action、录音字节数/音轨信息。录音本身和完整截图
可以保留在本地, 验收记录只需要上述结果。

## M18.2: scheduled 执行模式 (WorkManager 定时运行)

能力目录中 `scheduled` 执行模式的插件侧 (模式与 `scheduled_runner` 启动面解析、无检查点、
无隐式重试、退出码回报) 已由 ExecutionModeLifecycleSmokeTest 覆盖; 端到端需要宿主的
WorkManager 定时运行器实际拉起 Node 项目:

1. 将 sample/nodejs/scheduled-node-task 整个目录复制到设备的 AutoJs6 工作目录, 在该目录内新建空文件 `keep-scheduled.txt`。
2. 从项目入口运行。日志应出现 `sample.scheduled-node-task.lifecycle=one_shot/script` 与 `sample.scheduled-node-task.kept=<id>`。
3. 保持宿主可运行后台任务 (不要强行停止 AutoJs6), 约 60 秒后 WorkManager 运行器会再次拉起 `main.cjs`。
   在 AutoJs6 日志中应看到 `sample.scheduled-node-task.lifecycle=scheduled/scheduled_runner`、
   `sample.scheduled-node-task.launchedByRunner=true` 与 `sample.scheduled-node-task=PASS`。
4. 回报这三行 (或实际出现的错误); 该次运行不会被隐式重试。测试后删除 `keep-scheduled.txt`。
