"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  const policy = require("autojs6:lifecycle").policy;
  console.log("sample.scheduled-node-task.lifecycle=" + policy.executionMode + "/" + policy.launchSurface);
  const work = require("work_manager");
  console.log("sample.scheduled-node-task.provider=work_manager");

  if (policy.launchSurface === "scheduled_runner") {
    // Launched by the host WorkManager runner: report the scheduled run and finish without rescheduling.
    console.log("sample.scheduled-node-task.launchedByRunner=true");
    console.log("sample.scheduled-node-task=PASS");
    return;
  }

  // Create keep-scheduled.txt next to main.cjs to keep the task so the runner launches it about 60 s later.
  const fs = require("node:fs");
  const path = require("node:path");
  const keep = fs.existsSync(path.join(__dirname, "keep-scheduled.txt"));
  try {
    const id = await work.scheduleOnce({
      scriptPath: "main.cjs",
      delayMs: 60000,
      taskTimeoutMs: 5000,
      timeoutMs: 1000
    });
    if (keep) {
      console.log("sample.scheduled-node-task.kept=" + id);
    } else {
      await work.cancel(id, { timeoutMs: 1000 });
      console.log("sample.scheduled-node-task.cancelled=true");
    }
  } catch (error) {
    console.log("sample.scheduled-node-task.unavailable=" + codeOf(error));
  }

  console.log("sample.scheduled-node-task=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
