"use strict";
// M20.2: bare package specifiers resolve inside worker_threads through the workspace
// node_modules (main, exports require conditions, nested type routing package.json).
const { parentPort } = require("node:worker_threads");
const passed = [];
function check(label, action) {
  try {
    if (action() === true) passed.push(label);
    else passed.push(label + ":assertion");
  } catch (error) {
    passed.push(label + ":" + (error && (error.code || error.message)));
  }
}
check("lodash", () => require("lodash").chunk([1, 2, 3, 4], 2).length === 2);
check("dayjs", () => require("dayjs")("2026-08-18T12:34:56.000Z").isValid());
check("semver", () => require("semver").gt("2.0.0", "1.9.9"));
check("zod", () => require("zod").z.object({ n: require("zod").z.number() }).safeParse({ n: 1 }).success === true);
check("date-fns", () => {
  const { addDays, formatISO } = require("date-fns");
  return formatISO(addDays(new Date("2026-08-18T00:00:00.000Z"), 1), { representation: "date" }).length === 10;
});
check("resolve", () => require.resolve("zod").endsWith("/node_modules/zod/index.cjs"));
check("missing", () => {
  try { require("no-such-corpus-package"); return false; } catch (error) { return error.code === "MODULE_NOT_FOUND"; }
});
parentPort.postMessage(passed.join(","));
