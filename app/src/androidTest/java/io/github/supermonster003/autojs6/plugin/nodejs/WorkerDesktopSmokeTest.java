package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class WorkerDesktopSmokeTest {
    private Context context;
    private File root;
    private INodeJsRuntimePlugin runtime;
    private ServiceConnection connection;

    @Before public void connect() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        root = new File(context.getCacheDir(), "worker-desktop/" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binder = new AtomicReference<>();
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                binder.set(service); connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        assertTrue(context.bindService(new Intent().setComponent(new ComponentName(context.getPackageName(),
                context.getPackageName() + ".NodeJsRuntimePluginService")), connection, Context.BIND_AUTO_CREATE));
        assertTrue(connected.await(30, TimeUnit.SECONDS));
        runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
        assertNotNull(runtime);
    }

    @After public void disconnect() {
        if (connection != null) context.unbindService(connection);
        if (root != null) {
            deleteTree(root);
            deleteTree(new File(root.getParentFile(), root.getName() + ".zip"));
            deleteTree(new File(root.getParentFile(), root.getName() + "-output.zip"));
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void source(String name, String content) throws Exception {
        File file = new File(root, name);
        assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private String run(String name, String source, String policy, boolean network) throws Exception {
        return run(name, source, policy, network, false);
    }

    private String run(String name, String source, String policy, boolean network, boolean inspector) throws Exception {
        source(name, source);
        File inputArchive = new File(root.getParentFile(), root.getName() + ".zip");
        try (var zip = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(inputArchive));
             var files = Files.walk(root.toPath())) {
            org.json.JSONArray names = new org.json.JSONArray();
            for (var path : (Iterable<java.nio.file.Path>) files.filter(Files::isRegularFile)::iterator) {
                String relative = root.toPath().relativize(path).toString();
                names.put(relative);
                zip.putNextEntry(new java.util.zip.ZipEntry(relative));
                Files.copy(path, zip);
                zip.closeEntry();
            }
            zip.putNextEntry(new java.util.zip.ZipEntry(".autojs6-workspace-transport/input-manifest-v1.json"));
            zip.write(new org.json.JSONObject().put("version", 1).put("deleteEligibleFiles", names)
                    .toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Bundle request = new Bundle();
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, source);
        request.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, new File(root, name).getAbsolutePath());
        request.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, root.getAbsolutePath());
        request.putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT, root.getAbsolutePath());
        request.putBoolean(NodeJsRuntimeContract.KEY_RAW_NODE_NETWORK_MODULES_ENABLED, network);
        request.putBoolean(NodeJsRuntimeContract.KEY_INSPECTOR_ENABLED, inspector);
        request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 45_000L);
        if (policy != null) {
            request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCE_NAMES, new String[]{"autojs6:worker-policy"});
            request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCES, new String[]{policy});
        }
        request.putInt(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION,
                NodeJsRuntimeContract.WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION);
        request.putString(NodeJsRuntimeContract.KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY, "");
        Bundle result;
        try (var input = ParcelFileDescriptor.open(inputArchive, ParcelFileDescriptor.MODE_READ_ONLY);
             var output = ParcelFileDescriptor.open(new File(root.getParentFile(), root.getName() + "-output.zip"),
                     ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE)) {
            request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_INPUT_FD, input);
            request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_OUTPUT_FD, output);
            result = runtime.runScript(request, null);
        }
        assertNotNull(result);
        String output = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
        assertTrue(result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") + " / "
                        + result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + " / " + output,
                result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        Bundle status = new Bundle();
        status.putString("stream", "\n" + output);
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
        return output;
    }

    @Test public void workersUseNativeBuiltinsNetworkAndUserResourceLimits() throws Exception {
        source("worker.cjs", """
                const assert = require('node:assert/strict');
                const { Buffer } = require('node:buffer');
                const { parentPort, resourceLimits, workerData } = require('node:worker_threads');
                (async () => {
                  for (const name of workerData.builtins) {
                    try { assert.ok(require(name), name); }
                    catch (error) { if (error.code !== 'ERR_TRACE_EVENTS_UNAVAILABLE') throw error; }
                  }
                  assert.strictEqual(resourceLimits.maxOldGenerationSizeMb, 192);
                  assert.strictEqual(require('node:module').createRequire(__filename)('node:crypto'), require('node:crypto'));
                  const fs = require('node:fs');
                  // M23.1: worker fs is Node's own module; /proc follows Android like any other path.
                  assert.strictEqual(fs, process.getBuiltinModule('fs'));
                  assert.match(fs.readFileSync('/proc/self/status', 'utf8'), /^Pid:\\s+\\d+/m);
                  assert.strictEqual(fs.readFileSync(workerData.outside,'utf8'),'outside');
                  for (const name of ['autojs6:fetch','autojs6:websocket','sqlite','ui']) {
                    assert.throws(() => require(name), e => e.code === 'MODULE_NOT_FOUND');
                  }
                  for (const name of ['inspector','node:inspector']) {
                    assert.equal(require(name).url(),undefined);
                    assert.throws(() => require(name).open(0), e => e.code === 'ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED');
                  }
                  const {DatabaseSync}=require('node:sqlite');
                  const db=new DatabaseSync(':memory:');
                  try { assert.strictEqual(db.prepare('SELECT 42 AS value').get().value,42); assert.strictEqual(db.isOpen,true); }
                  finally { db.close(); }
                  // A procfs file is SQLite's business: its st_size is 0, so SQLite opens it as an empty read-only
                  // database and the first write fails with SQLite's own error; never an AutoJs6 policy code.
                  { let bad=null; try { bad=new DatabaseSync('/proc/self/status'); bad.exec('CREATE TABLE t (x)'); throw new Error('wrote into a procfs database'); }
                    catch(e){ assert.strictEqual(e.code,'ERR_SQLITE_ERROR'); assert.strictEqual(e.autojs6Code,undefined); } finally { try { if (bad) bad.close(); } catch(_) {} } }
                  const http = require('node:http');
                  const server = http.createServer((req,res)=>res.end('worker-native'));
                  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
                  try {
                    const response = await fetch('http://127.0.0.1:'+server.address().port);
                    assert.strictEqual(await response.text(),'worker-native');
                  } finally { server.closeAllConnections(); await new Promise(resolve=>server.close(resolve)); }
                  parentPort.postMessage('native-pass');
                })().catch(e=>{setImmediate(()=>{throw e;});});
                """);
        Files.write(new File(root.getParentFile(), "outside.txt").toPath(), "outside".getBytes(StandardCharsets.UTF_8));
        String output = run("main.cjs", """
                (async () => {
                  const assert=require('node:assert/strict'), wt=require('node:worker_threads');
                  assert.strictEqual(wt, process.getBuiltinModule('worker_threads'));
                  assert.strictEqual(typeof wt.receiveMessageOnPort, 'function');
                  for (const name of ['node:worker_threads','autojs6:bridge','device']) {
                    assert.strictEqual(require(require.resolve(name)),require(name),name);
                  }
                  assert.strictEqual(wt.policy.workerPool.taskTimeoutMs,0);
                  // M23.2: Node's own inventory; inspector stays denied inside workers.
                  const builtins = require('node:module').builtinModules.filter(name=>!name.startsWith('inspector'));
                  const workers = Array.from({length:4},()=>new wt.Worker(__dirname+'/worker.cjs',{
                    resourceLimits:{maxOldGenerationSizeMb:192},workerData:{builtins,outside:OUTSIDE_PATH}
                  }));
                  try {
                    const values=await Promise.all(workers.map(w=>new Promise((resolve,reject)=>{
                      w.once('message',resolve);w.once('error',reject);
                    })));
                    assert.deepStrictEqual(values,Array(4).fill('native-pass'));
                    console.log('m13.workers.native=PASS');
                  } finally { await Promise.all(workers.map(w=>w.terminate())); }
                })().catch(e=>{console.error(e.stack);process.exitCode=1;});
                """.replace("OUTSIDE_PATH", org.json.JSONObject.quote(new File(root.getParentFile(), "outside.txt").getAbsolutePath())), null, true);
        assertTrue(output.contains("m13.workers.native=PASS"));
    }

    @Test public void requestResourceDefaultsCanBeOverriddenAndNetworkCanBeDisabled() throws Exception {
        source("worker.cjs", """
                const assert=require('assert/strict'), wt=require('worker_threads');
                (async()=>{
                  assert.strictEqual(wt.resourceLimits.maxOldGenerationSizeMb,wt.workerData.expected);
                  assert.throws(()=>require('node:http'),e=>e.code==='ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED');
                  await assert.rejects(fetch('http://127.0.0.1:9'),e=>e.code==='ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED');
                  assert.throws(()=>new WebSocket('ws://127.0.0.1:9'),e=>e.code==='ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED');
                  wt.parentPort.postMessage('policy-pass');
                })().catch(e=>{setImmediate(()=>{throw e;});});
                """);
        String output = run("main.cjs", """
                (async()=>{
                  const assert=require('assert/strict'), wt=require('worker_threads');
                  for (const expected of [96,80,192]) {
                    const options={workerData:{expected}};
                    if(expected!==96) options.resourceLimits={maxOldGenerationSizeMb:expected};
                    const worker=new wt.Worker(__dirname+'/worker.cjs',options);
                    try { assert.strictEqual(await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);}), 'policy-pass'); }
                    finally { await worker.terminate(); }
                  }
                  console.log('m13.workers.policy=PASS');
                })().catch(e=>{console.error(e.stack);process.exitCode=1;});
                """, "{\"maxWorkers\":1,\"taskTimeoutMs\":200,\"resourceLimits\":{\"maxOldGenerationSizeMb\":96}}", false);
        assertTrue(output.contains("m13.workers.policy=PASS"));
    }

    @Test public void poolHasNoDefaultTaskTimeoutAndCleansUpExplicitTimeout() throws Exception {
        source("pool.cjs", """
                const {parentPort}=require('worker_threads');
                parentPort.on('message',task=>setTimeout(()=>parentPort.postMessage({__autojs6PoolTaskId:task.__autojs6PoolTaskId,value:42}),task.value));
                """);
        String output = run("main.cjs", """
                (async()=>{
                  const assert=require('assert/strict'), {WorkerPool}=require('worker_threads');
                  const pool=new WorkerPool(__dirname+'/pool.cjs',{maxWorkers:1});
                  try {
                    assert.strictEqual(await pool.run(5500),42);
                    await assert.rejects(pool.run(1000,{timeoutMs:50}),e=>e.code==='ERR_AUTOJS6_WORKER_POOL_TASK_TIMEOUT');
                    assert.strictEqual(await pool.run(0),42);
                    console.log('m13.workers.pool=PASS');
                  } finally { await pool.close(); }
                })().catch(e=>{console.error(e.stack);process.exitCode=1;});
                """, null, true);
        assertTrue(output.contains("m13.workers.pool=PASS"));
    }

    @Test public void sixteenWorkersCanRunTogetherAndCreateNestedWorkers() throws Exception {
        source("value.mjs", "export const value = 21;\n");
        source("nested.mjs", """
                import assert from 'node:assert/strict';
                import { parentPort } from 'node:worker_threads';
                import { createRequire } from 'node:module';
                assert.equal(import.meta.main, true);
                const require = createRequire(import.meta.url);
                parentPort.postMessage(require('./value.mjs').value + (await import('./value.mjs')).value);
                """);
        source("parallel.cjs", """
                const { Worker, parentPort, workerData } = require('node:worker_threads');
                parentPort.once('message', async () => {
                  if (workerData === 0) {
                    const nested = new Worker(require('node:path').join(__dirname, 'nested.mjs'));
                    nested.once('message', value => parentPort.postMessage(value));
                    nested.once('error', error => { throw error; });
                  } else {
                    const esm = await import('./value.mjs');
                    parentPort.postMessage(esm.value + require('./value.mjs').value + workerData);
                  }
                });
                parentPort.postMessage('ready');
                """);
        assertTrue(run("main.cjs", """
                const assert = require('node:assert/strict');
                const wt = require('node:worker_threads');
                (async () => {
                  const workers = Array.from({length:16}, (_,index) => new wt.Worker(__dirname+'/parallel.cjs', {workerData:index}));
                  const receive = worker => new Promise((resolve,reject) => {worker.once('message',resolve);worker.once('error',reject);});
                  try {
                    assert.deepEqual(await Promise.all(workers.map(receive)), Array(16).fill('ready'));
                    assert.equal(new Set(workers.map(worker=>worker.threadId)).size, 16);
                    const values = workers.map(receive);
                    for (const worker of workers) worker.postMessage('go');
                    assert.deepEqual(await Promise.all(values), Array.from({length:16}, (_,i)=>42+i));
                    console.log('m23.workers.parallel-nested=PASS');
                  } finally { await Promise.all(workers.map(worker=>worker.terminate())); }
                })().catch(error=>{console.error(error.stack);process.exitCode=1;});
                """, "{\"maxWorkers\":1,\"startupTimeoutMs\":1}", true).contains("m23.workers.parallel-nested=PASS"));
    }

    @Test public void workerPreloadsAndNativeOptionsSurviveNestedExecution() throws Exception {
        source("preload.cjs", """
                const { registerHooks } = require('node:module');
                const { workerData } = require('node:worker_threads');
                globalThis.preloaded = workerData.value;
                registerHooks({ resolve(s,c,next) {
                  return s === 'fixture:preloaded' ? {
                    url:'data:text/javascript,export default '+workerData.value,shortCircuit:true
                  } : next(s,c);
                }});
                """);
        source("options.mjs", """
                import assert from 'node:assert/strict';
                import { Worker, parentPort, workerData, getEnvironmentData } from 'node:worker_threads';
                import value from 'fixture:preloaded';
                assert.equal(value, 42);
                assert.equal(globalThis.preloaded, workerData.value);
                assert.equal(process.argv.at(-1), 'user-arg');
                assert.equal(process.env.M23_WORKER_ENV, 'present');
                assert.equal(getEnvironmentData('m23-example'), 7);
                const nested = new Worker("import('fixture:preloaded').then(m=>require('worker_threads').parentPort.postMessage(m.default))", {
                  eval:true,workerData:{value:43}
                });
                assert.equal(await new Promise((r,j)=>{nested.once('message',r);nested.once('error',j);}),43);
                const cleared = new Worker("require('worker_threads').parentPort.postMessage(typeof globalThis.preloaded)", {
                  eval:true,execArgv:[],workerData:{value:44}
                });
                assert.equal(await new Promise((r,j)=>{cleared.once('message',r);cleared.once('error',j);}), 'undefined');
                console.log('native-worker-stdout');
                parentPort.postMessage('options-pass');
                """);
        assertTrue(run("main.cjs", """
                (async()=>{
                  const assert=require('node:assert/strict'),wt=require('node:worker_threads');
                  wt.setEnvironmentData('m23-example',7);
                  const worker=new wt.Worker(new URL('file://'+__dirname+'/options.mjs'),{
                    workerData:{value:42},execArgv:['--require',__dirname+'/preload.cjs'],argv:['user-arg'],
                    env:{...process.env,M23_WORKER_ENV:'present'},stdout:true
                  });
                  let stdout='';worker.stdout.on('data',chunk=>{stdout+=chunk;});
                  const exit=new Promise((r,j)=>{worker.once('exit',r);worker.once('error',j);});
                  assert.equal(await new Promise((r,j)=>{worker.once('message',r);worker.once('error',j);}), 'options-pass');
                  assert.equal(await exit,0);
                  assert.match(stdout,/native-worker-stdout/);
                  const dataWorker=new wt.Worker(new URL('data:text/javascript,'+encodeURIComponent(
                    "import {parentPort} from 'node:worker_threads'; parentPort.postMessage(42);"
                  )));
                  assert.equal(await new Promise((r,j)=>{dataWorker.once('message',r);dataWorker.once('error',j);}),42);
                  await dataWorker.terminate();
                  console.log('m23.workers.options=PASS');
                })().catch(error=>{console.error(error.stack);process.exitCode=1;});
                """, null, true).contains("m23.workers.options=PASS"));
    }

    @Test public void inspectorSessionsWorkInsideEnabledWorkers() throws Exception {
        assertTrue(run("main.cjs", """
                (async()=>{
                  const assert=require('node:assert/strict'),{Worker}=require('node:worker_threads');
                  const enabled=require('autojs6:profile').inspector;
                  const code=`const {parentPort,workerData}=require('node:worker_threads');
                    if (!workerData) {
                      require('node:assert/strict').equal(require('node:inspector').url(),undefined);
                      parentPort.postMessage('request-disabled');
                    } else {
                      const inspector=require('node:inspector');
                      require('node:assert/strict').throws(()=>inspector.open(0,'0.0.0.0'),{code:'ERR_INVALID_ARG_VALUE'});
                      const session=new inspector.Session(); session.connect();
                      session.post('Runtime.evaluate',{expression:'21*2'},(error,result)=>{
                        session.disconnect();if(error)throw error;parentPort.postMessage(result.result.value);
                      });
                    }`;
                  const worker=new Worker(code,{eval:true,workerData:enabled});
                  try { assert.equal(await new Promise((r,j)=>{worker.once('message',r);worker.once('error',j);}),enabled?42:'request-disabled'); }
                  finally { await worker.terminate(); }
                  console.log('m23.workers.inspector=PASS');
                })().catch(error=>{console.error(error.stack);process.exitCode=1;});
                """, null, true, true).contains("m23.workers.inspector=PASS"));
    }

    @Test public void nestedWorkersForwardHostCallsCancellationAndPermissionErrors() throws Exception {
        source("project.json", "{\"node\":{\"permissions\":[\"device\"]}}\n");
        source("bridge-worker.cjs", """
                const assert=require('node:assert/strict'),wt=require('node:worker_threads');
                (async()=>{
                  if (wt.workerData.depth) {
                    const nested=new wt.Worker(__filename,{workerData:{depth:0}});
                    const value=await new Promise((r,j)=>{nested.once('message',r);nested.once('error',j);});
                    wt.parentPort.postMessage(value);
                    return;
                  }
                  const bridge=require('autojs6:bridge');
                  assert.ok(bridge.permissions().permissions.includes('device'));
                  assert.equal(await bridge.callAutoJs('device','getWidth',[]),42);
                  await assert.rejects(bridge.callAutoJs('media','info',[]),e=>e.code==='ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED' && e.module==='media');
                  const controller=new AbortController();
                  const pending=bridge.callAutoJs('device','getHeight',[],{signal:controller.signal,timeoutMs:5000});
                  setTimeout(()=>controller.abort(),50);
                  await assert.rejects(pending,e=>e.code==='ERR_AUTOJS6_BRIDGE_CANCELLED');
                  wt.parentPort.postMessage('bridge-pass');
                })().catch(error=>{setImmediate(()=>{throw error;});});
                """);
        assertTrue(run("main.cjs", """
                (async()=>{
                  const assert=require('node:assert/strict'),bridge=require('autojs6:bridge'),{Worker}=require('node:worker_threads');
                  const requests=[];
                  bridge.__test.setTransport({postMessage(text){
                    const request=JSON.parse(text);requests.push(request);
                    if(request.method==='getWidth')setImmediate(()=>bridge.__test.receiveMessage(JSON.stringify({id:request.id,ok:true,result:42})));
                  }});
                  const worker=new Worker(__dirname+'/bridge-worker.cjs',{workerData:{depth:1}});
                  const exit=new Promise((r,j)=>{worker.once('exit',r);worker.once('error',j);});
                  assert.equal(await new Promise((r,j)=>{worker.once('message',r);worker.once('error',j);}), 'bridge-pass');
                  assert.equal(await exit,0);
                  assert.equal(requests.filter(r=>r.module==='device').length,2);
                  assert.equal(bridge.__test.pendingCount(),0);
                  console.log('m23.workers.bridge=PASS');
                })().catch(error=>{console.error(error.stack);process.exitCode=1;});
                """, null, true).contains("m23.workers.bridge=PASS"));
    }

    @Test public void publishedCpuAndWasmSamplesRunInNativeWorkers() throws Exception {
        AssetManager assets = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        for (String example : new String[]{"worker-cpu", "wasm-worker"}) {
            for (String file : assets.list(example)) {
                if (file.endsWith(".cjs")) {
                    try (var input = assets.open(example + "/" + file)) {
                        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                        source(example + "/" + file, new String(output.toByteArray(), StandardCharsets.UTF_8));
                    }
                }
            }
            String entry = new String(Files.readAllBytes(new File(root, example + "/main.cjs").toPath()), StandardCharsets.UTF_8);
            assertTrue(run(example + "/main.cjs", entry, null, true).contains("sample." + example + "=PASS"));
        }
    }

    @Test public void publishedWorkerBridgeSampleUsesParentHostTransport() throws Exception {
        AssetManager assets = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        for (String file : new String[]{"main.cjs", "worker.cjs", "project.json"}) {
            try (var input = assets.open("worker-host-bridge/" + file);
                 var output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                source(file, new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        String entry = new String(Files.readAllBytes(new File(root, "main.cjs").toPath()), StandardCharsets.UTF_8);
        String transport = """
                const fixtureBridge=require('autojs6:bridge');
                fixtureBridge.__test.setTransport({postMessage(text){
                  const request=JSON.parse(text);
                  require('node:assert/strict').equal(request.module,'device');
                  require('node:assert/strict').equal(request.method,'info');
                  setImmediate(()=>fixtureBridge.__test.receiveMessage(JSON.stringify({
                    id:request.id,ok:true,result:{build:{model:'test-device',sdkInt:37}}
                  })));
                }});
                """;
        assertTrue(run("main.cjs", transport + entry, null, true).contains("sample.worker-host-bridge=PASS"));
    }

    @Test public void esmWorkersFileUrlsAndTransferListsRemainUsable() throws Exception {
        source("value.mjs", "export const value = 42;");
        source("worker.mjs", """
                import { parentPort, workerData } from 'node:worker_threads';
                import { value } from './value.mjs';
                workerData.port.postMessage(new Uint8Array(workerData.buffer)[0]);
                workerData.port.close();
                parentPort.postMessage(value);
                parentPort.close();
                """);
        assertTrue(run("main.cjs", """
                (async()=>{
                  const assert=require('assert/strict'), {Worker,MessageChannel}=require('worker_threads');
                  const {port1,port2}=new MessageChannel();
                  const buffer=new Uint8Array([7]).buffer;
                  const worker=new Worker(require('url').pathToFileURL(__dirname+'/worker.mjs'),{
                    workerData:{port:port2,buffer},transferList:[port2,buffer]
                  });
                  try {
                    assert.strictEqual(buffer.byteLength,0);
                    const got=await Promise.all([
                      new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);}),
                      new Promise(resolve=>port1.once('message',resolve))
                    ]);
                    assert.deepStrictEqual(got,[42,7]);
                    console.log('m13.workers.transfer=PASS');
                  } finally { port1.close(); await worker.terminate(); }
                })().catch(e=>{console.error(e.stack);process.exitCode=1;});
                """, null, true).contains("m13.workers.transfer=PASS"));
    }
}
