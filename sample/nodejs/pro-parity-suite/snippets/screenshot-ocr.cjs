"nodejs";

(async function main() {
  let capture = null;
  try {
    const image = require("image");
    const ocr = require("ocr");
    await image.requestScreenCapture({ timeoutMs: 120000 });
    await new Promise(resolve => setTimeout(resolve, 500));
    capture = await image.captureScreen();
    const text = await ocr.recognizeText(capture, { timeoutMs: 30000 });
    console.log("sample.pro-parity-suite.screenshot-ocr.text=" + String(text).slice(0, 32));
    console.log("sample.pro-parity-suite.screenshot-ocr=PASS");
  } catch (error) {
    if (error && ["permission-denied", "unavailable"].includes(error.category)) {
      console.log("sample.pro-parity-suite.screenshot-ocr.skipped=" + error.message);
    } else {
      throw error;
    }
  } finally {
    if (capture) {
      try {
        await require("image").recycle(capture, { timeoutMs: 1000 });
      } catch (_) {
        // Ignore cleanup failure in example skip paths.
      }
    }
    await require("image").stopScreenCapture();
  }
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
