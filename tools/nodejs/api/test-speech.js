const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
const factory = new Function(source.slice(source.indexOf('  // Shared speech facade.'), source.indexOf('  // End shared speech facade.')) + ';return __autojs6_create_speech;')();
function fixture(hook) {
  let next = 0;
  const clients = new Map(), uploads = new Map(), calls = [], jobs = new Map();
  const send = (client, id, type, value, round) => {
    const bytes = Buffer.from(JSON.stringify({type, value, round}));
    for (let offset = 0; offset < bytes.length; offset += 29) clients.get(client).push({id, data: bytes.subarray(offset, offset + 29).toString('base64'), last: offset + 29 >= bytes.length});
  };
  const native = async (_, method, args) => {
    calls.push({method, args});
    if (method === 'open') { const id = 'client-' + ++next; clients.set(id, []); return {id}; }
    if (method === 'close') return clients.delete(args[0]);
    assert(clients.has(args[0]), 'closed client');
    if (method === 'poll') {
      const queued = clients.get(args[0]), found = [];
      for (let i = 0; i < queued.length && found.length < 24;) {
        if (args[1].includes(queued[i].id)) found.push(...queued.splice(i, 1)); else i++;
      }
      return found;
    }
    if (method === 'uploadBegin') { const id = 'upload-' + ++next; uploads.set(id, {size: args[1], bytes: Buffer.alloc(0)}); return {id}; }
    if (method === 'uploadChunk') { const upload = uploads.get(args[1]); assert.equal(args[2], upload.bytes.length); upload.bytes = Buffer.concat([upload.bytes, Buffer.from(args[3], 'base64')]); return true; }
    if (method === 'uploadDiscard') return uploads.delete(args[1]);
    if (method === 'cancel') { send(args[0], args[1], 'reject', {code: 'SESSION_CANCELLED', message: 'cancelled'}); return true; }
    assert.equal(method, 'start');
    const [client, name, input] = args, id = 'op-' + ++next;
    jobs.set(id, {client, name, input});
    if (hook && await hook({client, name, input, id, send, uploads}) === false) return {id, kind: 'operation'};
    if (name === 'listen' || name === 'wake') {
      send(client, id, 'ready', {sessionId: 'plugin-' + id, modelId: 'model'});
      return {id, kind: name, source: input[0]?.source || 'mic', keywords: name === 'wake' ? [input[0]] : null, startedAt: Date.now()};
    }
    if (name === 'session.stop' || name === 'session.cancel') send(client, input[0], 'end', {reason: name.endsWith('stop') ? 'stopped' : 'cancelled'});
    send(client, id, 'resolve', input);
    return {id, kind: 'operation'};
  };
  return {speech: factory(native), clients, uploads, calls, jobs, send};
}
const waitEvent = (handle, name) => new Promise((resolve, reject) => { handle.once(name, resolve); handle.once('error', reject); });

test('PCM views preserve offsets and chunked recognition releases uploaded data', async () => {
  const f = fixture(async ({client, name, input, id, send, uploads}) => {
    if (name !== 'recognize') return;
    const upload = uploads.get(input[0].upload); assert.equal(upload.bytes.length, 40000);
    assert(upload.bytes.every(x => x === 7)); send(client, id, 'resolve', {text: 'recognized'}); return false;
  });
  const buffer = Buffer.alloc(40004, 7); buffer[0] = 99; buffer[40003] = 99;
  assert.equal((await f.speech.recognize(buffer.subarray(2, -2))).text, 'recognized');
  assert.equal(f.uploads.size, 0); assert(f.calls.filter(x => x.method === 'uploadChunk').length > 1);
  await assert.rejects(f.speech.recognize(new Uint8Array(3)), {code: 'INVALID_OPTIONS'});
  await assert.rejects(f.speech.recognize([0, 0]), {code: 'INVALID_OPTIONS'});
  await f.speech.close();
});

test('model results reassemble Unicode fragments and progress callbacks stay local', async () => {
  const f = fixture(({client, name, input, id, send}) => {
    if (name !== 'models.install') return;
    assert.equal(input[1].onProgress, undefined); assert.equal(input[1].acceptLicense, false);
    send(client, id, 'progress', [1, 2]); send(client, id, 'progress', [2, 2]);
    send(client, id, 'resolve', {id: input[0], name: '\u6a21\u578b \ud83e\udd9c'.repeat(3000)}); return false;
  });
  const progress = [], result = await f.speech.models.install('owned-model', {acceptLicense: false, onProgress: (done, total) => progress.push([done, total])});
  assert.deepEqual(progress, [[1, 2], [2, 2]]); assert.equal(result.name, '\u6a21\u578b \ud83e\udd9c'.repeat(3000));
  assert.throws(() => f.speech.models.install('model', {onProgress: true}), {code: 'INVALID_OPTIONS'});
  await f.speech.close();
});

