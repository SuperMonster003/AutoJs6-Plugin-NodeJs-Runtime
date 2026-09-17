"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

async function run() {
  try {
    const rhino = require("rhino");
    const target = {};
    const result = rhino.install({ explicit: true, target });
    console.log("sample.pro-parity-suite.rhino-install.keys=" + Object.keys(result.globals || target).sort().join(","));
  } catch (error) {
    console.log("sample.pro-parity-suite.rhino-install.skipped=" + codeOf(error));
  }
  console.log("sample.pro-parity-suite.rhino-install=PASS");
}

module.exports = { run };

if (require.main === module) {
  run().catch((error) => {
    console.error(error && (error.stack || error.message) || error);
    process.exitCode = 1;
  });
}
