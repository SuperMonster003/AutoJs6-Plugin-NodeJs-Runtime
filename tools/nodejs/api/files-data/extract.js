"use strict";
// The source of one module function of the shared JS tools core (node_bridge_sources.cpp), for the unit tests.
const fs = require("node:fs");
const path = require("node:path");
const source = fs.readFileSync(path.join(__dirname, "../../../../app/src/main/cpp/node_bridge_sources.cpp"), "utf8");
module.exports = name => {
  const start = source.indexOf(`    function __autojs6_js_tools_${name}(host) {`);
  const end = source.indexOf("\n    }\n", start);
  if (start < 0 || end < 0) throw new Error("missing core function " + name);
  return source.slice(start, end + 7);
};
