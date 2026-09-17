"nodejs";

const media = require("media");
const compat = require("autojs6:compat");

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function waitForEnd(session, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const status = await session.status();
    if (!status.active || status.ended) return status;
    if (Date.now() > deadline) {
      throw new Error("Playback did not end within " + timeoutMs + " ms: " + JSON.stringify(status));
    }
    await sleep(200);
  }
}

(async function main() {
  console.log("Playing tone.wav (1.5 s, 440 Hz) through the host media session at volume 0.4.");
  const session = await media.play("tone.wav", { volume: 0.4 });
  const started = await session.status();
  console.log("playback.session=" + [typeof session.id, started.active, started.durationMs > 0].join("|"));
  await sleep(300);
  const paused = await session.pause();
  console.log("playback.paused=" + (paused.active && !paused.playing));
  const sought = await session.seekTo(0);
  const resumed = await session.resume();
  console.log("playback.resumed=" + [sought.positionMs <= 200, resumed.playing].join("|"));
  const ended = await waitForEnd(session, 10000);
  console.log("playback.ended=" + (ended.ended || !ended.active));
  const stopped = await session.stop();
  console.log("playback.stopped=" + (stopped.active === false));
  const afterStop = await media.getPlaybackStatus({ session });
  console.log("playback.stale=" + (afterStop.active === false));

  await compat.media.playMusic("tone.wav", 0.2);
  const playing = await compat.media.isMusicPlaying();
  const duration = await compat.media.getMusicDuration();
  const position = await compat.media.getMusicCurrentPosition();
  await compat.media.stopMusic();
  console.log("compat.music=" + [playing, duration > 0, position >= 0].join("|"));
  console.log("sample.media-playback=PASS");
})().catch(error => {
  console.error(error && error.stack || error);
  process.exitCode = 1;
});
