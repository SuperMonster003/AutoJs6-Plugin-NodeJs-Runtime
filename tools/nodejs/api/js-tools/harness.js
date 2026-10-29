"use strict";
// Runs the cases of cases.js against a module environment and compares the results with the values Rhino gave on a
// device (rhino-expected.json), the same way device_js_tools.py compares the device Node runs.
const path = require("node:path");
const fs = require("node:fs");
const {cases, encode, thrown} = require("./cases.js");

const expectedFile = path.join(__dirname, "rhino-expected.json");

function javaInfo(v) {
  return v && typeof v === "object" && typeof v.__autojs6JavaHandle === "string" ? {className: v.className} : null;
}

// Results of the cases whose id passes `filter`, by id; a case whose Node expression is null is skipped.
async function runCases(env, filter = () => true) {
  const names = Object.keys(env), values = names.map(name => env[name]);
  const results = {};
  for (const c of cases) {
    if (!filter(c[0])) continue;
    const source = c.length > 2 ? c[2] : c[1];
    if (source === null) {
      results[c[0]] = {skip: 1};
      continue;
    }
    try {
      let value = new Function(...names, "return (" + source + ");")(...values);
      let async = false;
      if (value && typeof value.then === "function") {
        async = true;
        value = await value;
      }
      results[c[0]] = {ok: encode(value, javaInfo)};
      if (async) results[c[0]].async = 1;
    } catch (e) {
      results[c[0]] = {th: thrown(e)};
    }
  }
  return results;
}

// Encoded values are equal; a Java list (Rhino) equals an array (Node) with equal elements.
function same(a, b) {
  if (a && typeof a === "object" && !Array.isArray(a) && "list" in a && Array.isArray(b)) a = a.list;
  if (b && typeof b === "object" && !Array.isArray(b) && "list" in b && Array.isArray(a)) b = b.list;
  if (Array.isArray(a) || Array.isArray(b)) {
    return Array.isArray(a) && Array.isArray(b) && a.length === b.length && a.every((x, i) => same(x, b[i]));
  }
  if (a && b && typeof a === "object" && typeof b === "object") {
    const ka = Object.keys(a).sort(), kb = Object.keys(b).sort();
    return ka.length === kb.length && ka.every((k, i) => k === kb[i] && same(a[k], b[k]));
  }
  return a === b;
}

// A Java exception Rhino reports as "JavaException <Simple name>" matches a Node error of that name.
function errorName(name) {
  return name.startsWith("JavaException ") ? name.slice("JavaException ".length) : name;
}

// "pass", "skip" or the reason of a failure.
function compare(expected, actual) {
  if (actual.skip) return "skip";
  if (expected.th) {
    if (!actual.th) return "expected " + expected.th.name + ", got " + JSON.stringify(actual.ok);
    return errorName(expected.th.name) === errorName(actual.th.name) ? "pass" : "expected " + expected.th.name + ", got " + actual.th.name + ": " + actual.th.message;
  }
  if (actual.th) return "threw " + actual.th.name + ": " + actual.th.message;
  return same(expected.ok, actual.ok) ? "pass" : "expected " + JSON.stringify(expected.ok) + ", got " + JSON.stringify(actual.ok);
}

function compareAll(results, expected = JSON.parse(fs.readFileSync(expectedFile, "utf8")).cases) {
  const report = {pass: 0, skip: 0, fail: 0, failures: {}};
  for (const [id, actual] of Object.entries(results)) {
    const verdict = expected[id] ? compare(expected[id], actual) : "no expected value";
    if (verdict === "pass" || verdict === "skip") report[verdict] += 1;
    else {
      report.fail += 1;
      report.failures[id] = verdict;
    }
  }
  return report;
}

module.exports = {runCases, compare, compareAll, same, expectedFile};
