'use strict';
(async () => {
  await require('./crypto.cjs')();
  await require('./tls.cjs')();
  console.log('m25.openssl.cjs=PASS version=' + process.versions.openssl);
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
