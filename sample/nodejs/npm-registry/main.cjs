"nodejs";
const assert = require("node:assert/strict");
(async () => {
  const result = await require("package_manager").install("is-number@7.0.0");
  assert.equal(result.exitCode, 0);
  assert.equal(require("is-number")("42"), true);
  assert.equal(require("is-number")("not a number"), false);
  console.log("sample.npm-registry=PASS");
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
