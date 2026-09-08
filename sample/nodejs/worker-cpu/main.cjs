"nodejs";
const { Worker } = require("node:worker_threads");
const path = require("node:path");
const cpu = require("./cpu.cjs");
const now = () => Number(process.hrtime.bigint()) / 1e6;
(async function main() {
  const count = 4, limit = 1500000;
  cpu(50000);
  const serialStart = now();
  const sums = Array.from({ length: count }, () => cpu(limit));
  const serialMs = now() - serialStart;
  const workers = [], startupStart = now();
  try {
    await Promise.all(Array.from({ length: count }, () => new Promise((resolve, reject) => {
      const worker = new Worker(path.join(__dirname, "worker.cjs"), { workerData: { limit } });
      workers.push(worker);
      worker.once("message", message => message.ready ? resolve() : reject(new Error("Worker did not become ready")));
      worker.on("error", reject);
    })));
    const startupMs = now() - startupStart;
    const parallelStart = now();
    const results = await Promise.all(workers.map(worker => new Promise((resolve, reject) => {
      worker.once("message", resolve);
      worker.once("error", reject);
      worker.once("exit", code => { if (code !== 0) reject(new Error("Worker exited " + code)); });
      worker.postMessage("run");
    })));
    const parallelMs = now() - parallelStart;
    results.forEach((value, index) => { if (value.sum !== sums[index]) throw new Error("Parallel result mismatch"); });
    console.log("sample.worker-cpu.workers=" + count);
    console.log("sample.worker-cpu.serialMs=" + serialMs.toFixed(3));
    console.log("sample.worker-cpu.parallelMs=" + parallelMs.toFixed(3));
    console.log("sample.worker-cpu.startupMs=" + startupMs.toFixed(3));
    console.log("sample.worker-cpu.speedup=" + (serialMs / parallelMs).toFixed(3));
    console.log("sample.worker-cpu.speedupWithStartup=" + (serialMs / (parallelMs + startupMs)).toFixed(3));
    console.log("sample.worker-cpu=PASS");
  } finally { await Promise.all(workers.map(worker => worker.terminate())); }
})().catch(error => { console.error(error.stack || error); process.exitCode = 1; });
