"use strict";

// Manual regression for the actual embedded wrappers. Android provider behavior
// is verified separately through the host and device integration tests.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "../../..");
const source = fs.readFileSync(path.join(root, "app/src/main/cpp/node_bridge_sources.cpp"), "utf8");
const calls = [];
let nextResult;
let nextError;

function segment(start, end) {
  const first = source.indexOf(start);
  const last = source.indexOf(end, first);
  assert.ok(first >= 0 && last > first, `missing embedded section: ${start}`);
  return source.slice(first, last);
}

const factory = new Function("__autojs6_call_autojs", "__autojs6_invalid_arg_type", "process", `
  let __autojs6_limited_pinyin_cache, __autojs6_limited_pinyin4j_cache;
  let __autojs6_limited_screen_metrics_cache, __autojs6_limited_console_cache;
  const __autojs6_has_own = (object, name) => Object.prototype.hasOwnProperty.call(object, name);
  ${segment("  const __autojs6_pinyin_styles =", "  // Shared JS tools core.")}
  ${segment("  function __autojs6_limited_console()", "  let __autojs6_main_module_ref =")}
  return {
    pinyin: __autojs6_limited_pinyin(), pinyin4j: __autojs6_limited_pinyin4j(),
    screen: __autojs6_limited_screen_metrics(), console: __autojs6_limited_console()
  };
`);

const api = factory((module, method, args, options) => {
  calls.push({ module, method, args: JSON.parse(JSON.stringify(args)), options });
  return nextError ? Promise.reject(nextError) : Promise.resolve(nextResult);
}, message => new TypeError(message), { stdout: { write() {} }, stderr: { write() {} } });

async function call(module, method, args, result, invoke) {
  nextResult = result;
  const pending = invoke();
  assert.ok(pending instanceof Promise, `${module}.${method} must be awaitable`);
  const value = await pending;
  assert.deepEqual(calls.at(-1), {
    module, method, args,
    options: { permissions: [module === "screen_metrics" ? "device" : module] }
  });
  return value;
}

