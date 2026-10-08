const { parentPort } = require("node:worker_threads");
const bridge = require("autojs6:bridge");

bridge.callAutoJs("device", "info", []).then(
  info => parentPort.postMessage({ model: info.build.model, sdkInt: info.build.sdkInt }),
  error => { setImmediate(() => { throw error; }); }
);
