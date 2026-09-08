"nodejs";
const { Worker } = require("node:worker_threads");
const path = require("node:path");
const worker = new Worker(path.join(__dirname, "worker.cjs"));
worker.once("message", result => {
  if (result !== 42) throw new Error("Unexpected WASM result: " + result);
  console.log("sample.wasm-worker.add=" + result);
  console.log("sample.wasm-worker=PASS");
});
worker.on("error", error => { console.error(error.stack || error); process.exitCode = 1; });
