"nodejs";

(async function main() {
  const recorder = require("recorder");
  const mediainfo = require("mediainfo");
  let started = false;
  try {
    const status = await recorder.getStatus();
    if (!status.microphonePermissionGranted) {
      throw new Error("Grant AutoJs6 microphone permission in Android settings, then run this project again.");
    }
    await mediainfo.capabilities();
    const path = "recording-" + Date.now() + ".m4a";
    console.log("Recording microphone audio for 3 seconds. Speak a short sentence now.");
    const active = await recorder.start({ path, maxDurationMs: 3000 });
    started = true;
    console.log("recording.path=" + active.path);
    await new Promise(resolve => setTimeout(resolve, 3600));
    const result = await recorder.stop();
    started = false;
    if (!result || result.byteCount <= 0) throw new Error("Recording produced no audio bytes.");
    const info = await mediainfo.read(result.path, { includeInform: false });
    if (!info.sections || !info.sections.audio || !info.sections.audio.length) {
      throw new Error("MediaInfo did not find an audio track.");
    }
    console.log("recording.result=" + JSON.stringify(result));
    console.log("recording.audio=" + JSON.stringify(info.sections.audio));
    console.log("sample.audio-recording=PASS");
  } finally {
    if (started) await recorder.stop();
  }
})().catch(error => {
  console.error(error && error.stack || error);
  process.exitCode = 1;
});
