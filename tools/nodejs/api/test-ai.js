const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
const factory = new Function(source.slice(source.indexOf('  // Shared AI facade.'), source.indexOf('  // End shared AI facade.')) + ';return __autojs6_create_ai;')();
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

function fixture(hook) {
  const owners = new Map(), sessions = new Map(), calls = []; let sequence = 0;
  const response = {text: '\u96ea\ud83e\uddea'.repeat(300), reasoning: 'reason', usage: {inputTokens: 4, outputTokens: 8}, route: 'plugin'};
  const send = (owner, id, type, value) => {
    const bytes = Buffer.from(JSON.stringify({type, value}));
    for (let i = 0; i < bytes.length; i += 17) owners.get(owner)?.push({id,
      data: bytes.subarray(i, i + 17).toString('base64'), last: i + 17 >= bytes.length});
  };
  const call = async (module, method, args, options) => {
    assert.equal(module, 'ai'); assert.deepEqual(options.permissions, ['ai']); calls.push({method, args});
    if (method === 'open') { const id = 'owner-' + ++sequence; owners.set(id, []); return {id}; }
    if (method === 'close') {
      for (const [id, owner] of sessions) if (owner === args[0]) sessions.delete(id);
      return owners.delete(args[0]);
    }
    if (method === 'ack') return true;
    if (method === 'cancel') { sessions.delete(args[1]); return true; }
    assert(owners.has(args[0]), 'live transport owner');
    if (method === 'poll') {
      const queue = owners.get(args[0]); return queue.splice(0, Math.min(400, queue.length));
    }
    assert.equal(method, 'start');
    const [owner, name, input] = args, id = 'task-' + ++sequence;
    if (hook && await hook({owner, id, name, input, send, sessions, calls}) === false) return {id};
    if (name === 'session') {
      sessions.set(id, owner); send(owner, id, 'resolve', {id, provider: owner, model: 'fixture', target: 'local:fixture'});
    } else if (name === 'session.close') {
      send(owner, id, 'resolve', sessions.delete(input[0]));
    } else if (name.endsWith('stream')) {
      send(owner, id, 'open', {provider: 'fixture'}); send(owner, id, 'chunk', {text: response.text, reasoning: response.reasoning});
      send(owner, id, 'usage', response.usage); send(owner, id, 'resolve', response);
    } else if (name.endsWith('chat')) send(owner, id, 'resolve', response);
    else if (name.endsWith('ask')) send(owner, id, 'resolve', response.text);
    else send(owner, id, 'resolve', name === 'catalog' ? {defaultTarget: 'local:fixture', targets: [{id: 'local:fixture'}]} : true);
    return {id};
  };
  return {ai: factory(call), call, owners, sessions, calls, send, response};
}

test('AI callable, chat, catalog and explicit access helpers retain typed results and copy inputs', async () => {
  const f = fixture(), input = {messages: [{role: 'user', content: 'before'}], temperature: 0.1};
  const response = f.ai.chat(input, {temperature: 0.8}); input.messages[0].content = 'after';
  assert.equal((await response).reasoning, 'reason');
  assert.equal(f.calls.find(x => x.method === 'start').args[2][0].messages[0].content, 'before');
  assert.equal(await f.ai('hello'), f.response.text); assert.equal(f.ai.ask, f.ai);
  assert.equal((await f.ai.catalog()).defaultTarget, 'local:fixture');
  assert(await f.ai.hasAccess()); assert(await f.ai.requestAccess()); await f.ai.close(); assert.equal(f.owners.size, 0);
});

test('stream startup events, supplementary Unicode fragments, usage and completion preserve order', async () => {
  const f = fixture(), events = [], stream = f.ai.stream('hello'); let delta = '', once = 0;
  stream.on('open', () => events.push('open')).on('chunk', () => events.push('chunk'));
  stream.once('delta', text => { delta += text; once++; events.push('delta'); });
  stream.on('usage', () => events.push('usage')).on('done', () => events.push('done'));
  assert.equal(await stream.ready, stream); assert.equal((await stream.result).text, f.response.text);
  assert.equal(delta, f.response.text); assert.equal(once, 1); assert.equal(stream.state, 'done'); assert(stream.isDone);
  assert.deepEqual(events, ['open', 'chunk', 'delta', 'usage', 'done']); stream.cancel(); assert.equal(stream.state, 'done');
  await f.ai.close();
});