async function main() {
  const text = "\u5317\u4eac\u4e0a\u6d77";
  const options = { segment: true, heteronym: true, customDictionary: { "\u4eac": [["jing1"]] } };
  const rows = await call("pinyin", "convert", [text, options], [["bei"], ["jing"]], () => api.pinyin(text, options));
  assert.deepEqual(rows, [["bei"], ["jing"]]);
  assert.equal(Object.keys(rows).includes("compact"), false);
  assert.deepEqual(await call("pinyin", "compact", [[["bei"], ["jing"]]], [["bei", "jing"]], () => rows.compact()), [["bei", "jing"]]);
  assert.equal(await call("pinyin", "simple", [text, true, true], "bei3jing1shang4hai3", () => api.pinyin.simple(text, true, true)), "bei3jing1shang4hai3");
  await call("pinyin", "compare", ["a", "b"], -1, () => api.pinyin.compare("a", "b"));
  await call("pinyin", "fromCodePoint", [0x5317], "bei", () => api.pinyin.fromCodePoint(0x5317));
  await call("pinyin", "fromPhrase", [text], [["bei"]], () => api.pinyin.fromPhrase(text));
  await call("pinyin4j", "of", [text, " "], "bei jing shang hai", () => api.pinyin4j(text, " "));
  await call("pinyin4j", "as", ["bei3"], "bei", () => api.pinyin4j.as("bei3"));
  await call("pinyin4j", "readings", [text, { romanization: "hanyu" }], [["bei3"]], () => api.pinyin4j.readings(text, { romanization: "hanyu" }));
  const unavailable = Object.assign(new Error("Pinyin provider is unavailable"), { code: "ERR_AUTOJS6_BRIDGE_PROVIDER_UNAVAILABLE" });
  nextError = unavailable;
  await assert.rejects(api.pinyin.simple(text), error => error === unavailable);
  nextError = null;

  const aliases = { showConsole: "show", openConsole: "show", launchConsole: "launch", clearConsole: "clear" };
  const controls = ["show", "hide", "reset", "expand", "collapse", "launch", "clear", "setSize", "setPosition", "setTitle",
    "setTitleTextSize", "setTitleTextColor", "setTitleBackgroundColor", "setTitleBackgroundTint", "setTitleBackgroundAlpha", "setTitleIconsTint",
    "setContentTextSize", "setContentTextColor", "setContentTextColors", "setContentBackgroundColor", "setContentBackgroundTint", "setContentBackgroundAlpha",
    "setTextSize", "setTextColor", "setBackgroundColor", "setBackgroundTint", "setBackgroundAlpha", "setExitOnClose", "setTouchable",
    "setGlobalLogConfig", "resetGlobalLogConfig", ...Object.keys(aliases)];
  for (const name of controls) {
    assert.equal(await call("console", aliases[name] || name, ["test", 2], null, () => api.console[name]("test", 2)), undefined);
  }
  const denied = Object.assign(new Error("Overlay permission required"), { code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED" });
  nextError = denied;
  await assert.rejects(api.console.show(), error => error === denied);
  nextError = null;
  const beforeInvalidColor = calls.length;
  await assert.rejects(api.console.setTitleBackgroundTint(NaN), /finite/);
  assert.equal(calls.length, beforeInvalidColor, "NaN must not become a tint-clearing null");
  for (const name of ["cX", "cY", "cXy", "cYx"]) {
    assert.equal(await call("screen_metrics", name, [0.5, "16:9", true], 640, () => api.screen[name](0.5, "16:9", true)), 640);
    const before = calls.length;
    await assert.rejects(api.screen[name](NaN), /finite/);
    await assert.rejects(api.screen[name](10, Infinity), /finite/);
    await assert.rejects(api.screen[name](undefined), /finite/);
    assert.equal(calls.length, before, "invalid numbers must not be serialized to null");
  }
  assert.equal(await call("screen_metrics", "cX", [], 1096, () => api.screen.cX()), 1096);
  assert.equal(await call("screen_metrics", "cY", [], 2560, () => api.screen.cY()), 2560);
  const info = { width: 1096, height: 2560, rotation: 0, baseX: 720, baseY: 1280 };
  assert.deepEqual(await call("screen_metrics", "getInfo", [], info, () => api.screen.getInfo()), info);
  for (const name of ["getScaleBases", "getScaleBaseX", "getScaleBaseY"]) {
    await call("screen_metrics", name, [], 720, () => api.screen[name]());
  }
  for (const name of ["setScaleBases", "setScaleBaseX", "setScaleBaseY"]) {
    assert.equal(await call("screen_metrics", name, [720, 1280], null, () => api.screen[name](720, 1280)), undefined);
  }

  const catalog = JSON.parse(fs.readFileSync(path.join(root, "app/src/main/assets/nodejs/node-capability-catalog.json"), "utf8"));
  const serialized = JSON.stringify(catalog);
  for (const name of controls) {
    assert.ok(serialized.includes(`"module":"console","method":"${aliases[name] || name}"`), `catalog missing console.${name}`);
  }
  for (const name of Object.keys(api.screen)) {
    assert.ok(serialized.includes(`"module":"screen_metrics","method":"${name}"`), `catalog missing screen_metrics.${name}`);
  }
  const index = fs.readFileSync(path.join(root, "docs/nodejs/types/autojs6-node/index.d.ts"), "utf8");
  for (const name of ["pinyin", "pinyin4j", "console", "screen_metrics", "accessibility", "input_observer"]) {
    assert.ok(index.includes(`./${name}.d.ts`), `type entry does not reference ${name}`);
  }
  assert.equal(source.includes("consoleNoopReturnSelf"), false);
  assert.equal(source.includes("__autojs6_pinyin_dict"), false);
  console.log(`host-api-coverage: PASS (${calls.length} real-wrapper dispatch checks, rejection and declaration entry checks)`);
}

main().catch(error => { console.error(error); process.exitCode = 1; });
