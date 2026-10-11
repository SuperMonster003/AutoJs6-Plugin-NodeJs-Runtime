'use strict';
const assert = require('node:assert/strict');
const crypto = require('node:crypto');

module.exports = async function checkCrypto() {
  assert.equal(process.versions.openssl, '3.5.9');
  const message = Buffer.from('AutoJs6 OpenSSL 3.5.9 regression');
  const changed = Buffer.from('AutoJs6 altered message');
  // Brainpool exercises generic EC scalar multiplication, including the 384-bit word boundary.
  for (const namedCurve of ['prime256v1', 'secp384r1', 'secp521r1', 'brainpoolP256r1', 'brainpoolP384r1']) {
    const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve });
    const signature = crypto.sign('sha384', message, privateKey);
    assert.ok(crypto.verify('sha384', message, publicKey, signature), namedCurve);
    assert.equal(crypto.verify('sha384', changed, publicKey, signature), false, namedCurve);
    const peer = crypto.generateKeyPairSync('ec', { namedCurve });
    assert.deepEqual(crypto.diffieHellman({ privateKey, publicKey: peer.publicKey }),
      crypto.diffieHellman({ privateKey: peer.privateKey, publicKey }));
  }
  const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
  const options = { key: privateKey, padding: crypto.constants.RSA_PKCS1_PSS_PADDING, saltLength: 32 };
  const signature = await new Promise((resolve, reject) => crypto.sign('sha256', message, options,
    (error, result) => error ? reject(error) : resolve(result)));
  assert.ok(crypto.verify('sha256', message, { ...options, key: publicKey }, signature));
  const key = crypto.randomBytes(32), iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const encrypted = Buffer.concat([cipher.update(message), cipher.final()]);
  const tag = cipher.getAuthTag();
  const decrypt = (authTag) => {
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(authTag);
    return Buffer.concat([decipher.update(encrypted), decipher.final()]);
  };
  assert.deepEqual(decrypt(tag), message);
  const invalidTag = Buffer.from(tag); invalidTag[0] ^= 1;
  assert.throws(() => decrypt(invalidTag));
  const pair = await crypto.webcrypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
  const algorithm = { name: 'ECDSA', hash: 'SHA-256' };
  const webSignature = await crypto.webcrypto.subtle.sign(algorithm, pair.privateKey, message);
  assert.ok(await crypto.webcrypto.subtle.verify(algorithm, pair.publicKey, webSignature, message));
  return process.versions.openssl;
};
