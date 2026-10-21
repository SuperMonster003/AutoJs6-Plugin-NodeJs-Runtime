const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
const factory = new Function(source.slice(source.indexOf('  // Shared mail facade.'), source.indexOf('  // End shared mail facade.')) + ';return __autojs6_create_mail;')();
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const options = {address: 'a@example.invalid', password: 'synthetic-password'};

function fixture(hook) {
  let sequence = 0;
  const owners = new Map(), accounts = new Map(), watches = new Map(), tokens = new Map(), calls = [];
  const send = (owner, id, type, value) => {
    const bytes = Buffer.from(JSON.stringify({type, value}));
    for (let i = 0; i < bytes.length; i += 19) owners.get(owner)?.push({
      id, data: bytes.subarray(i, i + 19).toString('base64'), last: i + 19 >= bytes.length,
    });
  };
  const connect = (owner, id, account) => {
    const snapshot = typeof account === 'string' ? {alias: account} : {...account};
    delete snapshot.password; delete snapshot.accessToken;
    accounts.set(id, owner); send(owner, id, 'resolve', {id, account: snapshot, isConnected: true});
  };
  const transport = async (module, method, args, callOptions) => {
    assert.equal(module, 'mail'); assert.deepEqual(callOptions.permissions, ['mail']);
    calls.push({method, args});
    if (method === 'open') { const id = 'owner-' + ++sequence; owners.set(id, []); return {id}; }
    if (method === 'close') {
      for (const [id, owner] of accounts) if (owner === args[0]) accounts.delete(id);
      for (const [id, value] of watches) if (value.owner === args[0]) watches.delete(id);
      return owners.delete(args[0]);
    }
    assert(owners.has(args[0]), 'owner must still exist');
    if (method === 'poll') {
      const queue = owners.get(args[0]), found = [];
      for (let i = 0; i < queue.length && found.length < 64;)
        if (args[1].includes(queue[i].id)) found.push(...queue.splice(i, 1)); else i++;
      return found;
    }
    if (method === 'cancel') {
      accounts.delete(args[1]); watches.delete(args[1]); tokens.delete(args[1]); return true;
    }
    if (method === 'answer') {
      const continuation = tokens.get(args[1]); tokens.delete(args[1]);
      if (continuation) continuation(args[2], args[3]);
      return !!continuation;
    }
    assert.equal(method, 'start');
    const [owner, name, input] = args, id = 'operation-' + ++sequence;
    if (hook && await hook({owner, name, input, id, send, connect, tokens, accounts, watches}) === false) return {id};
    let result = true;
    if (name === 'connect') {
      if (input[1] && !input[0].accessToken) {
        tokens.set(id, (token, accepted) => accepted ? connect(owner, id, input[0]) :
          send(owner, id, 'reject', {code: 'AUTH_FAILED', message: 'Token provider failed', retryable: false}));
        send(owner, id, 'token', {id});
      } else connect(owner, id, input[0]);
      return {id};
    }
    if (name === 'providers.list') result = [{id: 'synthetic', domains: ['example.invalid']}];
    else if (name === 'providers.get' || name === 'providers.resolve') result = {id: 'synthetic'};
    else if (name === 'accounts.list') result = [{alias: 'fixture', address: 'a@example.invalid'}];
    else if (name === 'client.close') {
      result = accounts.delete(input[0]);
      for (const [key, watch] of watches) if (watch.account === input[0]) {
        send(owner, key, 'event', {name: 'close', value: 'closed'}); watches.delete(key);
      }
    } else if (name === 'client.watch') {
      watches.set(id, {owner, account: input[0]});
      send(owner, id, 'event', {name: 'mode', value: 'poll'});
      result = {id, folder: input[1] || 'INBOX', mode: 'idle', generation: 1, isClosed: false};
    } else if (name === 'watch.stop') {
      send(owner, input[0], 'event', {name: 'close', value: 'stopped'}); result = watches.delete(input[0]);
    } else if (name.startsWith('client.')) {
      assert.equal(accounts.get(input[0]), owner, 'account belongs to its owner');
      const item = {uid: 7, folder: 'INBOX', subject: '\u96ea\ud83d\udce7'.repeat(1000), date: 1700000000000,
        from: {name: 'Fixture', address: 'a@example.invalid'},
        attachments: [{partId: '2', fileName: 'fixture.txt', size: 8}]};
      if (name === 'client.get' || name === 'client.fetchBody') result = item;
      else if (name === 'client.fetch') result = [item];
      else if (name === 'client.search') result = {messages: [item], fallback: 'client'};
      else if (name === 'client.download' || name === 'client.raw') {
        send(owner, id, 'progress', {transferred: 8, total: 8}); result = input[2] || '/fixture/output';
      } else if (name === 'client.test') result = {ok: true};
      else if (name === 'client.append') result = 9;
    }
    send(owner, id, 'resolve', result);
    return {id};
  };
  return {mail: factory(transport), transport, owners, accounts, watches, calls, send};
}

