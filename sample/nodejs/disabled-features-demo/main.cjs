"nodejs";

function available(label, action) {
  try {
    action();
    console.log("sample.enabled." + label + "=AVAILABLE");
  } catch (error) {
    console.log("sample.enabled." + label + "=" + (error && (error.autojs6Code || error.code || error.name)));
    process.exitCode = 1;
  }
}

available("child_process", () => require("child_process"));
available("worker_threads", () => require("worker_threads"));
available("http", () => require("http"));
available("native_loader", () => { if (typeof process.dlopen !== "function") throw new Error("dlopen missing"); });
available("process_binding", () => process.binding("fs"));
available("process_cwd", () => process.cwd());

console.log("sample.disabled-features-demo=PASS");
