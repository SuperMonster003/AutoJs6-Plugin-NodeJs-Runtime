"nodejs";

const Module = require("module");
const profile = require("autojs6:profile");

// Since M23.2 `module` is Node's own builtin, so enableCompileCache() is exposed. Project
// modules are still compiled by the runtime's workspace loader until M23.3 hands them to
// Node's CommonJS loader, which is when the on-disk cache starts to apply to them (M23.8).
const onDisk = typeof Module.enableCompileCache === "function" ? "exposed" : "not_applicable_custom_loader";
console.log("sample.compile-cache.on-disk=" + onDisk);
console.log("sample.compile-cache.runtime-loader=" + (profile.compileCache && profile.compileCache.customLoader === true));

const first = require("./lib/counter.cjs");
const second = require("./lib/counter.cjs");

console.log("sample.compile-cache.same-module=" + (first === second));
console.log("sample.compile-cache=PASS");
