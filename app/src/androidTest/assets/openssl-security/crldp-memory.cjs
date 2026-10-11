'use strict';
// CVE-2026-35189: inspect a certificate without eagerly expanding every relative CRLDP name.
const assert = require('node:assert/strict');
const { X509Certificate } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, 'relative-crldp-memory.pem'));
const before = process.memoryUsage().rss;
const cert = new X509Certificate(source);
assert.equal(cert.ca, false);
const rssDeltaBytes = process.memoryUsage().rss - before;
// A fresh process avoids other runtime slots/Workers affecting the measurement. The fixed
// implementation needs only a few MB; allow 32 MiB for allocator/platform differences.
assert.ok(rssDeltaBytes < 32 * 1024 * 1024, 'relative CRLDP cache allocated ' + rssDeltaBytes + ' bytes');
console.log('m25.openssl.crldp=PASS ' + JSON.stringify({ openssl: process.versions.openssl, rssDeltaBytes }));
