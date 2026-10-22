const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
const factory = new Function(source.slice(source.indexOf('  // Shared AI facade.'), source.indexOf('  // End shared AI facade.')) + ';return __autojs6_create_ai;')();
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

function fixture(hook) {
  const owners = new Map(), calls = [], handles = new Map(); let serial = 0;
  const send = (owner, id, type, value) => {
    const bytes = Buffer.from(JSON.stringify({type, value}));
    for (let i = 0; i < bytes.length; i += 127) owners.get(owner)?.push({id, data: bytes.subarray(i, i + 127).toString('base64'), last: i + 127 >= bytes.length});
  };
  const call = async (module, method, args, options) => {
    assert.equal(module, 'aiAgent'); assert.deepEqual(options.permissions, ['ai.agent']); calls.push({method, args});
    if (method === 'hasAccess') return true;
    if (method === 'context') return {runId: 'registered', parameters: {count: 3}, presetName: 'default'};
    if (method === 'result') return true;
    if (method === 'open') {
      const id = 'owner-' + ++serial; owners.set(id, []);
      await hook?.({method, id, send, owners, handles}); return {id};
    }
    if (method === 'close') { for (const [id, h] of handles) if (h.owner === args[0]) handles.delete(id); return owners.delete(args[0]); }
    assert(owners.has(args[0]));
    if (method === 'release') { handles.delete(args[1]); return true; }
    if (method === 'ack' || method === 'cancel') return true;
    if (method === 'poll') {
      const packets = owners.get(args[0]), wanted = new Set(args[1]), result = [];
      for (let i = 0; i < packets.length && result.length < 40;) {
        if (wanted.has(packets[i].id)) result.push(...packets.splice(i, 1)); else i++;
      }
      return result;
    }
    assert.equal(method, 'start');
    const [owner, operation, input] = args, id = 'task-' + ++serial;
    if (await hook?.({method, owner, id, operation, input, send, handles, owners}) === false) return {id};
    let value;
    if (operation === 'run' || operation === 'get') {
      if (operation === 'get' && input[0] === 'missing') value = null;
      else {
        const handle = 'handle-' + ++serial;
        value = {handle, id: operation === 'get' ? input[0] : 'run-' + serial, goal: operation === 'get' ? 'existing' : input[0],
          state: 'queued', startedAt: 42, detached: input[2]?.detached ?? false, interaction: input[2]?.interaction ?? 'plugin'};
        handles.set(handle, {...value, owner, owned: operation === 'run'});
      }
    } else if (operation === 'status') value = {state: 'attached', queuedCount: 0};
    else if (operation === 'catalog') value = [{id: 'registered-script', description: 'fixture'}];
    else if (operation === 'presets') value = ['default'];
    else if (operation === 'list') value = [{id: 'history', state: 'completed'}];
    else if (operation === 'run.cancel') {
      const h = handles.get(input[0]);
      send(owner, input[0], 'event', {type: 'done', id: h.id, status: 'cancelled'}); value = true;
    } else value = true;
    send(owner, id, 'resolve', value); return {id};
  };
  const ai = factory(call);
  return {ai, agent: ai.agent, call, calls, owners, handles, send, event(type, value = {}) {
    for (const h of handles.values()) send(h.owner, h.handle, 'event', {type, runId: h.id, sequence: 1, ...value});
  }};
}

test('Agent roots, assistant snapshots, query results and registered-script channel use their own permission', async () => {
  const f = fixture(), base = {budget: {maxSteps: 4}, context: 'before'};
  const assistant = f.agent.create(base); base.context = 'after'; assistant.options.budget.maxSteps = 999;
  assert.equal(assistant.options.context, 'before'); assert.equal(assistant.options.budget.maxSteps, 4);
  const run = await assistant.run('goal', {budget: {maxSteps: 2}, interaction: 'script'});
  assert.equal(run.id.startsWith('run-'), true); assert.equal(run.startedAt, 42);
  const start = f.calls.find(call => call.method === 'start' && call.args[1] === 'run');
  assert.deepEqual(start.args[2], ['goal', {budget: {maxSteps: 4}, context: 'before'}, {budget: {maxSteps: 2}, interaction: 'script'}]);
  assert.deepEqual(await f.agent.presets(), ['default']); assert.equal((await f.agent.catalog())[0].id, 'registered-script');
  assert.equal((await f.agent.list())[0].state, 'completed'); assert.equal((await f.agent.status()).state, 'attached');
  assert.deepEqual(await f.agent.context(), {runId: 'registered', parameters: {count: 3}, presetName: 'default'});
  assert.equal(await f.agent.result({answer: true}), true); assert(await f.agent.hasAccess()); assert(await f.agent.requestAccess());
  assert.equal(await f.agent.get('missing'), null);
  await f.ai.close(); assert.equal(f.owners.size, 0);
});

