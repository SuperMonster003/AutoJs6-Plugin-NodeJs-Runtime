"nodejs";

(async function main() {
  const events = require("autojs6:events");
  const nativeEvents = require("node:events");
  console.log("native EventEmitter=" + typeof nativeEvents);
  console.log("Android observers=" + [typeof events.observeNotification, typeof events.observeToast, typeof events.observeKey].join(","));
  let timer;
  let finish;
  const received = new Promise(resolve => { finish = resolve; });
  events.on("key", event => {
    console.log("Android key=" + JSON.stringify(event));
    if (event.keyCode === 24 && event.action === "down") finish(event);
  });
  events.on("notification", event => console.log("Android notification=" + JSON.stringify(event)));
  events.on("toast", event => console.log("Android toast=" + JSON.stringify(event)));
  events.on("error", error => console.error(error.message));
  try {
    await events.observeKey();
    // These sources require separate Android access. Their failure does not hide the key test.
    for (const [name, observe] of [["notification", () => events.observeNotification()], ["toast", () => events.observeToast()]]) {
      try { await observe(); }
      catch (error) { console.log(name + " observer unavailable: " + error.message); }
    }
    console.log("READY: press the physical VOLUME_UP key within 45 seconds.");
    timer = setTimeout(() => finish(null), 45000);
    if (!await received) throw new Error("No physical VOLUME_UP event arrived. Check AutoJs6 accessibility access and rerun.");
    console.log("sample.host-events=PASS");
  } finally {
    clearTimeout(timer);
    await events.close();
  }
})().catch(error => {
  console.error(error && error.stack || error);
  process.exitCode = 1;
});
