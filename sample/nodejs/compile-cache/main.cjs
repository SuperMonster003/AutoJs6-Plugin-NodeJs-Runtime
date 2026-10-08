"nodejs";

const Module = require("module");
const profile = require("autojs6:profile");

// The runtime enables Node's portable module cache before project modules are loaded.
// NODE_DISABLE_COMPILE_CACHE=1 opts out; module.enableCompileCache remains Node's API.
const onDisk = Module.getCompileCacheDir() ? "enabled" : "disabled";
console.log("sample.compile-cache.on-disk=" + onDisk);
console.log("sample.compile-cache.runtime-loader=" + (profile.compileCache && profile.compileCache.customLoader === true));

const first = require("./lib/counter.cjs");
const second = require("./lib/counter.cjs");

console.log("sample.compile-cache.same-module=" + (first === second));
console.log("sample.compile-cache=PASS");
