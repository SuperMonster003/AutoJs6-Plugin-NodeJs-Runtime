const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
const factory = new Function(source.slice(source.indexOf('  // Shared TTS facade.'), source.indexOf('  // End shared TTS facade.')) + ';return __autojs6_create_tts;')();
function fixture(hook) {
  let next = 0;
  const sessions = new Map(), calls = [];
  const native = async (_, method, args) => {
    calls.push({method, args});
    if (hook) { const value = await hook(method, args); if (value !== undefined) return value; }
    if (method === 'open') { const id = String(++next); sessions.set(id, []); return {id}; }
    if (method === 'close') return sessions.delete(args[0]);
    const events = sessions.get(args[0]); assert(events, 'unknown session');
    if (method === 'drain') { await new Promise(resolve => setTimeout(resolve, 5)); return events.splice(0); }
    if (method === 'stopTask') { events.push({id: args[1], event: 'stop', state: 'stopped', value: {code: 'ABORTED', message: 'Stopped', info: {reason: 'user'}}, terminal: true}); return true; }
    if (method === 'invoke') {
      const name = args[1], input = args[2];
      if (['utterance', 'enqueueUtterance', 'synthesis', 'silenceTask'].includes(name)) {
        const id = 'task-' + ++next;
        events.push({id, event: 'start', state: 'running', value: {id}});
        if (input[0] !== 'pending') {
          events.push({id, event: 'audio', state: 'running', value: {id, data: 'AAEC'}});
          events.push({id, event: 'done', state: 'done', value: {id, status: 'done', path: input[1]}, terminal: true});
        }
        return {id, operation: name === 'synthesis' ? 'file' : 'speech', text: input[0], state: 'queued'};
      }
      return input;
    }
    throw Error(method);
  };
  return {tts: factory(native), sessions, calls, native};
}
test('observable synthesis delivers typed audio and resolves after ordered events', async () => {
  const f = fixture(), task = await f.tts.synthesis('hello', 'out.wav'), events = [];
  task.on('start', () => events.push('start')).once('audio', value => { assert(value.data instanceof Uint8Array); assert.deepEqual([...value.data], [0, 1, 2]); events.push('audio'); }).on('done', () => { assert(task.isDone); events.push('done'); });
  assert.equal((await task.result).path, 'out.wav'); assert.deepEqual(events, ['start', 'audio', 'done']);
  assert.equal(task.state, 'done'); assert(task.isDone); assert.equal(await task.stop(), task);
  await f.tts.close(); assert.equal(f.sessions.size, 0);
});
test('query aliases, queue constants and option validation preserve the host contract', async () => {
  const f = fixture();
  assert.equal(f.tts.QUEUE_FLUSH, 'flush'); assert.equal(f.tts.QUEUE_ADD, 'add'); assert.equal(f.tts.getMaxInputLength(), 3999);
  assert.equal(f.tts.speakTask, f.tts.utterance); assert.equal(f.tts.synthesisTask, f.tts.synthesis); assert.equal(f.tts.synthesizeToFile, f.tts.synthesize);
  assert.deepEqual(await f.tts.ready({engine: 'engine'}), ['engine']);
  assert.deepEqual(await f.tts.voices({lang: 'en-US', offline: true}), [{locale: 'en-US', offlineOnly: true, installedOnly: undefined}]);
  assert.throws(() => f.tts.configure({rate: NaN}), /finite JSON/);
  await assert.rejects(f.tts.silence(-1), /non-negative integer/);
  await f.tts.close();
});
test('task cancellation rejects its result while keeping the owning client usable', async () => {
  const f = fixture(), abort = new AbortController(), task = await f.tts.utterance('pending', {}, {signal: abort.signal});
  const stopped = new Promise(resolve => task.once('stop', resolve));
  abort.abort(); await assert.rejects(task.result, {code: 'ERR_AUTOJS6_TTS_ABORTED'});
  assert.equal((await stopped).reason, 'user'); assert.equal((await f.tts('hello')).status, 'done'); await f.tts.close();
});
test('closing a client rejects pending tasks and creates a fresh session on reuse', async () => {
  const f = fixture(), task = await f.tts.utterance('pending');
  await f.tts.close(); await assert.rejects(task.result, {code: 'ERR_AUTOJS6_TTS_ABORTED'});
  assert.equal((await f.tts.speak('hello')).status, 'done'); assert.equal(f.sessions.size, 1); await f.tts.close();
});
test('a close racing initialization releases the late native client', async () => {
  let release; const calls = [];
  const tts = factory(async (_, method, args) => { calls.push({method, args}); if (method === 'open') return new Promise(resolve => release = resolve); return true; });
  const pending = assert.rejects(tts.ready(), /closed during initialization/), closed = tts.close(); release({id: 'late'});
  await pending; await closed; assert(calls.some(x => x.method === 'close' && x.args[0] === 'late'));
});

test('closing from a done listener preserves the already completed result', async () => {
  const f = fixture(), task = await f.tts.synthesis('hello', 'out.wav');
  task.once('done', () => { assert(task.isDone); void f.tts.close(); });
  assert.equal((await task.result).status, 'done');
  await f.tts.close();
});
