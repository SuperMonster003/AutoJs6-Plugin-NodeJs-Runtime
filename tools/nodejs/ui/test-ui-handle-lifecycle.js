#!/usr/bin/env node

"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const CPP_SOURCE = path.resolve(__dirname, "../../../app/src/main/cpp/node_bridge_sources.cpp");

runTests().catch((error) => {
  process.stderr.write(`${error && error.stack ? error.stack : error}${os.EOL}`);
  process.exitCode = 1;
});

async function runTests() {
  const source = fs.readFileSync(CPP_SOURCE, "utf8");
  const createHandle = uiHandleFactory(source);

  await assertProviderCloseTerminatesHandle(createHandle);
  process.stdout.write(`PASS provider close event terminates UI handle after listener delivery${os.EOL}`);

  await assertExplicitCloseCancelsPendingDrain(createHandle);
  process.stdout.write(`PASS explicit close cancels pending UI drain and stays terminal${os.EOL}`);

  process.stdout.write(`PASS 2 UI handle terminal lifecycle scenarios${os.EOL}`);
}

async function assertProviderCloseTerminatesHandle(createHandle) {
  const harness = createHarness();
  const create = createHandle(harness);
  let bindingCallback = null;
  let bindingUnsubscribeCount = 0;
  const state = {
    get() {
      return "queued-after-provider-close";
    },
    subscribe(callback) {
      bindingCallback = callback;
      return function unsubscribe() {
        bindingUnsubscribeCount += 1;
      };
    },
  };
  const handle = create(
    { id: "ui-terminal-provider-close" },
    { drainIntervalMs: 20 },
    [{ state, key: "title", id: "label", property: "text" }],
  );

  assert.equal(harness.timerCount(), 0, "binding setup must not drain events before listeners attach");
  assert.equal(harness.callsByMethod("drainEvents").length, 0, "binding setup consumed events before listeners attach");
  assert.equal(typeof bindingCallback, "function", "binding subscription was not installed");

  const received = [];
  let updateDuringClose = null;
  let closeDuringClose = null;
  const unsubscribeClose = handle.on("close", (event) => {
    assert.equal(harness.clearIntervalCount(), 0, "terminal cleanup ran before close listener delivery");
    assert.equal(bindingUnsubscribeCount, 0, "binding was removed before close listener delivery");
    assert.equal(Object.isFrozen(event), true, "close event must remain frozen");
    received.push(event);
    updateDuringClose = handle.update({ id: "label", text: "must-not-dispatch" }).then(
      () => null,
      (error) => error,
    );
    closeDuringClose = handle.close();
  });
  assert.equal(harness.timerCount(), 1, "close listener must start lifecycle draining");
  assert.equal(harness.callsByMethod("drainEvents").length, 1, "close listener must start one immediate drain");

  harness.resolveNextDrain([
    { type: "close", reason: "activity_destroyed" },
    { type: "close", reason: "duplicate_terminal_event" },
    { type: "update", id: "must-not-deliver" },
  ]);
  bindingCallback({ keys: ["title"] });
  await flushMicrotasks();

  assert.equal(received.length, 1, "a terminal batch must deliver exactly one close event");
  assert.deepEqual(
    received[0],
    { type: "close", reason: "activity_destroyed", handleId: "ui-terminal-provider-close" },
  );
  assert.equal(harness.clearIntervalCount(), 1, "terminal cleanup must clear the drain timer exactly once");
  assert.equal(harness.timerCount(), 0, "terminal cleanup left a live drain timer");
  assert.equal(bindingUnsubscribeCount, 1, "terminal cleanup must unsubscribe bindings exactly once");
  assert.equal(harness.callsByMethod("batchUpdate").length, 0, "queued binding patches escaped after close");
  assert.equal(harness.callsByMethod("update").length, 0, "close listener dispatched a stale update");
  assertClosedError(await updateDuringClose, "update");
  await closeDuringClose;

  assert.throws(
    () => handle.on("click", () => {}),
    closedErrorMatcher("on"),
    "terminal handle accepted a new listener",
  );
  await assert.rejects(handle.update({ id: "label", text: "late" }), closedErrorMatcher("update"));
  await assert.rejects(handle.batchUpdate([{ id: "label", text: "late" }]), closedErrorMatcher("batchUpdate"));
  await handle.close();
  await handle.close();
  assert.equal(harness.callsByMethod("close").length, 0, "provider terminal event caused a duplicate ui.close dispatch");

  unsubscribeClose();
  assert.equal(bindingUnsubscribeCount, 1, "post-terminal unsubscribe repeated binding cleanup");
}