test('mail default forwarding, independent accounts, providers and aliases retain their contracts', async () => {
  const f = fixture();
  await assert.rejects(f.mail.fetch(), {code: 'NO_DEFAULT_ACCOUNT'});
  assert.throws(() => f.mail.folder('INBOX'), {code: 'NO_DEFAULT_ACCOUNT'});
  assert.equal(f.mail.connect, f.mail.connectAsync);
  assert.equal((await f.mail.providers.list())[0].id, 'synthetic');
  assert(await f.mail.accounts.has('fixture'));
  assert.equal(await f.mail.accounts.has('missing'), false);
  const a = await f.mail.setDefault(options), b = await f.mail.connect('fixture');
  assert.equal(f.mail.default, a); assert.equal(a.account.password, undefined); assert.equal(b.account.alias, 'fixture');
  for (const name of ['test','send','folders','folderStatus','createFolder','deleteFolder','renameFolder',
    'fetch','search','get','fetchBody','download','raw','setFlags','markRead','markUnread','flag','unflag',
    'move','copy','delete','expunge','append']) {
    assert.equal(a[name], a[name + 'Async']); assert.equal(f.mail[name], f.mail[name + 'Async']);
  }
  assert.equal((await f.mail.test()).ok, true);
  const folder = a.folder('INBOX/fixture');
  assert.equal(folder.name, 'fixture'); assert.equal(String(folder), 'INBOX/fixture');
  await folder.statusAsync(); await folder.create(); await folder.rename('INBOX/renamed'); await folder.delete();
  await f.mail.close(); assert.equal(f.mail.default, null); assert(a.isClosed); assert(!b.isClosed);
  await f.mail.dispose(); assert(b.isClosed); assert.equal(f.owners.size, 0);
});

test('mail Unicode fragments, message dates, bound attachments and download progress are preserved', async () => {
  const f = fixture(), account = await f.mail.connect(options);
  const found = await account.search({subject: 'fixture'}), item = found[0], progress = [];
  assert.equal(found.fallback, 'client'); assert(!Object.keys(found).includes('fallback'));
  assert(item.date instanceof Date); assert.equal(item.date.getTime(), 1700000000000);
  assert.equal(String(item.from), 'Fixture <a@example.invalid>');
  assert.equal(item.subject, '\u96ea\ud83d\udce7'.repeat(1000));
  assert.equal((await item.loadAsync()).uid, 7);
  const attachment = item.attachments[0]; assert.equal(attachment.uid, 7); assert.equal(attachment.folder, 'INBOX');
  assert.equal(await attachment.download('/tmp/a', {onProgress: (done,total) => progress.push([done,total])}), '/tmp/a');
  assert.deepEqual(progress, [[8,8]]);
  assert(!JSON.stringify(item).includes('loadAsync'));
  assert.equal(await account.raw(item, '/tmp/a.eml'), '/tmp/a.eml');
  await account.close(); await f.mail.dispose();
});

