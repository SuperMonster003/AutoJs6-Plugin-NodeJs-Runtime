"nodejs";

(async function main() {
  const assert = require("assert/strict");
  let count = 0;

  const test = require("node:test");
  await test("addition", () => assert.equal(2 + 2, 4));
  await test("match", () => assert.match("autojs6", /^auto/));
  count = 2;
  console.log("sample.node-test-project.runner=node:test");

  console.log("sample.node-test-project.tests=" + count + "/2");
  console.log("sample.node-test-project=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
