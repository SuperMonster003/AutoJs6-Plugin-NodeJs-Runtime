'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const tls = require('node:tls');
const { X509Certificate } = require('node:crypto');

module.exports = async function checkTls() {
  assert.equal(process.versions.openssl, '3.5.9');
  const read = name => fs.readFileSync(path.join(__dirname, name));
  const ca = read('ca.pem');
  const sniCertificate = new X509Certificate(read('sni-cert.pem'));
  assert.equal(sniCertificate.ca, false);
  assert.ok(sniCertificate.verify(new X509Certificate(ca).publicKey));
  assert.equal(sniCertificate.checkHost('sni.example.test'), 'sni.example.test');
  const alternate = tls.createSecureContext({ key: read('sni-key.pem'), cert: read('sni-cert.pem') });
  for (const protocol of ['TLSv1.2', 'TLSv1.3']) {
    let sniCalls = 0;
    const sockets = new Set();
    const server = tls.createServer({
      key: read('server-key.pem'), cert: read('server-cert.pem'), minVersion: protocol, maxVersion: protocol,
      SNICallback(name, callback) {
        sniCalls++;
        callback(null, name === 'sni.example.test' ? alternate : undefined);
      }
    }, socket => socket.end('openssl-tls-ok'));
    server.on('connection', socket => { sockets.add(socket); socket.once('close', () => sockets.delete(socket)); });
    server.on('tlsClientError', () => {}); // The untrusted CA and wrong-host clients below must fail.
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
    const connect = (servername, trusted = true) => new Promise((resolve, reject) => {
      const client = tls.connect({ host: '127.0.0.1', port: server.address().port, servername,
        ca: trusted ? ca : undefined, minVersion: protocol, maxVersion: protocol, rejectUnauthorized: true });
      let body = '';
      client.setTimeout(5000, () => client.destroy(new Error('TLS fixture timed out')));
      client.once('error', reject);
      client.once('secureConnect', () => {
        try {
          assert.ok(client.authorized);
          assert.equal(client.getProtocol(), protocol);
          assert.equal(client.getPeerCertificate().subject.CN, servername);
        } catch (error) { client.destroy(error); }
      });
      client.on('data', data => { body += data; });
      client.once('end', () => { client.destroy(); resolve(body); });
    });
    try {
      const replies = await Promise.all([connect('localhost'), connect('sni.example.test'), connect('sni.example.test')]);
      assert.deepEqual(replies, ['openssl-tls-ok', 'openssl-tls-ok', 'openssl-tls-ok']);
      await assert.rejects(connect('wrong.example.test'), { code: 'ERR_TLS_CERT_ALTNAME_INVALID' });
      await assert.rejects(connect('sni.example.test', false), error =>
        ['UNABLE_TO_VERIFY_LEAF_SIGNATURE', 'UNABLE_TO_GET_ISSUER_CERT_LOCALLY', 'SELF_SIGNED_CERT_IN_CHAIN'].includes(error.code));
      assert.ok(sniCalls >= 5);
    } finally {
      for (const socket of sockets) socket.destroy();
      await new Promise(resolve => server.close(resolve));
    }
  }
};
