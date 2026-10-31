const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), os = require('node:os'), path = require('node:path');
const {pathToFileURL} = require('node:url');
const source = fs.readFileSync(path.join(__dirname, '../../../app/src/main/cpp/node_bridge_sources.cpp'), 'utf8');
function part(a, z) { const start = source.indexOf(a), end = source.indexOf(z, start); assert(start >= 0 && end > start); return source.slice(start, end); }
const constructor = part('  function __autojs6_worker_constructor(', '  // The worker keeps synchronous metadata');
const modules = part('  function __autojs6_worker_host_modules(', '  async function __autojs6_worker_invoke(');
const preload = part('  function __autojs6_worker_preload(', '  function __autojs6_limited_worker_threads(');
const createWorker = new Function('return (' + constructor + ')')();
const directory = path.resolve(__dirname, '../../../build/worker-entry-tests'); fs.mkdirSync(directory, {recursive: true});
const preloadPath = path.join(directory, 'preload.cjs');
fs.writeFileSync(preloadPath, `(${preload})(require,${constructor},()=>{},${modules});`);
const entryConfig = moduleSources => ({preloadPath, entryUrl: pathToFileURL(path.join(directory, 'entry.cjs')).href, gatedBuiltins: [], moduleSources,
  typeScriptSources: {}, resourceLimits: {}, hostModuleMethods: {}});
const enter = (script, moduleSources) => new Promise((resolve, reject) => {
  const Worker = createWorker(require, () => entryConfig(moduleSources), async () => {});
  const worker = new Worker(script, {execArgv: []});
  worker.once('message', value => { worker.terminate(); resolve(value); }); worker.once('error', reject);
});

test('A project without a package.json starts a Worker from its own script (Q200)', async () => {
  const project = fs.mkdtempSync(path.join(os.tmpdir(), 'autojs6-worker-'));
  try {
    const script = path.join(project, 'main.js');
    const code = "const {parentPort} = require('node:worker_threads'); parentPort.postMessage({filename: __filename, loader: typeof require});";
    fs.writeFileSync(script, code);
    // The cause: the lookup answers with the script itself.
    assert.equal(require('node:module').findPackageJSON(pathToFileURL(script).href), script);
    assert.deepEqual(await enter(script, {[script]: {source: code}}), {filename: script, loader: 'function'});
  } finally { fs.rmSync(project, {recursive: true, force: true}); }
});

test('A package.json of the project still decides the format of the Worker script', async () => {
  const project = fs.mkdtempSync(path.join(os.tmpdir(), 'autojs6-worker-'));
  try {
    const script = path.join(project, 'main.js');
    const code = "import {parentPort} from 'node:worker_threads'; parentPort.postMessage({esm: typeof require === 'undefined'});";
    fs.writeFileSync(script, code); fs.writeFileSync(path.join(project, 'package.json'), '{"type":"module"}');
    assert.deepEqual(await enter(script, {[script]: {source: code}}), {esm: true});
  } finally { fs.rmSync(project, {recursive: true, force: true}); }
});
