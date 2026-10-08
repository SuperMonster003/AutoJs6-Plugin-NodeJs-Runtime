"nodejs";
const assert = require("node:assert/strict");
const addon = require("./index.cjs");
assert.equal(addon.hello(), "Hello from Node-API");
assert.equal(addon.add(20, 22), 42);
console.log("sample.native-addon.abi=" + addon.abi);
console.log("sample.native-addon=PASS");
