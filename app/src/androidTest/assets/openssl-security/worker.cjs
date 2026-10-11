'use strict';
const { parentPort } = require('node:worker_threads');
(async () => {
  await require('./crypto.cjs')();
  await require('./tls.cjs')();
  parentPort.postMessage(process.versions.openssl);
  parentPort.close();
})().catch(error => { throw error; });
