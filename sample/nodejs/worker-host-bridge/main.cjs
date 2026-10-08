"nodejs";

const { Worker } = require("node:worker_threads");
const path = require("node:path");

(async () => {
  const worker = new Worker(path.join(__dirname, "worker.cjs"));
  try {
    const info = await new Promise((resolve, reject) => {
      worker.once("message", resolve);
      worker.once("error", reject);
      worker.once("exit", code => {
        if (code !== 0) reject(new Error("Worker exited with code " + code));
      });
    });
    if (typeof info.sdkInt !== "number" || typeof info.model !== "string") {
      throw new Error("The host returned an unexpected device info result");
    }
    console.log("sample.worker-host-bridge.device=" + info.model + "/API " + info.sdkInt);
    console.log("sample.worker-host-bridge=PASS");
  } finally {
    await worker.terminate();
  }
})().catch(error => {
  console.error(error.stack || error);
  process.exitCode = 1;
});
