"nodejs";

import assert from "node:assert/strict";
import vm from "node:vm";

const context = vm.createContext({value: 40});
const script = new vm.Script("value += 2; value");
assert.equal(script.runInContext(context), 42);
assert.equal(vm.runInNewContext("typeof value"), "undefined");
assert.throws(() => vm.runInContext("while (true) {}", context, {timeout: 20}),
  error => error.code === "ERR_SCRIPT_EXECUTION_TIMEOUT");
assert.equal(vm.runInContext("value", context), 42);
console.log("sample.vm-context.answer=" + context.value);
console.log("sample.vm-context=PASS");
