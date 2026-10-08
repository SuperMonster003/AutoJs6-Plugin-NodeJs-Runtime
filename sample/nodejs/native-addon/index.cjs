const path = require("node:path");
const abi = { arm64: "arm64-v8a", arm: "armeabi-v7a", x64: "x86_64" }[process.arch];
if (!abi) throw new Error("No sample addon is bundled for " + process.arch);
module.exports = require(path.join(__dirname, "prebuilds", abi, "hello.node"));
