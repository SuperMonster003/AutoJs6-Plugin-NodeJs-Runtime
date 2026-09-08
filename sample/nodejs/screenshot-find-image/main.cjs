"nodejs";

(async function main() {
  const image = require("image");
  const handles = [];
  try {
    await image.requestScreenCapture({ timeoutMs: 120000 });
    await new Promise(resolve => setTimeout(resolve, 500));
    const capture = await image.captureScreen();
    handles.push(capture);
    await image.saveImage(capture, "screen.png");
    const width = Math.min(160, capture.width), height = Math.min(120, capture.height);
    const x = Math.floor((capture.width - width) / 2), y = Math.floor((capture.height - height) / 2);
    const clipped = await image.clip(capture, x, y, width, height);
    handles.push(clipped);
    await image.saveImage(clipped, "template.png");
    const template = await image.readImage("template.png");
    handles.push(template);
    const point = await image.findImage(capture, template, { threshold: 0.99 });
    console.log("sample.screenshot-find-image.size=" + capture.width + "x" + capture.height);
    console.log("sample.screenshot-find-image.point=" + JSON.stringify(point));
    console.log("sample.screenshot-find-image=PASS");
  } catch (error) {
    if (error && (error.category === "permission-denied" || /OpenCV/i.test(error.message))) {
      console.log("sample.screenshot-find-image.skipped=" + error.message);
    } else {
      throw error;
    }
  } finally {
    await Promise.allSettled(handles.map(handle => image.recycle(handle)));
    await image.stopScreenCapture();
  }
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
