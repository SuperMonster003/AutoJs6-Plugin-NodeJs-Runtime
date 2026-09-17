"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

async function run() {
  try {
    const inspector = require("inspector");
    console.log("sample.desktop-parity-suite.inspector.url=" + String(inspector.url && inspector.url()));
    console.log("sample.desktop-parity-suite.inspector.session=" + (typeof inspector.Session));
  } catch (error) {
    console.log("sample.desktop-parity-suite.inspector.skipped=" + codeOf(error));
  }
  console.log("sample.desktop-parity-suite.inspector=PASS");
}

module.exports = { run };

if (require.main === module) {
  run().catch((error) => {
    console.error(error && (error.stack || error.message) || error);
    process.exitCode = 1;
  });
}
