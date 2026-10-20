const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
const factory = new Function(source.slice(source.indexOf('  // Shared EPUB facade.'), source.indexOf('  // End shared EPUB facade.')) + ';return __autojs6_create_epub;')();
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

function fixture(hook) {
  let next = 0;
  const clients = new Map(), books = new Map(), readers = new Map(), calls = [];
  const send = (client, id, type, value) => {
    const bytes = Buffer.from(JSON.stringify({type, value}));
    for (let i = 0; i < bytes.length; i += 29)
      clients.get(client)?.push({id, data: bytes.subarray(i, i + 29).toString('base64'), last: i + 29 >= bytes.length});
  };
  const transport = async (_, method, args) => {
    calls.push({method, args});
    if (method === 'open') { const id = 'client-' + ++next; clients.set(id, []); return {id}; }
    if (method === 'close') {
      for (const [id, owner] of [...books, ...readers]) if (owner === args[0]) { books.delete(id); readers.delete(id); }
      return clients.delete(args[0]);
    }
    assert(clients.has(args[0]), 'client ownership');
    if (method === 'poll') {
      const queue = clients.get(args[0]), found = [];
      for (let i = 0; i < queue.length && found.length < 48;)
        if (args[1].includes(queue[i].id)) found.push(...queue.splice(i, 1)); else i++;
      return found;
    }
    if (method === 'cancel') { books.delete(args[1]); readers.delete(args[1]); return true; }
    assert.equal(method, 'start');
    const [client, name, input] = args, id = 'operation-' + ++next;
    if (hook && await hook({client, name, input, id, send, books, readers}) === false) return {id};
    let result = true;
    if (name === 'openBook') {
      books.set(id, client);
      result = {id, path: '/books/test.epub', metadata: {title: 'A book'}, toc: [{title: 'One', href: 'one.xhtml'}], readingOrder: [{href: 'one.xhtml'}], positions: 2};
    } else if (name === 'read') {
      readers.set(id, client);
      send(client, id, 'event', {type: 'open', title: 'A book', href: 'one.xhtml', positions: 2, totalProgression: 0});
      result = {id, path: '/books/test.epub'};
    } else if (name === 'book.close') result = books.delete(input[0]);
    else if (name === 'book.text' || name === 'book.textAll') result = 'Chapter \u4e00 \ud83d\udcd6'.repeat(2000);
    else if (name === 'book.cover') result = input[1] ? input[1] : {base64: Buffer.from([137, 80, 78, 71]).toString('base64')};
    else if (name === 'book.resource') result = input[2];
    else if (name === 'book.search') result = [{href: 'one.xhtml', excerpt: 'found'}];
    else if (name === 'book.annotations' || name === 'reader.bookmarks') result = [];
    else if (name === 'reader.goTo') send(client, input[0], 'event', {type: 'progress', href: input[1], totalProgression: 0.5, chapterTitle: 'Two'});
    else if (name === 'reader.close') { send(client, input[0], 'event', {type: 'close', reason: 'host'}); readers.delete(input[0]); }
    send(client, id, 'resolve', result);
    return {id};
  };
  return {epub: factory(transport), clients, books, readers, calls, send};
}

test('all 20 registered root names and book operations dispatch through the shared facade', async () => {
  const f = fixture();
  for (const name of ['open', 'metadata', 'toc', 'readingOrder', 'text', 'cover', 'search', 'annotations', 'read', 'isAvailable'])
    assert.equal(f.epub[name], f.epub[name + 'Async']);
  assert(await f.epub.isAvailable());
  const book = await f.epub.open('test.epub');
  assert.equal(book.metadata.title, 'A book'); assert.equal(book.positions, 2); assert(!book.isClosed);
  assert.equal(await book.textAll(), 'Chapter \u4e00 \ud83d\udcd6'.repeat(2000));
  assert.equal(await book.textAsync(0), 'Chapter \u4e00 \ud83d\udcd6'.repeat(2000));
  assert.deepEqual([...await book.cover()], [137, 80, 78, 71]);
  assert.equal(await book.cover('cover.png'), 'cover.png');
  assert.equal(await book.resource('one.xhtml', 'chapter.xhtml'), 'chapter.xhtml');
  assert.equal((await book.search('found'))[0].excerpt, 'found');
  assert.deepEqual(await book.annotations(), []);
  await book.close(); await book.close(); assert(book.isClosed); assert.equal(f.books.size, 0);
  await assert.rejects(book.text(), {code: 'SESSION_CLOSED'});
  await f.epub.close(); assert.equal(f.clients.size, 0);
});

test('convenience operations close their temporary book on both success and provider errors', async () => {
  const f = fixture(({client, name, id, send}) => {
    if (name === 'book.search') { send(client, id, 'reject', {code: 'PARSE_FAILED', message: 'failed'}); return false; }
  });
  assert.equal((await f.epub.metadata('book.epub')).title, 'A book');
  assert.equal((await f.epub.toc('book.epub'))[0].href, 'one.xhtml');
  assert.equal((await f.epub.readingOrder('book.epub')).length, 1);
  await f.epub.text('book.epub', 0);
  await f.epub.cover('book.epub');
  await f.epub.annotations('book.epub');
  await assert.rejects(f.epub.search('book.epub', 'word'), error => error instanceof f.epub.EpubError && error.code === 'PARSE_FAILED');
  assert.equal(f.books.size, 0);
  await f.epub.close();
});

