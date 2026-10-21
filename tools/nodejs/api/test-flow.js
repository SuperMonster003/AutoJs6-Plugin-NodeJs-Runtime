const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const source = fs.readFileSync(process.env.AUTOJS6_FLOW_SOURCE || path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
const block = source.slice(source.indexOf('  // Shared flow core.'), source.indexOf('  // End shared flow core.'));
assert(block.length > 1000);
const factory = new Function(block + ';return __autojs6_create_flow;')();
const create = (call, logger) => factory(call, logger, require('node:async_hooks').AsyncLocalStorage);
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const local = () => create(() => { throw Error('Unexpected host call'); });
const rejects = (value, code, reason) => assert.rejects(value.toPromise(), error => error.code === code && (reason === undefined || error.reason === reason));

test('Flow adoption, chaining, Promise assimilation and inspection', async () => {
  const flow = local(), initial = flow.of(3); let peeked;
  assert(initial instanceof flow.Flow); assert.equal(flow.Flow(initial), initial);
  const result = initial.map(async value => value + 2).peek(value => { peeked = value; }).filter(value => value > 3).run(value => value * 2);
  assert.equal(await result, 10); assert.equal(peeked, 5); assert.equal(result.state, 'fulfilled'); assert.equal(result.value, 10);
  assert.equal(await new flow.Flow(Promise.resolve(7)), 7);
  assert.match(result.toString(), /fulfilled/); await delay(1); assert.equal(flow.pending, 0); assert.equal(flow.workers, 0);
});
test('Condition truthiness keeps zero, empty strings and arrays, but not false, null or NaN', async () => {
  const flow = local();
  for (const value of [0, -0, '', [], {}]) assert.equal(await flow.wait(() => value), value);
  for (const value of [false, null, undefined, NaN]) await rejects(flow.wait(() => value, {timeout: 0}), 'TIMEOUT');
  assert.equal(await flow.wait(Promise.resolve(false)), false); assert.equal(await flow.wait(Promise.resolve(undefined)), undefined);
});
test('Wait callbacks, polling counts and while preserve values', async () => {
  const flow = local(); let count = 0;
  assert.equal(await flow.waitUntil(() => ++count === 3 ? 'ready' : false, {interval: 1, times: 3}, value => value + '!'), 'ready!');
  assert.equal(count, 3); count = 0;
  assert.equal(await flow.waitWhile(() => ++count < 3, 1000, 1), true); assert.equal(count, 3);
  assert.equal(await flow.wait(() => false, {times: 2, interval: 1}, null, error => error.code), 'TIMEOUT');
});
test('Defaults are validated atomically and captured when each step is created', async () => {
  const flow = local(), original = flow.defaults();
  assert.throws(() => flow.defaults({interval: 1, timeout: -1})); assert.deepEqual(flow.defaults(), original);
  assert.throws(() => flow.defaults({root: {}}));
  flow.defaults({timeout: 1000, interval: 1}); const step = flow.wait(() => true); flow.defaults({timeout: 0}); assert.equal(await step, true);
  assert.equal(flow.wait, flow.waitAsync); assert.equal(flow.sleep, flow.delay); assert.equal(flow.waitForHidden, flow.waitForGone);
});

test('Scope inherits after settlement, captures inputs and isolates sibling branches', async () => {
  const seen=[], root={windowId:7,indexInParent:0,packageName:'fixture',bounds:{left:0,top:0,right:90,bottom:90}};
  const flow=create(async(module,method,args)=>{seen.push(args);return {available:true,value:args[1]?.resultType==='text'?'inside':root};});
  const options={root,compass:'p',resultType:'text'};
  const base=flow.of('input'), scoped=base.scope(options);options.root.bounds.right=5;options.compass='c0';
  assert.equal(await scoped,'input');assert.equal(await scoped.wait('row'),'inside');
  assert.equal(seen[0][1].root.bounds.right,90);assert.equal(seen[0][1].compass,'p');
  await base.wait('outside');assert.equal(seen[1].length,1);
  await scoped.wait('override',{compass:'%',resultType:'#'});
  assert.equal(seen[2][1].compass,'%');assert.equal(seen[2][1].resultType,'#');
  await scoped.scope({root:null,compass:null}).wait('still scoped');assert.equal(seen[3][1].compass,'p');
  await scoped.wait('later');assert.equal(seen[4][1].resultType,'text');
  assert.throws(()=>base.scope({root:{}}));assert.throws(()=>base.scope({compass:42}));assert.throws(()=>base.scope({resultType:42}));
});

test('Within updates only its branch root; polling helpers retain that root', async () => {
  const root={windowId:8,indexInParent:0,packageName:'fixture',bounds:{left:0,top:0,right:90,bottom:90}}, seen=[];
  const flow=create(async(module,method,args)=>{seen.push(args);return {available:true,value:root};});
  const base=flow.of(0), scoped=base.within('container',value=>value);
  assert.deepEqual(await scoped,root);
  await scoped.sleep(1).wait('child');assert.deepEqual(seen.at(-1)[1].root,root);
  let optional;
  await scoped.whenPresent('optional',value=>{optional=value;});assert.deepEqual(optional,root);assert.deepEqual(seen.at(-1)[1].root,root);
  await scoped.repeatUntil(()=>{throw Error('already found');},'done');assert.deepEqual(seen.at(-1)[1].root,root);
  await base.wait('global');assert.equal(seen.at(-1).length,1);
  await rejects(base.within(()=>42),'INVALID_TARGET');
  const cancelled=base.within(()=>false,{interval:1});cancelled.cancel();await rejects(cancelled,'CANCELLED');
});

test('Scoped host failures retain Flow error information and direct snapshots request relocation',async()=>{
  const root={windowId:9,indexInParent:0,packageName:'fixture',bounds:{left:0,top:0,right:90,bottom:90}};
  const flow=create(async(module,method,args)=>{
    assert.equal(args.length,2);return {available:true,error:{code:'INVALID_TARGET',step:'target',reason:'stale snapshot'}};
  });
  await rejects(flow.of(0).scope({root}).wait('child'),'INVALID_TARGET','stale snapshot');
  await rejects(flow.wait(root),'INVALID_TARGET','stale snapshot');
});
test('Cancelling one fork preserves its sibling; cancelling the sole branch stops its parent', async () => {
  const flow = local(), parent = flow.sleep(30), a = parent.map(() => 'a'), b = parent.map(() => 'b');
  assert(a.cancel()); await rejects(a, 'CANCELLED'); assert.equal(await b, 'b'); assert.equal(parent.state, 'fulfilled');
  const p = flow.sleep(10000), leaf = p.map(() => 3); assert(leaf.cancel()); assert.equal(p.state, 'cancelled'); assert(!leaf.cancel());
  await rejects(leaf, 'CANCELLED'); await delay(5); assert.equal(flow.pending, 0);
});
test('Cancel stops polling and ignores late callback results; errors remain catchable', async () => {
  const flow = local(); let release, calls = 0;
  const waiting = flow.wait(() => { calls++; return new Promise(resolve => { release = resolve; }); });
  await delay(2); waiting.cancel(); release('late'); assert.equal(await waiting.catch(error => error.code), 'CANCELLED');
  await delay(5); assert.equal(calls, 1); assert.equal(waiting.state, 'cancelled'); assert.equal(flow.pending, 0);
});
test('Wait deadlines interrupt asynchronous predicates and permit retry of just the failed step', async () => {
  const flow = local(); let calls = 0, first = 0;
  const result = flow.run(() => ++first).wait(() => { calls++; return calls < 3 ? new Promise(() => {}) : 'ready'; }, {timeout: 15}).retry(2, 1);
  assert.equal(await result, 'ready'); assert.equal(calls, 3); assert.equal(first, 1);
  await rejects(flow.wait(new Promise(() => {}), {timeout: 10}), 'TIMEOUT');
  calls = 0;
  assert.equal(await flow.wait(() => ++calls === 1 ? flow.sleep(10000) : 'done', {timeout:15}).retry(1), 'done');
  await delay(1); assert.equal(flow.pending, 0, 'The timed-out callback must release its created Flow');
});
test('Chain deadlines bound later sleeps, callbacks and retries, and finally observes failure', async () => {
  const flow = local(); let final = 0;
  const result = flow.of('value').timeout(15).run(() => new Promise(() => {})).finally(() => { final++; });
  await rejects(result, 'TIMEOUT', 'chain'); assert.equal(final, 1);
  await rejects(flow.of(1).timeout(10).sleep(100).retry(2, 1), 'TIMEOUT', 'chain');
  assert.equal(await flow.of(2).timeout(1000).sleep(1), 2);
});
test('Retry does not rerun cancellations, adopted Promises or the preceding chain', async () => {
  const flow = local(); let calls = 0;
  const task = flow.run(() => { calls++; throw Error('fail'); }).retry(2, 1);
  await assert.rejects(task.toPromise(), /fail/); assert.equal(calls, 3);
  const cancelled = flow.sleep(1000); cancelled.cancel(); await rejects(cancelled.retry(2), 'CANCELLED');
  await assert.rejects(flow.Flow(Promise.reject(Error('promise'))).retry(2).toPromise(), /promise/);
});
test('Finally preserves results, awaits returned tasks and propagates its own error', async () => {
  const flow = local(); let done = false;
  assert.equal(await flow.of(2).finally(async () => { await delay(2); done = true; }), 2); assert(done);
  await assert.rejects(flow.of(2).finally(() => { throw Error('cleanup'); }).toPromise(), /cleanup/);
  await rejects(flow.of(0).filter(() => false), 'INVALID_TARGET', 'filtered out');
});
test('Stability snapshots survive in-place mutation, custom projection and comparator', async () => {
  const flow = local(), value = {count: 0}; let calls = 0;
  const stable = flow.waitForStable(() => { if (++calls < 4) value.count++; return value; }, {interval: 2, stableFor: 7, timeout: 1000, snapshot: item => ({count: item.count})});
  assert.equal((await stable).count, 3); assert(calls >= 4);
  assert.equal(await flow.waitForStable(() => 'same', {stableFor: 2, interval: 2, compare: async (a, b) => a === b}), 'same');
  assert.throws(() => flow.waitForStable(() => true, {snapshot: x => x, compare: 'bounds'}));
});
test('Missing targets reset stability or fail explicitly, and hidden waits return true', async () => {
  const flow = local(); let n = 0;
  await rejects(flow.waitForStable(() => ++n === 1 ? {id: 1} : false, {interval: 1, stableFor: 10, missing: 'fail'}), 'INVALID_TARGET', 'missing');
  assert.equal(await flow.waitForHidden(() => false, {interval: 1, stableFor: 2}), true);
  n = 0; assert.equal((await flow.waitForVisible(() => { n++; return {visibleToUser: n > 2, id: 1}; }, {interval: 1, stableFor: 2})).id, 1);
});
test('Selector snapshots and foreground waits use the granted stateless host query', async () => {
  const calls = [], flow = create(async (module, method, args, options) => {
    calls.push({module, method, args}); assert.deepEqual(options.permissions, ['accessibility']); assert(options.signal instanceof AbortSignal);
    return {available: true, value: method === 'flowForeground' ? {packageName: 'test.app', activityName: 'test.Activity'} : {text: 'ready', visibleToUser: true}};
  });
  assert.equal((await flow('ready')).text, 'ready'); assert.deepEqual(calls[0].args, [{content: 'ready'}]);
  await flow.wait(/ready/i); assert.deepEqual(calls[1].args, [{contentMatches: {pattern: 'ready', flags: 'i'}}]);
  assert.equal(await flow.waitForPackage('test.app'), true); assert.equal(await flow.waitForActivity('test.Activity'), true);
});
test('Service outage is distinct from absence, recovers only when requested, and grants still reject', async () => {
  let ready = false; const flow = create(async () => ({available: ready, value: {text: 'ready'}}));
  await rejects(flow.wait('ready'), 'A11Y_UNAVAILABLE');
  setTimeout(() => { ready = true; }, 8); assert.equal((await flow.wait('ready', {recoverService: true, interval: 1})).text, 'ready');
  ready = false; await rejects(flow.waitForHidden('ready', {recoverService: true, interval: 1, timeout: 10}), 'A11Y_UNAVAILABLE');
  const denied = create(async () => { throw Object.assign(Error('denied'), {code: 'NOT_GRANTED'}); });
  await rejects(denied.wait('ready', {recoverService: true}), 'NOT_GRANTED');
});
test('whenPresent handles optional absence, preserves input and awaits the handler', async () => {
  const flow = local(); let called = 0;
  assert.equal(await flow.of(7).whenPresent(() => false, () => called++, {times: 2, interval: 1}), 7); assert.equal(called, 0);
  assert.equal(await flow.of(7).whenPresent(() => 'yes', async value => { await delay(1); assert.equal(value, 'yes'); called++; }), 7); assert.equal(called, 1);
  await assert.rejects(flow.whenPresent(() => { throw Error('predicate'); }, () => {}).toPromise(), /predicate/);
});
test('repeatUntil checks before actions, honors limits and classifies action retries', async () => {
  const flow = local(); let count = 0;
  assert.equal(await flow.repeatUntil(attempt => { assert.equal(attempt, ++count); }, () => count === 3 ? 'done' : false, {interval: 1}), 'done'); assert.equal(count, 3);
  count = 0; assert.equal(await flow.repeatUntil(() => count++, () => true), true); assert.equal(count, 0);
  await rejects(flow.repeatUntil(() => count++, () => false, {timeout: 0}), 'TIMEOUT'); assert.equal(count, 0);
  await rejects(flow.repeatUntil(() => count++, () => false, {maxAttempts: 2, interval: 1}), 'TIMEOUT', 'attempts'); assert.equal(count, 2);
  count = 0; assert.equal(await flow.repeatUntil(() => { count++; throw Error('retry'); }, () => count === 2, {interval: 1, retryOn: async () => true}), true);
});
test('Combinators cancel created waits but preserve supplied sibling Flows', async () => {
  const flow = local(); assert.deepEqual(await flow.all([flow.of(1), Promise.resolve(2), () => 3]), [1, 2, 3]);
  assert.deepEqual(await flow.all([]), []); await rejects(flow.race([]), 'INVALID_TARGET'); await rejects(flow.any([]), 'INVALID_TARGET');
  let calls = 0; const external = flow.sleep(20).map(() => 'other');
  assert.equal(await flow.race([Promise.resolve('fast'), () => { calls++; return false; }, external]), 'fast');
  const old = calls; await delay(8); assert.equal(calls, old); assert.equal(await external, 'other');
  assert.equal(await flow.any([Promise.reject(Error('x')), Promise.resolve('ok')]), 'ok');
});
test('Self adoption rejects; cancellation stops owned callback flows and cancelAll clears timers', async () => {
  const flow = local(); let cycle; cycle = flow.run(() => cycle); await assert.rejects(cycle.toPromise(), /itself|Cyclic/);
  let child; const parent = flow.run(() => { child = flow.sleep(10000); return child; }); await delay(2); parent.cancel();
  await rejects(parent, 'CANCELLED'); assert.equal(child.state, 'cancelled');
  flow.sleep(10000); flow.wait(() => false, {timeout: Infinity}); assert(flow.cancelAll() >= 2); await delay(2); assert.equal(flow.pending, 0);
});
test('Flows created after an asynchronous callback resumes still belong to the cancelled step', async () => {
  const flow = local(); let late;
  try {
    const parent = flow.run(async () => { await delay(20); late = flow.sleep(10000); return late; });
    await delay(2); parent.cancel(); await rejects(parent, 'CANCELLED');
    await delay(40); assert.equal(late.state, 'cancelled'); assert.equal(flow.pending, 0);
    assert.equal(await flow.run(async () => { await delay(1); return flow.of(8); }), 8);
  } finally { flow.cancelAll(); }
});
