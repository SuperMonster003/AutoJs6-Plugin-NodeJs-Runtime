"nodejs";

(async function main() {
  const overlay = require("ui.overlay");
  let window = null;
  try {
    if (!(await overlay.hasPermission()).granted) {
      console.log("sample.pro-parity-suite.overlay-floaty.skipped=Allow AutoJs6 to display over other apps in Android settings, then run this example again.");
      return;
    }
    window = await overlay.show({
      content: { type: "Column", padding: 12, children: [
        { type: "Text", id: "status", text: "Drag this window or tap its button.", textSize: 16 },
        { type: "Button", id: "button", text: "Hello" }
      ] },
      window: { width: 240, x: 24, y: 96, draggable: true, alpha: 1 },
      disclosure: { title: "Node overlay example", text: "A movable example window, closed after three seconds." }
    });
    for (const type of ["click", "move", "update"]) window.on(type, event => {
      console.log("sample.pro-parity-suite.overlay-floaty.event=" + event.type);
    });
    await window.update({ id: "status", text: "Overlay updated. Closing soon...", textColor: "#1565C0", textSize: 18 });
    await window.update({ window: { alpha: 0.9 } });
    await new Promise(resolve => setTimeout(resolve, 3000));
    await window.close();
    if (!window.closed) throw new Error("The overlay did not close.");
    console.log("sample.pro-parity-suite.overlay-floaty=PASS");
  } catch (error) {
    if (error && ["permission-denied", "unavailable"].includes(error.category)) {
      console.log("sample.pro-parity-suite.overlay-floaty.skipped=" + error.message);
    } else {
      throw error;
    }
  } finally {
    if (window && !window.closed) await window.close();
  }
})().catch(error => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
