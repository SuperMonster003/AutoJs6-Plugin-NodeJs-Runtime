"use strict";
const { parentPort, workerData } = require("node:worker_threads");
const cpu = require("./cpu.cjs");
cpu(50000);
parentPort.once("message", () => {
  parentPort.postMessage({ sum: cpu(workerData.limit) });
  parentPort.close();
});
parentPort.postMessage({ ready: true });
