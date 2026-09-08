"nodejs";

(async function main() {
  const recorder = require("recorder");
  let recording = false;
  try {
    const status = await recorder.getStatus();
    if (!status.microphonePermissionGranted) {
      console.log("sample.pro-parity-suite.media-recorder.skipped=Grant AutoJs6 microphone permission in Android settings, then run this 3-second recording example again.");
      return;
    }
    const media = require("media");
    const mediainfo = require("mediainfo");
    await mediainfo.capabilities();
    const stream = await media.getAudioStreamInfo("music");
    console.log("sample.pro-parity-suite.media-recorder.audioStream=" + stream.stream);
    await recorder.start({ path: "record.m4a", maxDurationMs: 3000 });
    recording = true;
    await new Promise(resolve => setTimeout(resolve, 3600));
    const result = await recorder.stop();
    recording = false;
    if (!result || result.byteCount <= 0) throw new Error("The recording contains no audio data.");
    const snapshot = await mediainfo.read(result.path, { includeInform: false });
    if (!snapshot.sections.audio.length) throw new Error("MediaInfo found no audio track.");
    console.log("sample.pro-parity-suite.media-recorder.bytes=" + result.byteCount);
    console.log("sample.pro-parity-suite.media-recorder=PASS");
  } catch (error) {
    if (error && ["permission-denied", "unavailable"].includes(error.category)) {
      console.log("sample.pro-parity-suite.media-recorder.skipped=" + error.message);
    } else {
      throw error;
    }
  } finally {
    if (recording) await recorder.stop();
  }
})().catch(error => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
