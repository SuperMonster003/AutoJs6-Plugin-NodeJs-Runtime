import { parentPort } from 'node:worker_threads';
import checkCrypto from './crypto.cjs';
import checkTls from './tls.cjs';
await checkCrypto();
await checkTls();
parentPort.postMessage(process.versions.openssl);
parentPort.close();