test('Agent events preserve supplementary Unicode, listener removal, interaction and terminal results', async () => {
  const f = fixture(), run = await f.agent.run('events', {interaction: 'script'}), sequence = [];
  const unused = () => assert.fail('Removed listener ran');
  run.on('progress', unused).off('progress', unused);
  run.once('progress', value => sequence.push(value.message));
  run.on('state', value => sequence.push(value.to));
  run.on('input', value => { assert.equal(value.readOnly, false); sequence.push(value.requestId); });
  const text = '\u96ea\ud83e\uddea'.repeat(400);
  f.event('state', {from: 'queued', to: 'running'}); f.event('progress', {message: text}); f.event('progress', {message: 'not twice'});
  f.event('input', {requestId: 'input-1', question: 'Answer'});
  while (sequence.length < 3) await delay(5);
  assert.deepEqual(sequence, ['running', text, 'input-1']); assert.equal(run.state, 'running');
  assert(await run.respond('input-1', ['one', 'two'])); assert(await run.confirm('confirmation-1', true, 'once'));
  f.event('done', {id: run.id, status: 'completed', text});
  assert.equal((await run.result).text, text); assert.equal(run.state, 'completed');
  await delay(5); assert.equal(f.handles.size, 0); await f.agent.close();
});

test('Agent join timeout leaves a run alive, then cancel resolves the shared result', async () => {
  const f = fixture(), run = await f.agent.run('wait');
  await assert.rejects(run.join(15), {code: 'JOIN_TIMEOUT'});
  assert(!f.calls.some(call => call.args[1] === 'run.cancel')); assert.equal(run.state, 'queued');
  await assert.rejects(run.join(0), {code: 'INVALID_REQUEST'});
  assert.equal(await run.cancel('test'), run);
  assert.equal((await run.result).status, 'cancelled'); assert.equal((await run.join()).status, 'cancelled');
  await f.agent.close();
});

test('Agent input validation and pre-aborted admission cannot allocate a host task', async () => {
  const f = fixture();
  assert.throws(() => f.agent.create(), {code: 'INVALID_REQUEST'});
  assert.throws(() => f.agent.create({unexpected: true}), {code: 'INVALID_REQUEST'});
  assert.throws(() => f.agent.create({budget: {maxSteps: NaN}}), {code: 'INVALID_REQUEST'});
  assert.throws(() => f.agent.create({context: undefined}), {code: 'INVALID_REQUEST'});
  const controller = new AbortController(); controller.abort();
  await assert.rejects(f.agent.run('never', {}, {signal: controller.signal}), {code: 'CANCELLED'});
  assert.equal(f.calls.length, 0); await f.agent.close();
});

test('Agent close releases a late client creation and the next generation remains usable', async () => {
  let unblock, entered;
  const waiting = new Promise(resolve => { entered = resolve; });
  let first = true;
  const f = fixture(async ({method}) => { if (method === 'open' && first) {
    first = false; entered(); await new Promise(resolve => { unblock = resolve; });
  } });
  const pending = f.agent.run('late'); await waiting;
  const closed = f.agent.close(); unblock(); await closed;
  await assert.rejects(pending, {code: 'SESSION_CLOSED'}); assert.equal(f.owners.size, 0);
  assert.equal((await f.agent.status()).state, 'attached'); await f.agent.close();
});

test('Agent operation timeout cancels its task even when admission replies arrive later', async () => {
  const f = fixture(async ({operation}) => { if (operation === 'run') { await delay(30); return false; } });
  await assert.rejects(f.agent.run('late', {}, {timeoutMs: 10}), {code: 'TIMEOUT'});
  await delay(45); assert(f.calls.some(call => call.method === 'cancel'));
  await f.agent.close();
});

