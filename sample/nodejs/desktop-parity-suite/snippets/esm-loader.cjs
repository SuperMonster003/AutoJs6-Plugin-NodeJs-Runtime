"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

async function run() {
  try {
    const module = await import("./esm-fixture.mjs");
    console.log("sample.desktop-parity-suite.esm-loader.label=" + module.label());
    console.log("sample.desktop-parity-suite.esm-loader.answer=" + module.answer);
  } catch (error) {
    console.log("sample.desktop-parity-suite.esm-loader.skipped=" + codeOf(error));
  }
  console.log("sample.desktop-parity-suite.esm-loader=PASS");
}

module.exports = { run };

if (require.main === module) {
  run().catch((error) => {
    console.error(error && (error.stack || error.message) || error);
    process.exitCode = 1;
  });
}
