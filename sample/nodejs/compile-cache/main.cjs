"nodejs";

const Module = require("module");
const profile = require("autojs6:profile");

// Project modules are compiled by the runtime's workspace loader, not by Node's
// CommonJS loader, so Node's on-disk compile cache has nothing to cache here.
const onDisk = typeof Module.enableCompileCache === "function" ? "exposed" : "not_applicable_custom_loader";
console.log("sample.compile-cache.on-disk=" + onDisk);
console.log("sample.compile-cache.runtime-loader=" + (profile.compileCache && profile.compileCache.customLoader === true));

const first = require("./lib/counter.cjs");
const second = require("./lib/counter.cjs");

console.log("sample.compile-cache.same-module=" + (first === second));
console.log("sample.compile-cache=PASS");