test('mail watches buffer initial events, track generation and close on account teardown', async () => {
  const f = fixture(), account = await f.mail.connect(options), watch = await account.watch({folder:'INBOX'},{timeoutMs:5000});
  const modes = [], messages = [], closes = [];
  watch.on('mode', mode => modes.push(mode)).once('message', (value, owner) => { assert.equal(owner, watch); messages.push(value); }).on('close', reason => closes.push(reason));
  await delay(5); assert.deepEqual(modes, ['poll']);
  const [id, entry] = [...f.watches][0];
  f.send(entry.owner, id, 'state', {generation: 2});
  f.send(entry.owner, id, 'event', {name: 'message', value: {uid: 8, folder: 'INBOX', subject: 'new'}});
  for (let n=0;n<100 && !messages.length;n++) await delay(5);
  assert.equal(watch.generation, 2); assert.equal(messages[0].uid, 8); assert.equal(typeof messages[0].load, 'function');
  assert.equal(watch.listenerCount('message'), 0);
  await account.close(); assert(watch.isClosed); assert.deepEqual(closes, ['closed']);
  await watch.stop(); await f.mail.dispose(); assert.equal(f.watches.size, 0);
});

test('mail token providers are awaited under the operation deadline and can refresh on the owner thread', async () => {
  let refreshed = false, invocations = 0;
  const f = fixture(({owner,name,input,id,send,tokens}) => {
    if (name !== 'client.test') return;
    tokens.set(id, (token,accepted) => { refreshed = accepted && token === 'second-token'; send(owner,id,'resolve',{ok:refreshed}); });
    send(owner,input[0],'token',{id}); return false;
  });
  const account = await f.mail.connect({address:'a@example.invalid',tokenProvider:async () => ++invocations === 1 ? 'first-token' : 'second-token'});
  assert.equal((await account.test()).ok, true); assert(refreshed); assert.equal(invocations, 2);
  assert.equal(account.account.accessToken, undefined);
  await f.mail.dispose();
  const hanging = fixture();
  await assert.rejects(hanging.mail.connect({address:'a@example.invalid',tokenProvider:()=>new Promise(()=>{})},{timeoutMs:20}), {code:'TIMEOUT'});
  await hanging.mail.dispose(); assert.equal(hanging.owners.size, 0);
  const denied = fixture();
  await assert.rejects(denied.mail.connect({address:'a@example.invalid',tokenProvider:()=>{throw Error('secret must stay local');}}),
    error => error.code==='AUTH_FAILED' && !error.message.includes('secret'));
  await denied.mail.dispose();
});

test('mail abort and late open replies cannot retain a client after disposal', async () => {
  let finish;
  const f = fixture(({owner,name,id,input,connect}) => {
    if (name === 'connect') { finish = () => connect(owner,id,input[0]); return false; }
  });
  const controller = new AbortController(), pending = f.mail.connect(options,{signal:controller.signal});
  while (!finish) await delay(1);
  controller.abort(); await assert.rejects(pending,{code:'CANCELLED'});
  finish(); await f.mail.dispose(); assert.equal(f.accounts.size,0); assert.equal(f.owners.size,0);
});

test('mail large deadlines do not overflow timers and elapsed deadlines detach abort listeners', async () => {
  const f = fixture();
  const a = await f.mail.connect(options,{timeoutMs:2**40}); await a.close();
  let listeners=0;
  const signal={aborted:false,addEventListener(){listeners++;},removeEventListener(){listeners--;}};
  const before=f.calls.length;
  await assert.rejects(f.mail.connect(options,{timeoutMs:Number.MIN_VALUE,signal}),{code:'TIMEOUT'});
  assert.equal(listeners,0); assert.equal(f.calls.length,before);
  await assert.rejects(f.mail.connect({...options,timeout:{read:NaN}}),{code:'INVALID_ARGUMENT'});
  await assert.rejects(f.mail.connect({...options,tokenProvider:1}),{code:'INVALID_ARGUMENT'});
  await f.mail.dispose();
});

test('a token returned after account closure is never sent to the host', async () => {
  let release;
  const f=fixture(({owner,name,input,id,send,tokens})=>{
    if(name!=='client.test')return;
    tokens.set(id,()=>send(owner,id,'resolve',{ok:true}));
    send(owner,input[0],'token',{id});return false;
  });
  const account=await f.mail.connect({address:'a@example.invalid',accessToken:'initial',
    tokenProvider:()=>new Promise(resolve=>{release=resolve;})});
  const finished=assert.rejects(account.test({timeoutMs:150}),{code:'TIMEOUT'});
  while(!release)await delay(1);
  await account.close();
  release('late-synthetic-token');
  await delay(20);
  assert(!f.calls.some(call=>call.method==='answer'),'late token left the closed account');
  await finished;await f.mail.dispose();
});
