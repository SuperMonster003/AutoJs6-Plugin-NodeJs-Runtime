"use strict";
// M2.4 npm ecosystem smoke: one minimal assertion per package. Each line
// prints npm.<package>=ok only when the package's core behavior works.

function check(label, action) {
  try {
    const value = action();
    if (value !== true) {
      throw new Error("assertion returned " + value);
    }
    console.log("npm." + label + "=ok");
  } catch (error) {
    console.error("npm." + label + "=fail " + (error && error.message));
    process.exitCode = 1;
  }
}

check("lodash", () => {
  const _ = require("lodash");
  return _.chunk([1, 2, 3, 4], 2).length === 2 &&
      _.get({ a: { b: [0, { c: 7 }] } }, "a.b[1].c") === 7 &&
      _.debounce(() => 0, 10) !== undefined;
});

check("dayjs", () => {
  const dayjs = require("dayjs");
  const day = dayjs("2026-08-18T12:34:56.000Z");
  return day.isValid() && day.add(1, "day").format("YYYY-MM-DD").length === 10;
});

check("ms", () => {
  const ms = require("ms");
  return ms("2h") === 7200000 && ms(60000) === "1m";
});

check("semver", () => {
  const semver = require("semver");
  return semver.gt("2.0.0", "1.9.9") &&
      semver.satisfies("24.5.0", ">=24 <25") &&
      semver.parse(process.version.slice(1)) !== null;
});

check("uuid", () => {
  const { v4, validate } = require("uuid");
  const id = v4();
  return validate(id) && id.length === 36;
});

check("debug", () => {
  const debug = require("debug");
  const log = debug("m2:test");
  log("invisible unless DEBUG set");
  debug.enable("m2:*");
  return debug.enabled("m2:test") === true;
});

check("mime", () => {
  const mime = require("mime");
  return mime.getType("json") === "application/json" &&
      mime.getExtension("text/html") === "html";
});

check("qs", () => {
  const qs = require("qs");
  const parsed = qs.parse("a[b]=1&list[0]=x&list[1]=y");
  return parsed.a.b === "1" && parsed.list.length === 2 &&
      qs.stringify({ q: "node js" }) === "q=node%20js";
});

check("js-yaml", () => {
  const yaml = require("js-yaml");
  const doc = yaml.load("name: autojs6\nitems:\n  - 1\n  - 2\n");
  return doc.name === "autojs6" && doc.items[1] === 2 &&
      yaml.dump({ ok: true }).includes("ok: true");
});

check("ajv", () => {
  const Ajv = require("ajv");
  const ajv = new Ajv();
  const validateFn = ajv.compile({
    type: "object",
    properties: { count: { type: "integer", minimum: 1 } },
    required: ["count"],
  });
  return validateFn({ count: 3 }) === true && validateFn({ count: 0 }) === false;
});

console.log("npm.suite=done");
