"use strict";

const assert = require("node:assert/strict");

exports.pirates = function () {
  const target = require.resolve("./loader-transform.js");
  const revert = require("pirates").addHook(
    source => source.replace("ORIGINAL", "HOOKED"),
    { exts: [".js"], matcher: filename => filename === target }
  );
  try {
    delete require.cache[target];
    assert.equal(require(target), "HOOKED");
  } finally {
    revert();
    delete require.cache[target];
  }
  assert.equal(require(target), "ORIGINAL");
  return true;
};

exports.mockRequire = function () {
  const Module = require("node:module");
  const mock = require("mock-require");
  const consumer = require.resolve("./mock-consumer.cjs");
  const dependency = require.resolve("./mock-dependency.cjs");
  assert.equal(Module._resolveFilename("./mock-dependency.cjs", module), dependency);
  assert.equal(require(consumer).value, 7);
  mock(dependency, { value: 42 });
  try {
    assert.equal(require(consumer).value, 7);
    assert.equal(mock.reRequire(consumer).value, 42);
    assert.equal(require.cache[consumer].exports.value, 42);
  } finally {
    mock.stop(dependency);
    delete require.cache[consumer];
  }
  assert.equal(require(consumer).value, 7);
  return true;
};
