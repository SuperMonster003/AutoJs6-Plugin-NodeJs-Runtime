import assert from 'node:assert/strict';
import { Worker } from 'node:worker_threads';
import checkCrypto from './crypto.cjs';
import checkTls from './tls.cjs';

await checkCrypto();
await checkTls();
await Promise.all(['worker.cjs', 'worker.mjs'].map(file => new Promise((resolve, reject) => {
  const worker = new Worker(new URL(file, import.meta.url));
  let received;
  worker.once('message', value => { received = value; });
  worker.once('error', reject);
  worker.once('exit', code => {
    try { assert.equal(code, 0); assert.equal(received, '3.5.9'); resolve(); } catch (error) { reject(error); }
  });
})));
console.log('m25.openssl.esm-workers=PASS version=' + process.versions.openssl);