async function assertExplicitCloseCancelsPendingDrain(createHandle) {
  const harness = createHarness();
  const create = createHandle(harness);
  const handle = create({ id: "ui-terminal-explicit-close" }, { drainIntervalMs: 20 }, []);
  const received = [];
  const unsubscribe = handle.on("*", (event) => received.push(event));

  assert.equal(harness.callsByMethod("drainEvents").length, 1, "listener did not start an immediate drain");
  assert.equal(harness.timerCount(), 1, "listener did not start the drain timer");

  await handle.close();
  assert.equal(harness.callsByMethod("close").length, 1, "explicit close did not dispatch exactly once");
  assert.equal(harness.clearIntervalCount(), 1, "explicit close did not clear its timer exactly once");
  assert.equal(harness.timerCount(), 0, "explicit close left its timer active");

  harness.resolveNextDrain([{ type: "shown" }, { type: "close", reason: "late" }]);
  await flushMicrotasks();
  assert.deepEqual(received, [], "an in-flight drain delivered events after explicit close");
  await handle.close();
  await handle.close();
  await assert.rejects(handle.update({ id: "label", text: "late" }), closedErrorMatcher("update"));
  assert.equal(harness.callsByMethod("close").length, 1, "terminal handle repeated the remote close dispatch");
  unsubscribe();
}

function uiHandleFactory(source) {
  const embedded = [
    embeddedFunctionSource(source, "__autojs6_ui_handle_id"),
    embeddedFunctionSource(source, "__autojs6_ui_closed_error"),
    embeddedFunctionSource(source, "__autojs6_ui_handle"),
  ].join("\n");

  return function createWithHarness(harness) {
    return new Function(
      "__autojs6_bridge_error",
      "__autojs6_ui_invalid_argument_error",
      "__autojs6_ui_options",
      "__autojs6_ui_bridge_options",
      "__autojs6_ui_descriptor",
      "__autojs6_call_autojs",
      "setInterval",
      "clearInterval",
      "setTimeout",
      `${embedded}\nreturn __autojs6_ui_handle;`,
    )(
      bridgeError,
      (method, message) => bridgeError(message, "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED", "ui", method),
      (value) => value && typeof value === "object" && !Array.isArray(value) ? value : {},
      (options, fallbackMs) => ({ timeoutMs: options.timeoutMs || fallbackMs, permissions: ["ui"] }),
      (value) => value,
      harness.bridge,
      harness.setInterval,
      harness.clearInterval,
      setTimeout,
    );
  };
}

function createHarness() {
  const calls = [];
  const drains = [];
  const timers = new Map();
  let nextTimerId = 1;
  let clearedIntervals = 0;

  return {
    bridge(moduleName, methodName, args, options) {
      calls.push({ moduleName, methodName, args, options });
      if (methodName === "drainEvents") {
        const pending = deferred();
        drains.push(pending);
        return pending.promise;
      }
      return Promise.resolve(undefined);
    },
    setInterval(callback, intervalMs) {
      const id = nextTimerId;
      nextTimerId += 1;
      timers.set(id, { callback, intervalMs });
      return id;
    },
    clearInterval(id) {
      assert.equal(timers.has(id), true, `attempted to clear unknown interval ${id}`);
      timers.delete(id);
      clearedIntervals += 1;
    },
    resolveNextDrain(events) {
      const pending = drains.shift();
      assert.ok(pending, "no pending drain request was available");
      pending.resolve(events);
    },
    callsByMethod(methodName) {
      return calls.filter((entry) => entry.methodName === methodName);
    },
    timerCount() {
      return timers.size;
    },
    clearIntervalCount() {
      return clearedIntervals;
    },
  };
}

function bridgeError(message, code, moduleName, methodName) {
  const error = new Error(message);
  Object.defineProperties(error, {
    code: { value: code, enumerable: true },
    module: { value: moduleName, enumerable: true },
    method: { value: methodName, enumerable: true },
  });
  return error;
}

function assertClosedError(error, methodName) {
  assert.ok(error instanceof Error, `${methodName}: expected an Error`);
  assert.equal(error.code, "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST", `${methodName}: unstable error code`);
  assert.equal(error.module, "ui", `${methodName}: wrong error module`);
  assert.equal(error.method, methodName, `${methodName}: wrong error method`);
  assert.equal(
    error.message,
    `AutoJs6 ui.${methodName} requires an active UI handle.`,
    `${methodName}: unstable error message`,
  );
}

function closedErrorMatcher(methodName) {
  return (error) => {
    assertClosedError(error, methodName);
    return true;
  };
}

function embeddedFunctionSource(source, name) {
  const start = source.indexOf(`function ${name}(`);
  assert.ok(start >= 0, `could not locate embedded function ${name}`);
  const bodyStart = source.indexOf("{", start);
  assert.ok(bodyStart >= 0, `could not locate embedded function body ${name}`);
  let depth = 0;
  for (let index = bodyStart; index < source.length; index += 1) {
    if (source[index] === "{") depth += 1;
    if (source[index] !== "}") continue;
    depth -= 1;
    if (depth === 0) return source.slice(start, index + 1);
  }
  assert.fail(`could not locate embedded function end ${name}`);
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

async function flushMicrotasks() {
  for (let index = 0; index < 6; index += 1) {
    await Promise.resolve();
  }
}
