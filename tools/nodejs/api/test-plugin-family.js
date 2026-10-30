const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), {EventEmitter} = require('node:events');
const source = fs.readFileSync(path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
const body = source.slice(source.indexOf('  // Shared plugin family facades:'), source.indexOf('  // End shared plugin family facades.'));
const create = new Function(body + ';return __autojs6_create_plugin_family;')();

// The host's operation clients: every start goes to `answer`, events drain through poll in fragments.
function host(answer) {
  const calls = [], clients = new Map(); let next = 0;
  const send = (client, id, type, value) => {
    const bytes = Buffer.from(JSON.stringify({type, value}));
    for (let i = 0; i < bytes.length; i += 29)
      clients.get(client).push({id, data: bytes.subarray(i, i + 29).toString('base64'), last: i + 29 >= bytes.length});
  };
  const call = async (module, method, args, options) => {
    calls.push({module, method, args, options});
    if (method === 'open') { const id = module + '-' + ++next; clients.set(id, []); return {id}; }
    if (method === 'close') return clients.delete(args[0]);
    const queue = clients.get(args[0]);
    assert(queue, 'client ownership');
    if (method === 'poll') {
      const found = [];
      for (let i = 0; i < queue.length && found.length < 3;) if (args[1].includes(queue[i].id)) found.push(...queue.splice(i, 1)); else i++;
      return found;
    }
    if (method === 'cancel') return true;
    assert.equal(method, 'start');
    const id = 'operation-' + ++next, emit = (type, value) => send(args[0], id, type, value);
    const outcome = await answer(args[1], args[2], emit, id);
    if (outcome !== undefined) emit(...outcome);
    return {id};
  };
  return {call, calls};
}
const family = answer => { const fake = host(answer); return {...create(fake.call, EventEmitter), fake}; };
const started = (fake, name) => fake.calls.filter(x => x.method === 'start' && x.args[1] === name).map(x => x.args[2]);
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

test('GalleryError and BatchRenameError follow the Rhino classes', () => {
  const {gallery, batchRename} = family(() => {});
  for (const [Coded, name] of [[gallery.GalleryError, 'GalleryError'], [batchRename.BatchRenameError, 'BatchRenameError']]) {
    const error = Coded('broken'), named = new Coded('broken', 'IO');
    assert(error instanceof Coded && error instanceof Error);
    assert.equal(error.code, 'INTERNAL');
    assert.equal(error.name, name);
    assert.equal(String(named), name + ' [IO]: broken');
    assert.deepEqual(Object.keys(named), ['code']);
    assert.equal(new Coded().message, '');
  }
});

test('gallery calls keep their JSON arguments, strip the transport prefix and reject bad counts', async () => {
  const {gallery, fake} = family(name => {
    if (name === 'pick') return ['resolve', [{path: '/a.png'}]];
    if (name === 'convert') return ['reject', {code: 'FILE_NOT_FOUND', message: 'no file'}];
    if (name === 'edit') return ['resolve', null];
    return ['resolve', true];
  });
  assert.deepEqual(await gallery.pick({multiple: true, ignored: () => 1}), [{path: '/a.png'}]);
  assert.deepEqual(started(fake, 'pick'), [[{multiple: true}]]);
  assert.equal(await gallery.editAsync('a.png'), null);
  assert.deepEqual(started(fake, 'edit'), [['a.png', null]]);
  assert.equal(await gallery.isAvailableAsync(), true);
  await assert.rejects(gallery.convert('a.png', {format: 'webp'}), error =>
    error instanceof gallery.GalleryError && error.code === 'FILE_NOT_FOUND' && error.message === 'no file');
  await assert.rejects(gallery.view(), error => error.code === 'INVALID_ARGUMENT' && /range \[1\.\.2\]/.test(error.message));
  await assert.rejects(gallery.isAvailable({}, 1), error => error.code === 'INVALID_ARGUMENT' && error.message === 'Arguments must be empty');
  assert.equal(fake.calls.filter(x => x.method === 'open').length, 1, 'one client per module');
  assert(fake.calls.every(x => x.options.permissions[0] === 'gallery' && x.options.timeoutMs === 10000));
});

test('host refusals keep their capability and code', async () => {
  const denied = Object.assign(new Error('denied'), {code: 'NOT_GRANTED', capability: 'gallery.control', missingCapabilities: ['gallery.control']});
  const {gallery} = create(() => Promise.reject(denied), EventEmitter);
  await assert.rejects(gallery.pick(), error => error instanceof gallery.GalleryError && error.code === 'NOT_GRANTED' &&
    error.capability === 'gallery.control' && error.missingCapabilities[0] === 'gallery.control');
  const prefixed = Object.assign(new Error('late'), {code: 'ERR_AUTOJS6_GALLERY_SESSION_CLOSED'});
  const again = create(() => Promise.reject(prefixed), EventEmitter).gallery;
  await assert.rejects(again.pick(), error => error.code === 'SESSION_CLOSED');
});

test('a viewer session replays its early events to the listeners added after the await', async () => {
  let viewer;
  const {gallery} = family((name, args, emit, id) => {
    viewer = emit;
    emit('event', {type: 'open', count: 2});
    emit('event', {type: 'page', index: 0, path: '/a.png'});
    return ['resolve', {id, paths: ['/a.png', '/b.png']}];
  });
  const events = [];
  const session = await gallery.view(['a.png', 'b.png'], {index: 0});
  assert.deepEqual(session.paths, ['/a.png', '/b.png']);
  assert.equal(session.count, 2); assert.equal(session.state, 'open'); assert(session.isOpen);
  assert.equal(session.index, 0); assert.equal(session.path, '/a.png');
  assert.equal(String(session), 'GalleryViewSession { count: 2, state: open, index: 0 }');
  session.on('open', value => events.push(['open', value.count])).on('page', value => events.push(['page', value.index]));
  await delay(5);
  assert.deepEqual(events, [['open', 2], ['page', 0]]);
  // The viewer keeps the execution alive until its close event.
  viewer('event', {type: 'close', reason: 'user'});
  await new Promise(resolve => session.once('close', resolve));
  assert.equal(session.reason, 'user');
});

test('viewer events after the answer, an unlistened error and the terminal close', async () => {
  const emitters = {};
  const {gallery} = family((name, args, emit, id) => {
    if (name === 'view' || name === 'viewAsync') { emitters.view = emit; return ['resolve', {id, paths: ['/a.png']}]; }
    assert.equal(name, 'view.close');
    assert.deepEqual(args[1], {finish: false});
    emitters.view('event', {type: 'close', reason: 'host'});
    return ['resolve', null];
  });
  const session = await gallery.viewAsync('a.png');
  const seen = [];
  session.on('page', value => seen.push(value)).on('close', value => seen.push(value));
  emitters.view('event', {type: 'open', count: 1});
  emitters.view('event', {type: 'error', code: 'IO', message: 'ignored without a listener'});
  emitters.view('event', {type: 'page', index: 0, path: '/a.png'});
  await delay(80);
  assert.equal(session.index, 0);
  assert.equal(await session.close({finish: false}), session);
  await delay(5);
  assert.deepEqual(seen, [{index: 0, path: '/a.png'}, {reason: 'host'}]);
  assert(session.isClosed); assert.equal(session.reason, 'host');
  assert.equal(String(session), 'GalleryViewSession { count: 1, state: closed, index: 0, reason: host }');
  assert.equal(await session.close(), session, 'idempotent');
});

test('a signal cancels a waiting picker and ends an active viewer; a timeout is TIMEOUT', async () => {
  const {gallery, fake} = family((name, args, emit, id) => name === 'view' ? ['resolve', {id, paths: ['/a.png']}] : undefined);
  const controller = new AbortController();
  const pending = gallery.pick(null, {signal: controller.signal});
  await delay(5);
  controller.abort();
  await assert.rejects(pending, error => error.code === 'CANCELLED');
  await assert.rejects(gallery.pick(null, {timeoutMs: 5}), error => error.code === 'TIMEOUT');
  await assert.rejects(gallery.pick(null, 7), error => error.code === 'INVALID_ARGUMENT');
  const active = new AbortController(), session = await gallery.view('a.png', null, {signal: active.signal});
  const closes = [];
  await delay(5);
  session.on('close', value => closes.push(value.reason));
  active.abort();
  assert.deepEqual(closes, ['cancelled']);
  assert.equal(session.reason, 'cancelled');
  await delay(5);
  assert(fake.calls.some(x => x.method === 'cancel'));
});

test('a failed poll ends pending calls and viewers', async () => {
  let failing = false;
  const fake = host((name, args, emit, id) => name === 'view' ? ['resolve', {id, paths: ['/a.png']}] : undefined);
  const call = (module, method, args, options) => failing && method === 'poll' ? Promise.reject(new Error('bridge gone')) : fake.call(module, method, args, options);
  const {gallery} = create(call, EventEmitter);
  const session = await gallery.view('a.png');
  const seen = [];
  session.on('error', error => seen.push(error.message)).on('close', value => seen.push(value.reason));
  await delay(5);
  const pending = gallery.pick();
  await delay(5);
  failing = true;
  await assert.rejects(pending, error => error.message === 'bridge gone');
  assert.deepEqual(seen, ['bridge gone', 'error']);
  assert(session.isClosed);
});

test('batchRename plans, applies with progress and passes RegExp searches', async () => {
  const {batchRename, fake} = family((name, args, emit) => {
    if (name === 'plan') return ['resolve', {id: 'plan-1', items: [{excluded: false}, {excluded: true}]}];
    if (name === 'preview') return ['resolve', {newName: 'b'}];
    if (name === 'apply') { emit('progress', [1, 2, {name: 'a'}]); emit('progress', [2, 2, {name: 'b'}]); return ['resolve', {batchId: 'batch'}]; }
    return ['resolve', true];
  });
  const progress = [];
  const result = await batchRename('./a', {search: /a+/gi, replace: 'b', onProgress: (done, total, item) => progress.push([done, total, item.name])});
  assert.deepEqual(result, {batchId: 'batch'});
  assert.deepEqual(progress, [[1, 2, 'a'], [2, 2, 'b']]);
  const [paths, options, regexp] = started(fake, 'plan')[0];
  assert.equal(paths, './a'); assert.deepEqual(options, {search: {}, replace: 'b'}); assert.deepEqual(regexp, {source: 'a+', flags: 'gi'});
  assert.deepEqual(started(fake, 'apply')[0], ['plan-1', [false, true], null, true]);
  await batchRename.previewAsync('a.txt', {search: 'a'});
  assert.deepEqual(started(fake, 'preview')[0], ['a.txt', {search: 'a'}, null]);
  await batchRename.apply({id: 'plan-2', items: [{}, null]}, {onConflict: 'skip'});
  assert.deepEqual(started(fake, 'apply')[1], ['plan-2', [false, false], {onConflict: 'skip'}, false]);
  await batchRename.undoAsync('batch');
  await batchRename.history();
  await batchRename.importPresets({presets: []}, {onConflict: 'skip'});
  await batchRename.deleteHistory('batch');
  await batchRename.clearHistory({source: 'script'});
  assert.deepEqual(started(fake, 'undoAsync').concat(started(fake, 'undo'))[0], ['batch']);
  assert.deepEqual(started(fake, 'history')[0], [null]);
  assert.deepEqual(started(fake, 'importPresets')[0], ['{"presets":[]}', {onConflict: 'skip'}]);
  assert.deepEqual(started(fake, 'deleteHistory')[0], ['batch']);
  assert.deepEqual(started(fake, 'clearHistory')[0], [{source: 'script'}]);
  assert(fake.calls.every(x => x.module === 'batch_rename' && x.options.permissions[0] === 'batch_rename'));
});

test('batchRename reports argument errors with the codes and text of Rhino', async () => {
  const {batchRename} = family(() => ['resolve', true]);
  const refused = text => error => error instanceof batchRename.BatchRenameError && error.code === 'INVALID_OPTIONS' && error.message.startsWith(text);
  await assert.rejects(batchRename.apply(1), refused('apply needs a plan returned by plan(), got 1 (Number)'));
  await assert.rejects(batchRename.apply({}), refused('apply needs a plan returned by plan(): the plan has no id'));
  await assert.rejects(batchRename.apply({id: 'x'}), refused('apply needs a plan returned by plan(): the plan has no items'));
  await assert.rejects(batchRename.apply({id: 'x', items: []}, {onProgress: 1}), refused('onProgress must be a function, got 1'));
  await assert.rejects(batchRename.importPresets(1), refused('importPresets needs a JSON string or an object of exported presets, got 1'));
  await assert.rejects(batchRename('a'), refused('Method only accepts 2 arguments'));
  await assert.rejects(batchRename.undo(), refused('Method only accepts 1 argument'));
  await assert.rejects(batchRename.presets(1, 2), refused('Arguments must be empty'));
  const cycle = {}; cycle.self = cycle;
  await assert.rejects(batchRename.plan('a', cycle), refused('Converting circular structure'));
  assert.deepEqual(Object.keys(batchRename).filter(key => key.endsWith('Async')).sort(),
    ['applyAsync', 'importPresetsAsync', 'planAsync', 'presetsAsync', 'previewAsync', 'undoAsync']);
  assert(Object.isFrozen(batchRename));
});

test('a throwing onProgress does not stop the batch and rejects afterwards', async () => {
  const {batchRename} = family((name, args, emit) => {
    for (const done of [1, 2]) emit('progress', [done, 2, {}]);
    return args[0] === 'plan' ? ['resolve', {counts: {renamed: 2}}] : ['reject', {code: 'IO_FAILED', message: 'later'}];
  });
  let calls = 0;
  const boom = new TypeError('boom');
  await assert.rejects(batchRename.apply({id: 'plan', items: []}, {onProgress() { calls++; throw boom; }}), error => error === boom);
  assert.equal(calls, 1);
  await assert.rejects(batchRename.apply({id: 'other', items: []}, {onProgress() { throw boom; }}), error => error === boom, 'the callback error wins');
  await assert.rejects(batchRename.apply({id: 'other', items: []}), error => error.code === 'IO_FAILED');
});

test('colors.theme checks arguments like Rhino and answers through the host', async () => {
  const core = new Function(source.slice(source.indexOf('  // Shared JS tools core.'), source.indexOf('  // End shared JS tools core.')) + ';return __autojs6_create_js_tools;')();
  const calls = [], answers = {get: -16711936, activate: {}, state: {}, deactivate: false, next: true, libraries: [{id: 1}], current: {color: 1}};
  const {colors} = core({device: {}, log() {}, warn() {}, java() {}, fs: {}, path: {}, cwd: () => '/', sdcardPath: '/sdcard',
    themeCall: async (method, args) => { calls.push([method, args]); return answers[method] ?? null; }});
  const {theme} = colors;
  assert.deepEqual(Object.keys(theme), ['get', 'set', 'reset', 'libraries', 'activate', 'deactivate', 'next', 'state', 'current']);
  assert(Object.isFrozen(theme));
  assert.equal(await theme.get(), -16711936);
  assert.equal(await theme.set('#2196F3', {accent: '#FF9800'}), undefined);
  assert.deepEqual(calls.at(-1), ['set', [0xff2196f3 | 0, 0xffff9800 | 0, null]]);
  await theme.set(0x112233);
  assert.deepEqual(calls.at(-1), ['set', [0xff112233 | 0, null, null]]);
  assert.equal(await theme.reset(), undefined);
  assert.deepEqual(await theme.libraries(), [{id: 1}]);
  assert.equal(await theme.activate(5, {group: 2}), undefined);
  assert.deepEqual(calls.at(-1), ['activate', [5, 2]]);
  assert.equal(await theme.deactivate(), false);
  assert.equal(await theme.next(), true);
  assert.equal(await theme.state('busy'), undefined);
  assert.deepEqual(await theme.current(), {color: 1});
  const failed = (text, Type) => error => error instanceof Type && error.message.includes(text);
  await assert.rejects(theme.set('#2196F3', 1), failed('Argument "options" must be of type Object', TypeError));
  await assert.rejects(theme.set(null), failed('colors.theme.set', TypeError));
  await assert.rejects(theme.activate(1.5), failed('Argument "libraryId" must be of type integer, got 1.5', RangeError));
  await assert.rejects(theme.state(null), failed('Argument "name" cannot be null', TypeError));
  await assert.rejects(theme.get(1), failed('Method only accepts 0 arguments', TypeError));
  await assert.rejects(theme.set(), failed('in the range [1..2]', TypeError));
  answers.activate = {library: false};
  await assert.rejects(theme.activate(9), failed('id of an intelligent color library', RangeError));
  answers.activate = {group: false, name: 'Library'};
  await assert.rejects(theme.activate(9, {group: 3}), failed('id of a usable group of Library, got 3', RangeError));
  answers.state = {error: 'No such state'};
  await assert.rejects(theme.state('busy'), failed('No such state', RangeError));
});

test('speech analysis and speaker members run as operations', () => {
  const speech = source.slice(source.indexOf('  // Shared speech facade.'), source.indexOf('  // End shared speech facade.'));
  for (const name of ['diarize', 'diarizeAsync', 'detectLanguage', 'detectLanguageAsync', 'tag', 'tagAsync',
    'speaker.enroll', 'speaker.enrollAsync', 'speaker.verify', 'speaker.verifyAsync', 'speaker.identify', 'speaker.identifyAsync', 'speaker.list', 'speaker.remove'])
    assert(speech.includes(`start("${name}", [`), name);
});

test('the Worker table and the module tables list the facades', () => {
  assert(source.includes('gallery:Object.keys(__autojs6_limited_gallery())'));
  assert(source.includes('if(pluginFamily && methods.gallery) modules.set("gallery",pluginFamily.gallery);'));
  assert(source.includes('__autojs6_create_plugin_family.toString() + ");";'));
  for (const module of ['gallery', 'batch_rename']) assert(source.includes(`message.module==="${module}" && message.method==="open"`));
});
