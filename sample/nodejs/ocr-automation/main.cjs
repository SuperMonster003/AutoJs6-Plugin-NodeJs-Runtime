"nodejs";

(async function main() {
  const image = require("image");
  const ocr = require("ocr");
  let capture;
  try {
    await image.requestScreenCapture({ timeoutMs: 120000 });
    await new Promise(resolve => setTimeout(resolve, 500));
    capture = await image.captureScreen();
    const text = await ocr.recognizeText(capture, { timeoutMs: 30000 });
    console.log("sample.ocr-automation.text=" + text.slice(0, 160));
    console.log("sample.ocr-automation=PASS");
  } catch (error) {
    if (error && ["permission-denied", "unavailable"].includes(error.category)) {
      console.log("sample.ocr-automation.skipped=" + error.message);
    } else {
      throw error;
    }
  } finally {
    if (capture) await image.recycle(capture);
    await image.stopScreenCapture();
  }
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
