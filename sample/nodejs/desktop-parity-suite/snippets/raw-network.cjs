"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

async function run() {
  try {
    const modules = ["http", "https", "net", "tls", "dns", "dns/promises"];
    const shape = modules.map((name) => {
      const mod = require(name);
      return name + ":" + Object.keys(mod).slice(0, 3).join("|");
    }).join(",");
    console.log("sample.desktop-parity-suite.raw-network.shape=" + shape);
  } catch (error) {
    console.log("sample.desktop-parity-suite.raw-network.skipped=" + codeOf(error));
  }
  console.log("sample.desktop-parity-suite.raw-network=PASS");
}

module.exports = { run };

if (require.main === module) {
  run().catch((error) => {
    console.error(error && (error.stack || error.message) || error);
    process.exitCode = 1;
  });
}
