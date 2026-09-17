"nodejs";

import assert from "node:assert/strict";
import fs from "node:fs";
import {pipeline} from "node:stream/promises";
import {createGzip, createGunzip} from "node:zlib";

fs.mkdirSync("out", {recursive: true});
const input = Buffer.from("AutoJs6 file and compression streams\n".repeat(4096));
fs.writeFileSync("out/input.txt", input);
await pipeline(fs.createReadStream("out/input.txt"), createGzip(), fs.createWriteStream("out/input.txt.gz"));
await pipeline(fs.createReadStream("out/input.txt.gz"), createGunzip(), fs.createWriteStream("out/restored.txt"));
assert.deepEqual(fs.readFileSync("out/restored.txt"), input);
console.log("sample.streams-compression.bytes=" + input.length);
console.log("sample.streams-compression=PASS");
