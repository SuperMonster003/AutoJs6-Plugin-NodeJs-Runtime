"nodejs";

const fs = require("fs");
const path = require("path");

// Runs every catalogued snippet in order; each snippet prints its own result
// lines, including an explicit skip code when a provider or permission is missing.
(async function main() {
  const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, "examples.json"), "utf8"));
  console.log("sample.pro-parity-suite.catalog=" + manifest.examples.length);
  for (const entry of manifest.examples) {
    await require("./" + entry.file).run();
  }
  console.log("sample.pro-parity-suite=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