test('session event state, ordered feed/stop and local text match the speech contract', async () => {
  const f = fixture(({client, name, input, send}) => {
    if (name === 'session.feed') {
      send(client, input[0], 'final', {text: 'hello', segmentIndex: 0});
      send(client, input[0], 'final', {text: 'world', segmentIndex: 1});
    }
  });
  const session = await f.speech.listen({source: 'external'}), events = [];
  session.on('ready', () => { assert.equal(session.state, 'active'); events.push('ready'); });
  session.on('final', () => events.push('final'));
  const ended = waitEvent(session, 'end');
  const fed = session.feed(new Int16Array(32000)), stop = session.stop();
  assert.equal(await fed, session); assert.equal(await stop, session);
  assert.equal((await ended).text, 'hello world'); assert.equal(session.result(), 'hello world');
  assert(session.isEnded); assert.equal(session.reason, 'stopped'); assert.equal(await session.stop(), session);
  assert.deepEqual(events, ['ready', 'final', 'final']); assert.equal(f.uploads.size, 0);
  await f.speech.close();
});

test('early wake round events wait until the wake listener has attached its round listeners', async () => {
  const f = fixture(), wake = await f.speech.wake('hello', {thenListen: true}), events = [];
  const entry = [...f.jobs].find(([, value]) => value.name === 'wake'), [id, {client}] = entry;
  f.send(client, id, 'ready', {sessionId: 'round-0', modelId: 'asr'}, 0);
  f.send(client, id, 'final', {text: 'command', segmentIndex: 0}, 0);
  f.send(client, id, 'wake', {keyword: 'hello', timestampMillis: 10, score: null}, 0);
  f.send(client, id, 'end', {reason: 'stopped', text: 'command'}, 0);
  let round;
  const done = new Promise(resolve => wake.once('wake', event => {
    events.push('wake'); round = event.listen; assert.equal(round.round, 0);
    round.on('ready', () => events.push('ready')).on('final', () => events.push('final')).on('end', resolve);
  }));
  await done; assert.deepEqual(events, ['wake', 'ready', 'final']); assert.equal(round.result(), 'command');
  await wake.stop(); await f.speech.close();
});

test('plugin errors, abort and close settle pending work without leaving the client unusable', async () => {
  const f = fixture(({client, name, id, send}) => {
    if (name === 'recognize') { send(client, id, 'reject', {code: 'MODEL_MISSING', message: 'missing'}); return false; }
    if (name === 'models.install') return false;
  });
  await assert.rejects(f.speech.recognize('missing.wav'), error => error instanceof f.speech.SpeechError && error.code === 'MODEL_MISSING' && error.toString().includes('[MODEL_MISSING]'));
  const controller = new AbortController(), pending = f.speech.models.install('model', {}, {signal: controller.signal});
  setTimeout(() => controller.abort(), 10); await assert.rejects(pending, {code: 'SESSION_CANCELLED'});
  const more = f.speech.models.install('model'); const rejected = assert.rejects(more, {code: 'SESSION_CLOSED'});
  await new Promise(resolve => setTimeout(resolve, 10)); await f.speech.close(); await rejected;
  assert.equal(f.clients.size, 0); await f.speech.capabilities(); await f.speech.close();
});

test('a close during client initialization closes the late native client', async () => {
  let release; const calls = [];
  const speech = factory(async (_, method, args) => { calls.push({method, args}); if (method === 'open') return new Promise(resolve => release = resolve); return true; });
  const result = assert.rejects(speech.capabilities(), {code: 'SESSION_CLOSED'});
  await Promise.resolve(); await Promise.resolve();
  const closed = speech.close(); release({id: 'late'});
  await result; await closed;
  assert(calls.some(x => x.method === 'close' && x.args[0] === 'late'));
});

test('operation deadlines cancel pending work and invalid signals create no native job', async () => {
  const f = fixture(({name}) => name === 'models.install' ? false : undefined);
  await assert.rejects(f.speech.models.install('model', {}, {timeoutMs: 20}), {code: 'TIMEOUT'});
  const count = f.calls.length;
  await assert.rejects(f.speech.capabilities({signal: {}}), {code: 'INVALID_OPTIONS'});
  assert.equal(f.calls.length, count);
  await f.speech.close();
});

test('a result arriving after the caller deadline rejects even if the native operation already finished', async () => {
  const f = fixture(async ({name}) => {
    if (name === 'capabilities') await new Promise(resolve => setTimeout(resolve, 35));
  });
  await assert.rejects(f.speech.capabilities({timeoutMs: 10}), {code: 'TIMEOUT'});
  await f.speech.close();
});
