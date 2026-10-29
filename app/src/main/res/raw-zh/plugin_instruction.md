# AutoJs6 Node.js Runtime

此插件为 AutoJs6 提供 Node.js 26.13.0 原生运行时.

在 AutoJs6 插件中心安装并启用后, 使用以下指令启动脚本:

```js
"nodejs";

console.log(process.version);
```

运行时槽位为 `node26_13`. 支持 `arm64-v8a`, `armeabi-v7a`, `x86_64` 构建, 以及 `universal` 通用包.

更多示例位于 `sample/nodejs`.

插件设置页支持 1-8 个并行执行槽 (默认 2) 与排队上限 (默认不限), 超额脚本按 FIFO 等待, 设置调整保留已接受的任务, idleExitMs 可回收空闲运行进程