test('early reader events, local state, async controls and idempotent close retain ordering', async () => {
  const f = fixture(), reader = await f.epub.read('book.epub'), names = [];
  const opened = new Promise(resolve => reader.once('open', event => { names.push('open'); resolve(event); }));
  assert.equal((await opened).title, 'A book'); assert(reader.isOpen); assert.equal(reader.title, 'A book');
  const progress = new Promise(resolve => reader.once('progress', event => { names.push('progress'); resolve(event); }));
  assert.equal(await reader.goTo('two.xhtml'), reader); await progress;
  assert.equal(reader.href, 'two.xhtml'); assert.equal(reader.chapterTitle, 'Two'); assert.equal(reader.progress, 0.5);
  assert.equal(await reader.next(), reader); assert.equal(await reader.prev(), reader);
  assert.equal(await reader.nextChapter(), reader); assert.equal(await reader.prevChapter(), reader);
  assert.equal(await reader.setPreferences({fontSize: 1.2}), reader);
  assert.deepEqual(await reader.bookmarks(), []);
  const closed = new Promise(resolve => reader.once('close', event => { names.push('close'); resolve(event); }));
  assert.equal(await reader.close({finish: true}), reader); assert.equal((await closed).reason, 'host');
  assert(reader.isClosed); assert.equal(await reader.close(), reader);
  assert.deepEqual(names, ['open', 'progress', 'close']);
  await f.epub.close(); assert.equal(f.readers.size, 0);
});

test('abort and deadline settle promptly and discard a late newly opened book', async () => {
  const f = fixture(async ({name, id, client, books}) => {
    if (name === 'openBook') { await delay(35); books.set(id, client); }
  });
  await assert.rejects(f.epub.open('book.epub', {timeoutMs: 10}), {code: 'TIMEOUT'});
  await delay(50); assert.equal(f.books.size, 0);
  const controller = new AbortController();
  const opening = f.epub.open('book.epub', {signal: controller.signal});
  setTimeout(() => controller.abort(), 5);
  await assert.rejects(opening, {code: 'CANCELLED'}); await delay(50);
  assert.equal(f.books.size, 0); await f.epub.close();
});

test('closing during native client initialization releases the late client and invalidates old books', async () => {
  let release; const calls = [];
  const epub = factory(async (_, method, args) => {
    calls.push({method, args});
    if (method === 'open') return new Promise(resolve => { release = resolve; });
    return true;
  });
  const rejected = assert.rejects(epub.open('book.epub'), {code: 'SESSION_CLOSED'});
  await delay(1); const closing = epub.close(); release({id: 'late'});
  await closing; await rejected; assert(calls.some(call => call.method === 'close' && call.args[0] === 'late'));
  const f = fixture(), book = await f.epub.open('book.epub'); await f.epub.close();
  assert(book.isClosed); await assert.rejects(book.text(), {code: 'SESSION_CLOSED'});
  assert(await f.epub.isAvailable()); await f.epub.close();
});

test('invalid JSON, call deadlines and signals never issue a native request', async () => {
  const f = fixture();
  const cyclic = {}; cyclic.self = cyclic;
  for (const value of [NaN, Infinity, 1n, () => {}, Symbol('bad'), cyclic])
    await assert.rejects(f.epub.open({path: 'book.epub', value}), {code: 'INVALID_ARGUMENT'});
  for (const options of [{timeoutMs: 0}, {timeoutMs: NaN}, {signal: {}}, 1])
    await assert.rejects(f.epub.open('book.epub', options), {code: 'INVALID_ARGUMENT'});
  assert.equal(f.calls.length, 0);
  await f.epub.close();
});

test('a reader AbortSignal closes only its own session', async () => {
  const f = fixture(), controller = new AbortController();
  const reader = await f.epub.read('book.epub', {}, {signal: controller.signal});
  const book = await f.epub.open('book.epub');
  const closed = new Promise(resolve => reader.once('close', resolve));
  controller.abort(); assert.equal((await closed).reason, 'cancelled');
  assert(reader.isClosed); assert(!book.isClosed); assert.equal(f.books.size, 1);
  await book.close(); await f.epub.close();
});

test('large positive deadlines do not overflow the JavaScript timer', async () => {
  const f = fixture(async ({name}) => { if (name === 'isAvailable') await delay(20); });
  assert.equal(await f.epub.isAvailable({timeoutMs: 10_000_000_000}), true);
  await f.epub.close();
});

test('an already elapsed deadline detaches cancellation and creates no client', async () => {
  const f = fixture(), controller = new AbortController();
  await assert.rejects(f.epub.open('book.epub', {timeoutMs: Number.MIN_VALUE, signal: controller.signal}), {code: 'TIMEOUT'});
  assert.equal(require('node:events').getEventListeners(controller.signal, 'abort').length, 0);
  assert.equal(f.calls.length, 0);
  await f.epub.close();
});