test('session methods retain owner identity and close is idempotent across facade generations', async () => {
  const f = fixture(), session = await f.ai.session({system: 'short'});
  assert.equal(session.model, 'fixture'); assert.equal(session.state, 'ready');
  assert.equal(await session.ask('one'), f.response.text); assert.equal((await session.chat('two')).text, f.response.text);
  assert.equal((await session.stream('three').result).text, f.response.text);
  await session.close(); await session.close(); assert(session.isClosed); assert.equal(f.sessions.size, 0);
  await assert.rejects(session.ask('closed'), {code: 'SESSION_CLOSED'});
  const old = await f.ai.session(); await f.ai.close(); assert(old.isClosed);
  const fresh = await f.ai.session(); assert(!fresh.isClosed); await fresh.close(); await f.ai.close();
});

test('invalid JSON, non-finite options and pre-aborted calls never reach the host', async () => {
  const f = fixture(); const cyclic = {}; cyclic.self = cyclic;
  for (const value of [cyclic, {temperature: NaN}, {maxTokens: Infinity}, {callback() {}}, {value: 1n}])
    await assert.rejects(f.ai.ask('x', value), {code: 'INVALID_REQUEST'});
  const controller = new AbortController(); controller.abort();
  await assert.rejects(f.ai.ask('x', {}, {signal: controller.signal}), {code: 'CANCELLED'});
  assert.equal(f.calls.length, 0); await f.ai.close();
});

test('abort and deadline cancel only the owned pending operation', async () => {
  const f = fixture(() => false), controller = new AbortController();
  const result = f.ai.ask('wait', {}, {signal: controller.signal});
  while (!f.calls.some(x => x.method === 'start')) await delay(5);
  controller.abort(); await assert.rejects(result, {code: 'CANCELLED'}); await delay(5);
  assert(f.calls.some(x => x.method === 'cancel'));
  await assert.rejects(f.ai.chat('wait', {}, {timeoutMs: 20}), {code: 'TIMEOUT'}); await f.ai.close();
});

test('late native session replies after cancellation are released', async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const f = fixture(async ({owner, id, name, sessions}) => {
    if (name === 'session') { await gate; sessions.set(id, owner); return false; }
  });
  const controller = new AbortController(), result = f.ai.session({}, {signal: controller.signal});
  while (!f.calls.some(x => x.method === 'start')) await delay(5);
  controller.abort(); await assert.rejects(result, {code: 'CANCELLED'}); release(); await delay(30);
  assert.equal(f.sessions.size, 0); await f.ai.close();
});

test('provider failures preserve error codes and settle both stream promises', async () => {
  const f = fixture(({owner, id, send}) => { send(owner, id, 'reject', {code: 'TARGET_UNAVAILABLE', message: 'unavailable'}); return false; });
  await assert.rejects(f.ai.ask('x'), {name: 'AiError', code: 'TARGET_UNAVAILABLE', route: 'plugin'});
  const stream = f.ai.stream('x'); let error; stream.once('error', value => { error = value; });
  await assert.rejects(stream.ready, {code: 'TARGET_UNAVAILABLE'}); await assert.rejects(stream.result, {code: 'TARGET_UNAVAILABLE'});
  assert(error instanceof f.ai.AiError); assert.equal(stream.state, 'error'); await f.ai.close();
});

test('stream cancellation and listener failures release native work and suppress later packets', async () => {
  const f = fixture(), stream = f.ai.stream('x'); let count = 0;
  stream.on('chunk', () => { count++; throw new Error('listener failed'); });
  await assert.rejects(stream.result, /listener failed/); await delay(10); assert.equal(count, 1);
  assert(f.calls.some(x => x.method === 'cancel')); await f.ai.close();
});
