"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

async function run() {
  try {
    const math = require("../vendor/pure-math");
    console.log("sample.desktop-parity-suite.npm-dependency.sum=" + math.sum([8, 13, 21]));
  } catch (error) {
    console.log("sample.desktop-parity-suite.npm-dependency.skipped=" + codeOf(error));
  }
  console.log("sample.desktop-parity-suite.npm-dependency=PASS");
}

module.exports = { run };

if (require.main === module) {
  run().catch((error) => {
    console.error(error && (error.stack || error.message) || error);
    process.exitCode = 1;
  });
}