test('Agent listener failures reject the result and request owned-run cleanup', async () => {
  const f = fixture(), run = await f.agent.run('listener');
  run.on('progress', () => { throw new Error('listener failed'); });
  f.event('progress', {message: 'test'});
  await assert.rejects(run.result, /listener failed/);
  await delay(5); assert(f.calls.some(call => call.method === 'release' && call.args[2] === true));
  await f.agent.close();
});

test('Agent failure hints and historical handles remain typed without replaying old steps', async () => {
  const f = fixture(), run = await f.agent.get('history');
  assert.equal(run.goal, 'existing');
  f.event('handle_error', {error: {code: 'PLUGIN_UNAVAILABLE', message: 'Unavailable', hint: 'Open Plugin Center'}});
  await assert.rejects(run.result, {name: 'AgentError', code: 'PLUGIN_UNAVAILABLE', hint: 'Open Plugin Center'});
  assert.equal(run.error.hint, 'Open Plugin Center'); await f.agent.close();
});

test('Closing Agent during the access preflight prevents a late authorization window', async () => {
  let release, entered;
  const pending = new Promise(resolve => { entered = resolve; });
  const agent = factory(async (_, method) => {
    assert.equal(method, 'hasAccess'); entered();
    return await new Promise(resolve => { release = resolve; });
  }).agent;
  const request = agent.requestAccess(); await pending; await agent.close(); release(false);
  await assert.rejects(request, {code: 'SESSION_CLOSED'});
});

test('Closing Agent cancels its pending application authorization request', async () => {
  let release, entered, token;
  const pending = new Promise(resolve => { entered = resolve; });
  const agent = factory(async (_, method, args) => {
    if (method === 'hasAccess') return false;
    if (method === 'requestAccess') { token = args[0]; entered(); return await new Promise(resolve => { release = resolve; }); }
    assert.equal(method, 'close'); assert.equal(args[0], token); release(false); return true;
  }).agent;
  const request = agent.requestAccess(); await pending; await agent.close();
  assert.equal(await request, false);
});

test('Agent authorization timeout also closes the native authorization action', async () => {
  let token, closed = 0;
  const agent = factory(async (_, method, args) => {
    if (method === 'hasAccess') return false;
    if (method === 'requestAccess') { token = args[0]; throw Object.assign(new Error('expired'), {code: 'TIMEOUT'}); }
    assert.equal(method, 'close'); assert.equal(args[0], token); closed++; return true;
  }).agent;
  await assert.rejects(agent.requestAccess(), {code: 'TIMEOUT'}); assert.equal(closed, 1);
  await agent.close();
});

test('Granting Agent access again replaces a cached client without replaying a task', async () => {
  const f = fixture(); let granted = true;
  const agent = factory(async (...args) => {
    const [, method] = args;
    if (method === 'hasAccess') return granted;
    if (method === 'requestAccess') { granted = true; return true; }
    return f.call(...args);
  }).agent;
  await agent.status(); const previous = [...f.owners.keys()][0]; granted = false;
  assert(await agent.requestAccess()); assert(!f.owners.has(previous));
  assert.equal((await agent.status()).state, 'attached');
  assert.equal(f.calls.filter(call => call.method === 'open').length, 2);
  assert(!f.calls.some(call => call.method === 'start' && call.args[1] === 'run'));
  await agent.close();
});

test('Repeated Agent get calls reuse a live facade without allocating extra native observers', async () => {
  const f = fixture(), run = await f.agent.run('owned');
  for (let i = 0; i < 12; i++) assert.equal(await f.agent.get(run.id), run);
  await delay(5); assert.equal(f.handles.size, 1);
  assert(!f.calls.some(call => call.method === 'start' && call.args[1] === 'get'));
  const [first, second] = await Promise.all([f.agent.get('existing'), f.agent.get('existing')]);
  assert.equal(first, second); await delay(5); assert.equal(f.handles.size, 2);
  assert(f.calls.filter(call => call.method === 'release').every(call => call.args[2] === false));
  await run.cancel(); await run.result; await f.agent.close();
});
