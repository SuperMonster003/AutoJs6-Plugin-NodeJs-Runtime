# media-playback

这是会实际发出声音的人工测试。项目自带 tone.wav (1.5 秒 440 Hz 单声道), 通过宿主的
脚本音乐会话播放: media.play(path, { volume, looping }) 返回会话对象, 会话提供
status / pause / resume / seekTo / stop, 每次调用都返回宿主的播放状态快照。
项目声明 media 与 media.playback 两个桥能力; 宿主会显示媒体通知, 一个应用同一时刻
只有一个脚本音乐会话, 新的 play() 会替换旧会话。

脚本先以 0.4 音量播放, 暂停、回到开头、继续, 等待自然结束后 stop; 然后用
Rhino 风格别名 playMusic / isMusicPlaying / getMusicDuration / getMusicCurrentPosition /
stopMusic 再播一次并停止。PASS 要求所有状态判断为 true; 若宿主因 Android 前台服务
资格限制无法启动播放, 会以错误结束而不是通过。

The sample plays a bundled 1.5 second tone through the host media session twice
(session API, then Rhino aliases) and checks the status snapshots. The audible check is
manual; the status assertions are automated.

完整步骤见 [人工验收说明](../../../docs/nodejs/MANUAL-ACCEPTANCE.md)。
