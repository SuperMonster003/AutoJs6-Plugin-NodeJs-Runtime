"nodejs";

const path = require("node:path");
const { pathToFileURL } = require("node:url");

(async function main() {
  const featureName = "formatter";
  const feature = await import("./features/" + featureName + ".mjs");
  console.log("sample.packaged-dynamic-import.result=" + feature.format("dynamic"));
  // Absolute paths and file: URLs resolve like Node within Android file access.
  const byFileUrl = await import(pathToFileURL(path.join(__dirname, "features", featureName + ".mjs")).href);
  console.log("sample.packaged-dynamic-import.fileUrl=" + (byFileUrl === feature ? "same-module" : "different-module"));
  console.log("sample.packaged-dynamic-import=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
